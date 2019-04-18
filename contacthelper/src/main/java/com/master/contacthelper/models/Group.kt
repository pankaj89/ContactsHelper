package com.master.contacthelper.models

import java.io.Serializable

const val FIRST_GROUP_ID = 10000L

data class Group(
    var id: Long?,
    var title: String,
    var contactsCount: Int = 0) : Serializable {

    fun addContact() = contactsCount++

    fun getBubbleText() = title

    fun isPrivateSecretGroup() = id ?: 0 >= FIRST_GROUP_ID
}
