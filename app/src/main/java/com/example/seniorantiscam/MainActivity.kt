package com.example.seniorantiscam

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import android.content.ClipboardManager
import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.DownloadConditions.*
import com.google.mlkit.nl.translate.*

class MainActivity : AppCompatActivity() {
    private lateinit var btnPaste: Button
    private lateinit var btnTranslate: Button
    var isHindi = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        val edtMessage = findViewById<EditText>(R.id.edtMessage)
        val btnCheck = findViewById<Button>(R.id.btnCheck)
        val btnPaste = findViewById<Button>(R.id.btnPaste)
        btnTranslate = findViewById(R.id.btnTranslate)

        btnCheck.setOnClickListener {
            val message = edtMessage.text.toString().trim()

            if (message.isEmpty()) {
                Toast.makeText(this, "Please enter a message", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val riskScore = mockPredict(message)

            val intent = Intent(this, ResultActivity::class.java)
            intent.putExtra("message", message)
            intent.putExtra("riskScore", riskScore)
            startActivity(intent)
        }
        btnPaste.setOnClickListener {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = clipboard.primaryClip

            if (clip != null && clip.itemCount > 0) {
                val pastedText = clip.getItemAt(0).text
                edtMessage.setText(pastedText)
            } else {
                Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            }
        }
        btnTranslate.setOnClickListener {

            val text = edtMessage.text.toString()
            if (text.isEmpty()) {
                Toast.makeText(this, "No text to translate", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val options = if (!isHindi) {
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.ENGLISH)
                    .setTargetLanguage(TranslateLanguage.HINDI)
                    .build()
            } else {
                TranslatorOptions.Builder()
                    .setSourceLanguage(TranslateLanguage.HINDI)
                    .setTargetLanguage(TranslateLanguage.ENGLISH)
                    .build()
            }

            val translator = Translation.getClient(options)

            val conditions = Builder().build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    translator.translate(text)
                        .addOnSuccessListener { translatedText ->
                            edtMessage.setText(translatedText)

                            isHindi = !isHindi
                            btnTranslate.text =
                                if (isHindi) "Translate to English" else "Translate to Hindi"
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Translation failed", Toast.LENGTH_SHORT).show()
                        }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Language model download failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // TEMPORARY MOCK MODEL
    private fun mockPredict(text: String): Float {
        val lower = text.lowercase()

        return when {
            listOf("otp", "bank", "urgent", "click", "verify").any { it in lower } -> 0.85f
            listOf("offer", "free", "winner").any { it in lower } -> 0.6f
            else -> 0.2f
        }
    }

}