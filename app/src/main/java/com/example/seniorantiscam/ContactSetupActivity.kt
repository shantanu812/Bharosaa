package com.example.seniorantiscam

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class ContactSetupActivity : AppCompatActivity() {

    private val CONTACT_PERMISSION_CODE = 101
    private val PICK_CONTACT_CODE = 102
    private val MAX_CONTACTS = 4

    private lateinit var txtContacts: TextView
    private lateinit var sharedPrefs: SharedPreferences
    private val contactList = mutableListOf<String>()
    private lateinit var btnContinue: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_contact_setup)

        txtContacts = findViewById(R.id.txtContacts)
        val btnPickContact = findViewById<Button>(R.id.btnPickContact)
        btnContinue = findViewById(R.id.btnContinue)

        sharedPrefs = getSharedPreferences("trusted_contacts", MODE_PRIVATE)
        loadSavedContacts()

        btnPickContact.setOnClickListener {
            if (contactList.size >= MAX_CONTACTS) {
                Toast.makeText(this, "You can add only 4 contacts", Toast.LENGTH_SHORT).show()
            } else {
                checkContactPermission()
            }
        }


        btnContinue.setOnClickListener {
            // Move to main message screen
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun loadSavedContacts() {
        contactList.clear()
        for (i in 0 until MAX_CONTACTS) {
            val contact = sharedPrefs.getString("contact_$i", null)
            if (contact != null) {
                contactList.add(contact)
            }
        }
        updateContactDisplay()
    }

    private fun updateContactDisplay() {
        txtContacts.text = if (contactList.isEmpty()) {
            btnContinue.isEnabled = false
            "No contacts added yet"
        } else {
            btnContinue.isEnabled = true
            contactList.joinToString("\n")
        }
    }

    private fun checkContactPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.READ_CONTACTS),
                CONTACT_PERMISSION_CODE
            )
        } else {
            openContactPicker()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == CONTACT_PERMISSION_CODE &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            openContactPicker()
        }
    }

    private fun openContactPicker() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == PICK_CONTACT_CODE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                val cursor: Cursor? = contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )

                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex =
                            it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex =
                            it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                        val name = it.getString(nameIndex)
                        val number = it.getString(numberIndex)

                        val contactInfo = "$name : $number"

                        contactList.add(contactInfo)
                        saveContacts()
                        updateContactDisplay()
                    }
                }
            }
        }
    }

    private fun saveContacts() {
        val editor = sharedPrefs.edit()
        editor.clear()
        contactList.forEachIndexed { index, contact ->
            editor.putString("contact_$index", contact)
        }
        editor.apply()
    }
}