package com.example.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class XiaomiIslandScenarioPhase(
    val progress: Int,
    val title: String,
    val content: String,
    val detail: String,
    val waitMillis: Long
)

data class XiaomiIslandScenario(
    val name: String,
    val version: Long,
    val enabled: Boolean,
    val phases: List<XiaomiIslandScenarioPhase>,
    val remote: Boolean
)

object XiaomiIslandScenarioStore {
    private const val COLLECTION = "config"
    private const val DOCUMENT = "xiaomiSuperIslandScenario"

    private val defaultScenario = XiaomiIslandScenario(
        name = "Atualização NRD",
        version = 1,
        enabled = true,
        remote = false,
        phases = listOf(
            XiaomiIslandScenarioPhase(0, "NRD · atualização", "Conectando ao servidor…", "Conectando", 2200),
            XiaomiIslandScenarioPhase(8, "NRD · atualização", "Preparando download…", "Preparando", 2200),
            XiaomiIslandScenarioPhase(20, "NRD · baixando", "Baixando atualização… 20%", "20%", 2200),
            XiaomiIslandScenarioPhase(38, "NRD · baixando", "Baixando atualização… 38%", "38%", 2200),
            XiaomiIslandScenarioPhase(56, "NRD · baixando", "Baixando atualização… 56%", "56%", 2200),
            XiaomiIslandScenarioPhase(74, "NRD · baixando", "Baixando atualização… 74%", "74%", 2200),
            XiaomiIslandScenarioPhase(88, "NRD · verificando", "Verificando pacote…", "Verificando", 2600),
            XiaomiIslandScenarioPhase(96, "NRD · preparando", "Preparando instalação…", "96%", 2600),
            XiaomiIslandScenarioPhase(100, "NRD · pronto", "Atualização pronta para instalar", "Concluído", 3500)
        )
    )

    suspend fun loadOrSeedDefault(): XiaomiIslandScenario {
        val ref = FirebaseFirestore.getInstance().collection(COLLECTION).document(DOCUMENT)
        val snapshot = runCatching { ref.get().await() }.getOrNull()

        if (snapshot != null && snapshot.exists()) {
            parse(snapshot.data)?.let { return it.copy(remote = true) }
        }

        if (isManager()) {
            runCatching {
                ref.set(toMap(defaultScenario)).await()
            }
        }

        return defaultScenario
    }

    private fun parse(data: Map<String, Any>?): XiaomiIslandScenario? {
        if (data == null) return null
        val rawPhases = data["phases"] as? List<*> ?: return null
        val phases = rawPhases.mapNotNull { raw ->
            val map = raw as? Map<*, *> ?: return@mapNotNull null
            val progress = (map["progress"] as? Number)?.toInt()?.coerceIn(0, 100) ?: return@mapNotNull null
            val title = map["title"]?.toString()?.trim().orEmpty()
            val content = map["content"]?.toString()?.trim().orEmpty()
            val detail = map["detail"]?.toString()?.trim().orEmpty()
            val waitMillis = ((map["waitMillis"] as? Number)?.toLong() ?: 2000L).coerceIn(500L, 15000L)
            if (title.isBlank() || content.isBlank()) return@mapNotNull null
            XiaomiIslandScenarioPhase(
                progress = progress,
                title = title,
                content = content,
                detail = detail.ifBlank { progress.toString() + "%" },
                waitMillis = waitMillis
            )
        }.sortedBy { it.progress }

        if (phases.isEmpty()) return null

        return XiaomiIslandScenario(
            name = data["name"]?.toString()?.trim().orEmpty().ifBlank { "Cenário remoto" },
            version = (data["version"] as? Number)?.toLong() ?: 1L,
            enabled = data["enabled"] as? Boolean ?: true,
            phases = phases,
            remote = true
        )
    }

    private fun toMap(scenario: XiaomiIslandScenario): Map<String, Any> = mapOf(
        "name" to scenario.name,
        "version" to scenario.version,
        "enabled" to scenario.enabled,
        "phases" to scenario.phases.map { phase ->
            mapOf(
                "progress" to phase.progress,
                "title" to phase.title,
                "content" to phase.content,
                "detail" to phase.detail,
                "waitMillis" to phase.waitMillis
            )
        }
    )

    private fun isManager(): Boolean {
        val email = FirebaseAuth.getInstance().currentUser?.email?.trim()?.lowercase()
        return email == "mestre@nrdlojas.com" || email == "admin@nrdlojas.com"
    }
}
