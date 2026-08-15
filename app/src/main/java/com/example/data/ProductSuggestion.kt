package com.example.data

data class ProductSuggestion(
    val id: String = "",
    val text: String = "",
    val submittedBy: String = "Usuário do aplicativo",
    val submittedByUid: String = "",
    val installationId: String = "",
    val appVersion: String = "",
    val createdAt: Long = 0L,
    val status: String = STATUS_PENDING
) {
    companion object {
        const val STATUS_PENDING = "PENDENTE"
        const val STATUS_FIXED = "CORRIGIDO"
    }
}
