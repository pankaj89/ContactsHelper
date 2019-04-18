package com.master.contacthelper.models

import android.graphics.Bitmap
import android.telephony.PhoneNumberUtils
import com.master.contacthelper.extensions.normalizeNumber

data class Contact(var id: Int, var prefix: String, var firstName: String, var middleName: String, var surname: String, var suffix: String, var nickname: String,
                   var photoUri: String, var phoneNumbers: ArrayList<PhoneNumber>, var emails: ArrayList<Email>, var addresses: ArrayList<Address>,
                   var events: ArrayList<Event>, var source: String, var starred: Int, var contactId: Int, var thumbnailUri: String, var photo: Bitmap?, var notes: String,
                   var groups: ArrayList<Group>, var organization: Organization, var websites: ArrayList<String>, var IMs: ArrayList<IM>)
{
    companion object {
        var sorting = 0
        var startWithSurname = false
    }


    fun getNameToDisplay(): String {
        var firstPart = if (startWithSurname) surname else firstName
        if (middleName.isNotEmpty()) {
            firstPart += " $middleName"
        }

        val lastPart = if (startWithSurname) firstName else surname
        val suffixComma = if (suffix.isEmpty()) "" else ", $suffix"
        val fullName = "$prefix $firstPart $lastPart$suffixComma".trim()
        return if (fullName.isEmpty()) {
            if (organization.isNotEmpty()) {
                getFullCompany()
            } else {
                emails.firstOrNull()?.value?.trim() ?: ""
            }
        } else {
            fullName
        }
    }

    fun getStringToCompare(): String {
        val newEmails = ArrayList<Email>()
        emails.mapTo(newEmails) { Email(it.value, 0, "") }

        return copy(id = 0, prefix = "", firstName = getNameToDisplay().toLowerCase(), middleName = "", surname = "", suffix = "", nickname = "", photoUri = "",
            phoneNumbers = ArrayList(), events = ArrayList(), addresses = ArrayList(), emails = newEmails, source = "", starred = 0,
            contactId = 0, thumbnailUri = "", notes = "", groups = ArrayList(), websites = ArrayList(), organization = Organization("", ""),
            IMs = ArrayList()).toString()
    }

    fun getHashToCompare() = getStringToCompare().hashCode()

    private fun getFullCompany(): String {
        var fullOrganization = if (organization.company.isEmpty()) "" else "${organization.company}, "
        fullOrganization += organization.jobPosition
        return fullOrganization.trim().trimEnd(',')
    }

    fun isABusinessContact() = prefix.isEmpty() && firstName.isEmpty() && middleName.isEmpty() && surname.isEmpty() && suffix.isEmpty() && organization.isNotEmpty()

    fun doesContainPhoneNumber(text: String, convertLetters: Boolean): Boolean {
        return if (text.isNotEmpty()) {
            val normalizedText = if (convertLetters) text.normalizeNumber() else text
            phoneNumbers.any {
                PhoneNumberUtils.compare(it.normalizedNumber, normalizedText) ||
                    it.value.contains(text) ||
                    it.normalizedNumber?.contains(normalizedText) == true ||
                    it.value.normalizeNumber().contains(normalizedText)
            }
        } else {
            false
        }
    }
}
