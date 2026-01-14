package com.example.seniorantiscam

import android.content.Context
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.Locale
import kotlin.math.min

/**
 * TextClassifier for Keras Tokenizer + TFLite LSTM model (converted with float16 weights).
 *
 * Important parameters to set to match your training:
 * - vocabSize: the Keras num_words (12000)
 * - maxSeqLen: MAX_LEN used during training (40)
 * - oovIndex: index assigned to OOV token by Keras (usually 1 when oov_token="<OOV>")
 *
 * Place your tflite and tokenizer.json into app/src/main/assets/
 * Example tokenizer.json produced by: tokenizer.to_json()
 */
class TextClassifier(
    private val context: Context,
    private val modelFileName: String = "scam_lstm_fp16.tflite",
    private val tokenizerFileName: String = "tokenizer.json",
    private val maxSeqLen: Int = 40,
    private val paddingPost: Boolean = true,
    private val oovIndex: Int = 1,
    private val vocabSize: Int = 12000
) {

    private var interpreter: Interpreter
    private var wordIndex: Map<String, Int> = emptyMap()

    init {
        loadTokenizer()
        interpreter = Interpreter(loadModelFile(modelFileName))
    }

    /**
     * Load tokenizer JSON saved by Keras tokenizer.to_json()
     * Expects "word_index" object mapping token -> index
     */
    private fun loadTokenizer() {
        try {
            val json = context.assets.open(tokenizerFileName).bufferedReader().use { it.readText() }
            val jo = JSONObject(json)

            if (jo.has("word_index")) {
                val wi = jo.getJSONObject("word_index")
                val map = mutableMapOf<String, Int>()
                val it = wi.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    // JSON may store indices as ints; attempt getInt
                    val v = try { wi.getInt(k) } catch (e: Exception) {
                        // fallback if numbers are strings
                        try { wi.getString(k).toInt() } catch (_: Exception) { null }
                    }
                    if (v != null) map[k] = v
                }
                wordIndex = map
                return
            }

            // fallback attempts (index_word etc.)
            if (jo.has("index_word")) {
                val iw = jo.getJSONObject("index_word")
                val map = mutableMapOf<String, Int>()
                val it = iw.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val word = iw.getString(k)
                    val idx = try { k.toInt() } catch (_: Exception) { null }
                    if (idx != null) map[word] = idx
                }
                wordIndex = map
                return
            }

            // final fallback: try to read top-level string->int map
            val fallback = mutableMapOf<String, Int>()
            val keys = jo.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val maybeInt = try { jo.getInt(k) } catch (e: Exception) { null }
                if (maybeInt != null) fallback[k] = maybeInt
            }
            if (fallback.isNotEmpty()) wordIndex = fallback

        } catch (e: Exception) {
            e.printStackTrace()
            wordIndex = emptyMap()
        }
    }

    /**
     * Normalize text to mimic Keras Tokenizer default behavior:
     * - lowercasing
     * - filtering punctuation using Keras default filters:
     *   !"#$%&()*+,-./:;<=>?@[\]^_`{|}~\t\n
     * This is a close approximation; if you used custom filters in Python adjust accordingly.
     */
    private fun normalizeText(text: String): String {
        val filters = "!\"#\$%&()*+,-./:;<=>?@[\\\\]^_`{|}~\\t\\n"
        var s = text.lowercase(Locale.getDefault())
        // remove filter characters
        filters.forEach { ch ->
            s = s.replace(ch.toString(), " ")
        }
        // collapse whitespace
        s = s.replace(Regex("\\s+"), " ").trim()
        return s
    }

    /**
     * Convert normalized text to sequence of token indices, apply vocab size cap and padding.
     */
    private fun textToSequence(text: String): IntArray {
        val normalized = normalizeText(text)
        if (normalized.isEmpty()) return IntArray(maxSeqLen) { 0 }

        val tokens = normalized.split(" ").filter { it.isNotEmpty() }
        val seq = tokens.map { token ->
            val idx = wordIndex[token] ?: oovIndex
            // Keras' num_words behavior: keep only indices < vocabSize (if num_words set)
            if (idx >= vocabSize) oovIndex else idx
        }

        val truncated = if (seq.size > maxSeqLen) seq.subList(0, maxSeqLen) else seq

        val padded = IntArray(maxSeqLen) { 0 }
        if (paddingPost) {
            for (i in truncated.indices) padded[i] = truncated[i]
        } else {
            val offset = maxSeqLen - truncated.size
            for (i in truncated.indices) padded[offset + i] = truncated[i]
        }
        return padded
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val afd = context.assets.openFd(fileName)
        FileInputStream(afd.fileDescriptor).use { fis ->
            val fc: FileChannel = fis.channel
            val startOffset = afd.startOffset
            val declaredLength = afd.declaredLength
            return fc.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        }
    }

    /**
     * Predict risk score in [0,1] for given text.
     * Handles INT32 or FLOAT input tensors. Many LSTM-with-Embedding TFLite models expect INT32.
     * If model output is logits rather than sigmoid, you should apply sigmoid here.
     */
    fun predict(text: String): Float {
        val seq = textToSequence(text)

        return try {
            val inputTensor = interpreter.getInputTensor(0)
            when (inputTensor.dataType()) {
                DataType.INT32 -> {
                    // Input shape expected: [1, maxSeqLen]
                    val input = arrayOf(seq)
                    val output = FloatArray(1)
                    interpreter.run(input, output)
                    output[0].coerceIn(0f, 1f)
                }
                DataType.FLOAT32, DataType.FLOAT16 -> {
                    // Convert indices to floats (some TFLite conversions make inputs float)
                    val floatSeq = FloatArray(seq.size) { i -> seq[i].toFloat() }
                    val input = arrayOf(floatSeq)
                    val output = FloatArray(1)
                    interpreter.run(input, output)
                    output[0].coerceIn(0f, 1f)
                }
                else -> {
                    // Unsupported input dtype - fallback
                    0f
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            0f
        }
    }

    fun close() {
        try {
            interpreter.close()
        } catch (_: Exception) { }
    }
}
