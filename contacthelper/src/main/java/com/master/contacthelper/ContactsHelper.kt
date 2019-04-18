package com.master.contacthelper

import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.text.TextUtils
import android.util.SparseArray
import com.master.contacthelper.extensions.normalizeNumber
import com.master.contacthelper.extensions.times
import com.master.contacthelper.models.*
import com.master.contacthelper.extensions.*
import com.master.contacthelper.models.Contact
import java.util.*
import kotlin.collections.ArrayList

class ContactsHelper(val context: Context) {
    private val BATCH_SIZE = 100
    private var displayContactSources = ArrayList<String>()

    fun Context.getPrivateContactSource() =
        ContactSource(SMT_PRIVATE, SMT_PRIVATE, "Phone storage (not visible by other apps)")

    fun Context.getVisibleContactSources(): ArrayList<String> {
        val sources = getAllContactSources()
        return ArrayList(sources).map { it.name }.toMutableList() as ArrayList<String>
    }

    fun getDeviceContactSources(): LinkedHashSet<ContactSource> {
        val sources = LinkedHashSet<ContactSource>()
        if (!context.hasContactPermissions()) {
            return sources
        }

        val accounts = AccountManager.get(context).accounts
        accounts.forEach {
            var publicName = it.name
            /*if (it.type == TELEGRAM_PACKAGE) {
                publicName += " (${context.getString(R.string.telegram)})"
            }*/
            val contactSource = ContactSource(it.name, it.type, publicName)
            sources.add(contactSource)
        }

        val contentResolverAccounts = getContentResolverAccounts().filter {
            it.name.isNotEmpty() && it.type.isNotEmpty() && !accounts.contains(Account(it.name, it.type))
        }
        sources.addAll(contentResolverAccounts)

        return sources
    }

    fun Context.getAllContactSources(): List<ContactSource> {
        val sources = ContactsHelper(this).getDeviceContactSources()
        sources.add(getPrivateContactSource())
        return sources.toMutableList()
    }

    fun getContacts(
        ignoredContactSources: HashSet<String>? = null,
        contactId: Int? = null,
        callback: (ArrayList<Contact>) -> Unit
    ) {
        Thread {
            val contacts = SparseArray<Contact>()
            displayContactSources = context.getVisibleContactSources()

            getDeviceContacts(contacts, contactId)

            val contactsSize = contacts.size()
            var tempContacts = ArrayList<Contact>(contactsSize)
            val resultContacts = ArrayList<Contact>(contactsSize)
            val showOnlyContactsWithNumbers = false

            (0 until contactsSize).filter {
                true
            }.mapTo(tempContacts) {
                contacts.valueAt(it)
            }

            Handler(Looper.getMainLooper()).post {
                callback(tempContacts)
            }
        }.start()
    }

    private fun getContentResolverAccounts(): HashSet<ContactSource> {
        val sources = HashSet<ContactSource>()
        arrayOf(
            ContactsContract.Groups.CONTENT_URI,
            ContactsContract.Settings.CONTENT_URI,
            ContactsContract.RawContacts.CONTENT_URI
        ).forEach {
            fillSourcesFromUri(it, sources)
        }

        return sources
    }

    private fun fillSourcesFromUri(uri: Uri, sources: HashSet<ContactSource>) {
        val projection = arrayOf(
            ContactsContract.RawContacts.ACCOUNT_NAME,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, null, null, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val name = cursor.getStringValue(ContactsContract.RawContacts.ACCOUNT_NAME)
                        ?: ""
                    val type = cursor.getStringValue(ContactsContract.RawContacts.ACCOUNT_TYPE)
                        ?: ""
                    var publicName = name
//                    if (type == TELEGRAM_PACKAGE) {
//                        publicName += " (${context.getString(R.string.telegram)})"
//                    }

                    val source = ContactSource(name, type, publicName)
                    sources.add(source)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
        } finally {
            cursor?.close()
        }
    }

    private fun getSortString(): String {
        return "${CommonDataKinds.StructuredName.GIVEN_NAME} COLLATE NOCASE DESC"
    }

