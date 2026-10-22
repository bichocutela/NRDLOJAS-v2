package com.example.data.training

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

object TrainingRepository {
    fun defaults(context: Context) = TrainingCodec.decode(context.assets.open("training-default.json").bufferedReader().use { it.readText() })
    fun isMaster() = FirebaseAuth.getInstance().currentUser?.email.equals("mestre@nrdlojas.com", true)
    fun observe() = callbackFlow<TrainingConfig?> {
        val auth = FirebaseAuth.getInstance()
        val db = FirebaseFirestore.getInstance()
        var content: com.google.firebase.firestore.ListenerRegistration? = null
        var gate: com.google.firebase.firestore.ListenerRegistration? = null
        val listener = FirebaseAuth.AuthStateListener {
            gate?.remove(); content?.remove(); trySend(null)
            val master = isMaster()
            // This public document contains only visibility. Its listener stays alive
            // while content is private, so enabling Todos also reaches existing sessions.
            gate = db.collection("config").document("training_access").addSnapshotListener { snapshot, gateError ->
                content?.remove(); content = null; trySend(null)
                if (gateError == null && (master || snapshot?.getString("visibility") == "ALL")) {
                    content = db.collection("config").document("training").addSnapshotListener { snap, failure ->
                        if (failure != null || snap == null || !snap.exists()) trySend(null)
                        else trySend(runCatching {
                            TrainingCodec.decode(snap.getString("payload") ?: error("Sem conteúdo"))
                                .copy(visibility = TrainingVisibility.valueOf(snap.getString("visibility") ?: "OFF"), revision = snap.getLong("revision") ?: 0)
                                .takeIf { it.validate() == null }
                        }.getOrNull())
                    }
                }
            }
        }
        auth.addAuthStateListener(listener)
        awaitClose { gate?.remove(); content?.remove(); auth.removeAuthStateListener(listener) }
    }
    suspend fun load(): TrainingConfig? {
        val snapshot = FirebaseFirestore.getInstance().collection("config").document("training")
            .get(com.google.firebase.firestore.Source.SERVER).await()
        if (!snapshot.exists()) return null
        val config = TrainingCodec.decode(snapshot.getString("payload") ?: error("Configuração sem conteúdo."))
            .copy(visibility = TrainingVisibility.valueOf(snapshot.getString("visibility") ?: "OFF"), revision = snapshot.getLong("revision") ?: 0)
        config.validate()?.let { error(it) }
        return config
    }
    suspend fun announce(id: String): String = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        check(isMaster()) { "Somente o Mestre pode anunciar treinamentos." }
        val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token ?: error("Faça login novamente.")
        val body = org.json.JSONObject().put("action", "PUBLISH_TRAINING").put("trainingId", id).toString()
        val media = "application/json".toMediaType()
        val request = okhttp3.Request.Builder().url(com.example.BuildConfig.SUPABASE_URL + "/functions/v1/send-fcm")
            .header("Authorization", "Bearer " + com.example.BuildConfig.SUPABASE_ANON_KEY)
            .header("apikey", com.example.BuildConfig.SUPABASE_ANON_KEY).header("x-firebase-token", token)
            .post(body.toRequestBody(media)).build()
        okhttp3.OkHttpClient.Builder().callTimeout(35, java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val data = org.json.JSONObject(response.body?.string() ?: "{}")
            check(response.isSuccessful && data.optBoolean("ok")) { data.optString("error", "Falha ao publicar aviso.") }
            if (data.optBoolean("alreadyAnnounced")) "Este treinamento já foi anunciado." else "Aviso enviado aos inscritos."
        }
    }
    suspend fun save(draft: TrainingConfig, expectedRevision: Long): TrainingConfig {
        check(isMaster()) { "Somente o Mestre pode editar o Treinamento." }
        draft.validate()?.let { error(it) }
        val next = draft.copy(revision = System.currentTimeMillis().coerceAtLeast(expectedRevision + 1))
        val ref = FirebaseFirestore.getInstance().collection("config").document("training")
        FirebaseFirestore.getInstance().runTransaction { tx ->
            val previous = tx.get(ref)
            check((previous.getLong("revision") ?: 0) == expectedRevision) { "O treinamento foi alterado em outro aparelho. Reabra antes de salvar." }
            tx.set(FirebaseFirestore.getInstance().collection("config").document("training_access"), mapOf("visibility" to next.visibility.name, "revision" to next.revision))
            tx.set(ref, mapOf("visibility" to next.visibility.name, "revision" to next.revision, "payload" to TrainingCodec.encode(next)))
        }.await()
        return next
    }
}
