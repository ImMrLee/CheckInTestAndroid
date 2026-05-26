package com.example.checkintest

data class Contact(
    val name: String,
    val phoneNumber: String,
    val role: ContactRole,
    val note: String? = null
)

enum class ContactRole {
    GRID_MEMBER,
    POLICE
}