    private fun getDeviceContacts(contacts: SparseArray<Contact>, contactId: Int?) {
        if (!context.hasContactPermissions()) {
            return
        }

        val uri = ContactsContract.Data.CONTENT_URI
        val projection = getContactProjection()

        val selection =
            "${ContactsContract.Data.MIMETYPE} = ?" + if (contactId != null) " AND ${ContactsContract.Data.RAW_CONTACT_ID} = ?" else ""
        val selectionArgs = if (contactId != null) arrayOf(
            CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE,
            contactId.toString()
        ) else arrayOf(CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
        val sortOrder = getSortString()

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            if (cursor?.moveToFirst() == true) {
                do {
                    val accountName = cursor.getStringValue(ContactsContract.RawContacts.ACCOUNT_NAME)
                        ?: ""
                    val accountType = cursor.getStringValue(ContactsContract.RawContacts.ACCOUNT_TYPE)
                        ?: ""

                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val prefix = cursor.getStringValue(CommonDataKinds.StructuredName.PREFIX) ?: ""
                    val firstName = cursor.getStringValue(CommonDataKinds.StructuredName.GIVEN_NAME)
                        ?: ""
                    val middleName = cursor.getStringValue(CommonDataKinds.StructuredName.MIDDLE_NAME)
                        ?: ""
                    val surname = cursor.getStringValue(CommonDataKinds.StructuredName.FAMILY_NAME)
                        ?: ""
                    val suffix = cursor.getStringValue(CommonDataKinds.StructuredName.SUFFIX) ?: ""
                    val nickname = ""
                    val photoUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_URI)
                        ?: ""
                    val numbers = ArrayList<PhoneNumber>()          // proper value is obtained below
                    val emails = ArrayList<Email>()
                    val addresses = ArrayList<Address>()
                    val events = ArrayList<Event>()
                    val starred = cursor.getIntValue(CommonDataKinds.StructuredName.STARRED)
                    val contactId = cursor.getIntValue(ContactsContract.Data.CONTACT_ID)
                    val thumbnailUri = cursor.getStringValue(CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI)
                        ?: ""
                    val notes = ""
                    val groups = ArrayList<Group>()
                    val organization = Organization("", "")
                    val websites = ArrayList<String>()
                    val ims = ArrayList<IM>()
                    val contact = Contact(
                        id,
                        prefix,
                        firstName,
                        middleName,
                        surname,
                        suffix,
                        nickname,
                        photoUri,
                        numbers,
                        emails,
                        addresses,
                        events,
                        accountName,
                        starred,
                        contactId,
                        thumbnailUri,
                        null,
                        notes,
                        groups,
                        organization,
                        websites,
                        ims
                    )

                    contacts.put(id, contact)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        val phoneNumbers = getPhoneNumbers(null)
        var size = phoneNumbers.size()
        for (i in 0 until size) {
            val key = phoneNumbers.keyAt(i)
            if (contacts[key] != null) {
                val numbers = phoneNumbers.valueAt(i)
                contacts[key].phoneNumbers = numbers
            }
        }

        val nicknames = getNicknames()
        size = nicknames.size()
        for (i in 0 until size) {
            val key = nicknames.keyAt(i)
            contacts[key]?.nickname = nicknames.valueAt(i)
        }

        val emails = getEmails()
        size = emails.size()
        for (i in 0 until size) {
            val key = emails.keyAt(i)
            contacts[key]?.emails = emails.valueAt(i)
        }

        val addresses = getAddresses()
        size = addresses.size()
        for (i in 0 until size) {
            val key = addresses.keyAt(i)
            contacts[key]?.addresses = addresses.valueAt(i)
        }

        val IMs = getIMs()
        size = IMs.size()
        for (i in 0 until size) {
            val key = IMs.keyAt(i)
            contacts[key]?.IMs = IMs.valueAt(i)
        }

        val events = getEvents()
        size = events.size()
        for (i in 0 until size) {
            val key = events.keyAt(i)
            contacts[key]?.events = events.valueAt(i)
        }

        val notes = getNotes()
        size = notes.size()
        for (i in 0 until size) {
            val key = notes.keyAt(i)
            contacts[key]?.notes = notes.valueAt(i)
        }

        val organizations = getOrganizations()
        size = organizations.size()
        for (i in 0 until size) {
            val key = organizations.keyAt(i)
            contacts[key]?.organization = organizations.valueAt(i)
        }

        val websites = getWebsites()
        size = websites.size()
        for (i in 0 until size) {
            val key = websites.keyAt(i)
            contacts[key]?.websites = websites.valueAt(i)
        }
    }

    private fun getContactProjection() = arrayOf(
        ContactsContract.Data.CONTACT_ID,
        ContactsContract.Data.RAW_CONTACT_ID,
        CommonDataKinds.StructuredName.PREFIX,
        CommonDataKinds.StructuredName.GIVEN_NAME,
        CommonDataKinds.StructuredName.MIDDLE_NAME,
        CommonDataKinds.StructuredName.FAMILY_NAME,
        CommonDataKinds.StructuredName.SUFFIX,
        CommonDataKinds.StructuredName.PHOTO_URI,
        CommonDataKinds.StructuredName.PHOTO_THUMBNAIL_URI,
        CommonDataKinds.StructuredName.STARRED,
        ContactsContract.RawContacts.ACCOUNT_NAME,
        ContactsContract.RawContacts.ACCOUNT_TYPE
    )

    private fun getQuestionMarks() = "?,".times(displayContactSources.filter { it.isNotEmpty() }.size).trimEnd(',')

    private fun getSourcesSelection(
        addMimeType: Boolean = false,
        addContactId: Boolean = false,
        useRawContactId: Boolean = true
    ): String {
        val strings = ArrayList<String>()
        if (addMimeType) {
            strings.add("${ContactsContract.Data.MIMETYPE} = ?")
        }

        if (addContactId) {
            strings.add("${if (useRawContactId) ContactsContract.Data.RAW_CONTACT_ID else ContactsContract.Data.CONTACT_ID} = ?")
        } else {
            // sometimes local device storage has null account_name, handle it properly
            val accountnameString = StringBuilder()
            if (displayContactSources.contains("")) {
                accountnameString.append("(")
            }
            accountnameString.append("${ContactsContract.RawContacts.ACCOUNT_NAME} IN (${getQuestionMarks()})")
            if (displayContactSources.contains("")) {
                accountnameString.append(" OR ${ContactsContract.RawContacts.ACCOUNT_NAME} IS NULL)")
            }
            strings.add(accountnameString.toString())
        }

        return TextUtils.join(" AND ", strings)
    }

    private fun getSourcesSelectionArgs(mimetype: String? = null, contactId: Int? = null): Array<String> {
        val args = ArrayList<String>()

        if (mimetype != null) {
            args.add(mimetype)
        }

        if (contactId != null) {
            args.add(contactId.toString())
        } else {
            args.addAll(displayContactSources.filter { it.isNotEmpty() })
        }

        return args.toTypedArray()
    }

    fun getPhoneNumbers(contactId: Int? = null): SparseArray<ArrayList<PhoneNumber>> {
        val phoneNumbers = SparseArray<ArrayList<PhoneNumber>>()
        val uri = CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Phone.NUMBER,
            CommonDataKinds.Phone.NORMALIZED_NUMBER,
            CommonDataKinds.Phone.TYPE,
            CommonDataKinds.Phone.LABEL
        )

        val selection = if (contactId == null) getSourcesSelection() else "${ContactsContract.Data.RAW_CONTACT_ID} = ?"
        val selectionArgs = if (contactId == null) getSourcesSelectionArgs() else arrayOf(contactId.toString())

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val number = cursor.getStringValue(CommonDataKinds.Phone.NUMBER) ?: continue
                    val normalizedNumber = cursor.getStringValue(CommonDataKinds.Phone.NORMALIZED_NUMBER)
                        ?: number.normalizeNumber()
                    val type = cursor.getIntValue(CommonDataKinds.Phone.TYPE)
                    val label = cursor.getStringValue(CommonDataKinds.Phone.LABEL) ?: ""

                    if (phoneNumbers[id] == null) {
                        phoneNumbers.put(id, ArrayList())
                    }

                    val phoneNumber = PhoneNumber(number, type, label, normalizedNumber)
                    phoneNumbers[id].add(phoneNumber)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return phoneNumbers
    }

    fun getFirstName(contactId: Int? = null): String {

        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)

        val selection = if (contactId == null) getSourcesSelection() else "${ContactsContract.Contacts._ID} = ?"
        val selectionArgs = if (contactId == null) getSourcesSelectionArgs() else arrayOf(contactId.toString())

        val sortOrder = getSortString()

        var nicknames = ""

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    nicknames = cursor.getStringValue(ContactsContract.Contacts.DISPLAY_NAME) ?: continue
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return nicknames
    }

