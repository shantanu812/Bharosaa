package com.example.seniorantiscam

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ResultActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val txtResult = findViewById<TextView>(R.id.txtResult)
        val txtMessage = findViewById<TextView>(R.id.txtMessage)
        val btnAction = findViewById<Button>(R.id.btnAction)

        val message = intent.getStringExtra("message") ?: ""
        val riskScore = intent.getFloatExtra("riskScore", 0f)

        txtMessage.text = message

        when {
            riskScore < 0.3 -> {
                txtResult.text = "✅ Safe Message"
                txtResult.setTextColor(0xFF2E7D32.toInt())
                btnAction.text = "Go Back"
            }
            riskScore < 0.7 -> {
                txtResult.text = "⚠️ Suspicious Message"
                txtResult.setTextColor(0xFFF9A825.toInt())
                btnAction.text = "Call Trusted Contact"
            }
            else -> {
                txtResult.text = "🚨 Scam Alert"
                txtResult.setTextColor(0xFFC62828.toInt())
                btnAction.text = "Call Trusted Contact"
            }
        }

        btnAction.setOnClickListener {
            finish()
        }
    }
}