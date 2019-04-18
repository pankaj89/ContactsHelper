package com.master.contact

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import android.support.v7.app.AppCompatActivity
import android.util.Log
import com.master.contact.R
import com.master.contacthelper.ContactsHelper
import kotlinx.android.synthetic.main.activity_main.*
import java.lang.Exception

class MainActivity : AppCompatActivity() {

    var contactsHelper: ContactsHelper? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        contactsHelper = ContactsHelper(this@MainActivity)
        btnReadContacts.setOnClickListener {
            /*contactsHelper.getContacts {
                Log.i("CONTACT", it.size.toString())
                val ll = it.filter { it.emails.size > 0 }
                tvContacts.setText("Contacts size : " + ll.size)
            }*/


            val intent = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI);
            startActivityForResult(intent, 1);


        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 1) {
            if (resultCode == RESULT_OK) {
                val contactData = data?.getData();
                val cursor = getContentResolver().query(contactData, null, null, null, null);
                cursor.moveToFirst();
                val contactId1 =
                    cursor.getString(cursor.getColumnIndexOrThrow("name_raw_contact_id"));//ContactsContract.Contacts.NAME_RAW_CONTACT_ID
                val contactId2 =
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID));//ContactsContract.Contacts.NAME_RAW_CONTACT_ID
                cursor.close()
                try {
                    val contactId = if (contactId1.isNotBlank()) contactId1 else contactId2
                    if (contactId.isNotBlank()) {
                        contactsHelper?.getContacts(contactId = contactId.toInt()) {
                            Log.i("CONTACT", it.size.toString())
                            if (it.isNotEmpty())
                                tvContacts.setText(it.get(0).toString())
                        }
                    }
                } catch (e: Exception) {

                }
            }
        }
    }
}