    fun getNicknames(contactId: Int? = null): SparseArray<String> {
        val nicknames = SparseArray<String>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            Nickname.NAME
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(Nickname.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val nickname = cursor.getStringValue(Nickname.NAME) ?: continue
                    nicknames.put(id, nickname)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return nicknames
    }

    fun getEmails(contactId: Int? = null): SparseArray<ArrayList<Email>> {
        val emails = SparseArray<ArrayList<Email>>()
        val uri = CommonDataKinds.Email.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Email.DATA,
            CommonDataKinds.Email.TYPE,
            CommonDataKinds.Email.LABEL
        )

        val selection = if (contactId == null) getSourcesSelection() else "${ContactsContract.Data.RAW_CONTACT_ID} = ?"
        val selectionArgs = if (contactId == null) getSourcesSelectionArgs() else arrayOf(contactId.toString())

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val email = cursor.getStringValue(CommonDataKinds.Email.DATA) ?: continue
                    val type = cursor.getIntValue(CommonDataKinds.Email.TYPE)
                    val label = cursor.getStringValue(CommonDataKinds.Email.LABEL) ?: ""

                    if (emails[id] == null) {
                        emails.put(id, ArrayList())
                    }

                    emails[id]!!.add(Email(email, type, label))
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return emails
    }

