package com.example.data

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Locale
import java.util.UUID

data class RestrictedAccess(val loading: Boolean = false, val enabled: Boolean = false, val profile: Boolean = false, val promotions: Boolean = false, val prices: Boolean = false)

object RestrictedAccessRepository {
    fun loginEmail(login: String): String {
        val value = login.trim().lowercase(Locale.ROOT)
        require(value.matches(Regex("[a-z0-9._-]{3,64}"))) { "Use um login de 3 a 64 letras, números, ponto, traço ou sublinhado." }
        return if (value == "admin" || value == "mestre") "$value@nrdlojas.com" else "$value@usuarios.nrdlojas.com"
    }
    fun observe() = callbackFlow {
        val auth = FirebaseAuth.getInstance()
        var registration: com.google.firebase.firestore.ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener {
            registration?.remove()
            val user = it.currentUser
            trySend(RestrictedAccess(loading = user != null))
            if (user == null) trySend(RestrictedAccess())
            else if (user.email == "mestre@nrdlojas.com") trySend(RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true))
            else registration = FirebaseFirestore.getInstance().collection("restricted_access").document(user.uid)
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { doc, error ->
                    if (auth.currentUser?.uid == user.uid) {
                        if (error != null || doc == null) trySend(RestrictedAccess())
                        else if (doc.metadata.isFromCache) trySend(RestrictedAccess(loading = true))
                        else trySend(fromDocument(doc))
                    }
                }
        }
        auth.addAuthStateListener(listener)
        awaitClose { registration?.remove(); auth.removeAuthStateListener(listener) }
    }
    private fun fromDocument(doc: com.google.firebase.firestore.DocumentSnapshot): RestrictedAccess {
        val enabled = doc.getBoolean("enabled") == true
        return RestrictedAccess(enabled = enabled, profile = enabled && doc.getBoolean("profile") == true,
            promotions = enabled && doc.getBoolean("promotions") == true, prices = enabled && doc.getBoolean("prices") == true)
    }
    suspend fun current(): RestrictedAccess {
        val user = FirebaseAuth.getInstance().currentUser ?: return RestrictedAccess()
        if (user.email == "mestre@nrdlojas.com") return RestrictedAccess(enabled = true, profile = true, promotions = true, prices = true)
        return try {
            val doc = FirebaseFirestore.getInstance().collection("restricted_access").document(user.uid).get(Source.SERVER).await()
            if (FirebaseAuth.getInstance().currentUser?.uid == user.uid) fromDocument(doc) else RestrictedAccess()
        } catch (_: Exception) { RestrictedAccess() }
    }
    suspend fun create(login: String, password: String, profile: Boolean, promotions: Boolean, prices: Boolean) {
        check(FirebaseAuth.getInstance().currentUser?.email == "mestre@nrdlojas.com") { "Acesso exclusivo do Mestre." }
        val email = loginEmail(login)
        require(!email.endsWith("@nrdlojas.com")) { "Esse login está reservado." }
        require(password.length >= 8) { "A senha deve ter pelo menos 8 caracteres." }
        require(profile || promotions || prices) { "Selecione pelo menos uma aba." }
        val base = FirebaseApp.getInstance()
        val secondary = FirebaseApp.initializeApp(base.applicationContext, base.options, "account-${UUID.randomUUID()}")
        val auth = FirebaseAuth.getInstance(secondary)
        try {
            val user = auth.createUserWithEmailAndPassword(email, password).await().user ?: error("Não foi possível criar o login.")
            try {
                FirebaseFirestore.getInstance().collection("restricted_access").document(user.uid).set(mapOf(
                    "login" to login.trim().lowercase(Locale.ROOT), "enabled" to true,
                    "profile" to profile, "promotions" to promotions, "prices" to prices
                )).await()
            } catch (error: Exception) { runCatching { user.delete().await() }; throw error }
        } finally { auth.signOut(); secondary.delete() }
    }
}
