package com.example.data

/** Built-in management accounts; ordinary user names never imply administrative access. */
internal fun isManagementAccount(email: String?): Boolean =
    email?.trim()?.lowercase(java.util.Locale.ROOT) in setOf("admin@nrdlojas.com", "mestre@nrdlojas.com")