    fun getAddresses(contactId: Int? = null): SparseArray<ArrayList<Address>> {
        val addresses = SparseArray<ArrayList<Address>>()
        val uri = CommonDataKinds.StructuredPostal.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
            CommonDataKinds.StructuredPostal.TYPE,
            CommonDataKinds.StructuredPostal.LABEL
        )

        val selection = if (contactId == null) getSourcesSelection() else "${ContactsContract.Data.RAW_CONTACT_ID} = ?"
        val selectionArgs = if (contactId == null) getSourcesSelectionArgs() else arrayOf(contactId.toString())

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val address = cursor.getStringValue(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
                        ?: continue
                    val type = cursor.getIntValue(CommonDataKinds.StructuredPostal.TYPE)
                    val label = cursor.getStringValue(CommonDataKinds.StructuredPostal.LABEL) ?: ""

                    if (addresses[id] == null) {
                        addresses.put(id, ArrayList())
                    }

                    addresses[id]!!.add(Address(address, type, label))
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return addresses
    }

    fun getIMs(contactId: Int? = null): SparseArray<ArrayList<IM>> {
        val IMs = SparseArray<ArrayList<IM>>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Im.DATA,
            CommonDataKinds.Im.PROTOCOL,
            CommonDataKinds.Im.CUSTOM_PROTOCOL
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(CommonDataKinds.Im.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val IM = cursor.getStringValue(CommonDataKinds.Im.DATA) ?: continue
                    val type = cursor.getIntValue(CommonDataKinds.Im.PROTOCOL)
                    val label = cursor.getStringValue(CommonDataKinds.Im.CUSTOM_PROTOCOL) ?: ""

                    if (IMs[id] == null) {
                        IMs.put(id, ArrayList())
                    }

                    IMs[id]!!.add(IM(IM, type, label))
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return IMs
    }

    fun getEvents(contactId: Int? = null): SparseArray<ArrayList<Event>> {
        val events = SparseArray<ArrayList<Event>>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Event.START_DATE,
            CommonDataKinds.Event.TYPE
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(CommonDataKinds.Event.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val startDate = cursor.getStringValue(CommonDataKinds.Event.START_DATE)
                        ?: continue
                    val type = cursor.getIntValue(CommonDataKinds.Event.TYPE)

                    if (events[id] == null) {
                        events.put(id, ArrayList())
                    }

                    events[id]!!.add(Event(startDate, type))
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return events
    }

    fun getNotes(contactId: Int? = null): SparseArray<String> {
        val notes = SparseArray<String>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            Note.NOTE
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(Note.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val note = cursor.getStringValue(Note.NOTE) ?: continue
                    notes.put(id, note)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return notes
    }

    fun getOrganizations(contactId: Int? = null): SparseArray<Organization> {
        val organizations = SparseArray<Organization>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Organization.COMPANY,
            CommonDataKinds.Organization.TITLE
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(CommonDataKinds.Organization.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val company = cursor.getStringValue(CommonDataKinds.Organization.COMPANY) ?: ""
                    val title = cursor.getStringValue(CommonDataKinds.Organization.TITLE) ?: ""
                    if (company.isEmpty() && title.isEmpty()) {
                        continue
                    }

                    val organization = Organization(company, title)
                    organizations.put(id, organization)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return organizations
    }

    fun getWebsites(contactId: Int? = null): SparseArray<ArrayList<String>> {
        val websites = SparseArray<ArrayList<String>>()
        val uri = ContactsContract.Data.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Data.RAW_CONTACT_ID,
            CommonDataKinds.Website.URL
        )

        val selection = getSourcesSelection(true, contactId != null)
        val selectionArgs = getSourcesSelectionArgs(CommonDataKinds.Website.CONTENT_ITEM_TYPE, contactId)

        var cursor: Cursor? = null
        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor?.moveToFirst() == true) {
                do {
                    val id = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID)
                    val url = cursor.getStringValue(CommonDataKinds.Website.URL) ?: continue

                    if (websites[id] == null) {
                        websites.put(id, ArrayList())
                    }

                    websites[id]!!.add(url)
                } while (cursor.moveToNext())
            }
        } catch (e: Exception) {
            context.showErrorToast(e)
        } finally {
            cursor?.close()
        }

        return websites
    }

}
