package com.master.contacthelper.models

const val SMT_PRIVATE = "smt_private"   // used at the contact source of local contacts hidden from other apps
data class ContactSource(var name: String, var type: String, var publicName: String) {
    fun getFullIdentifier(): String {
        return if (type == SMT_PRIVATE) {
            type
        } else {
            "$name:$type"
        }
    }
}
