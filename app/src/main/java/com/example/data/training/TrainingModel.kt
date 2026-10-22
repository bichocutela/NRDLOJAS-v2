package com.example.data.training

import org.json.JSONArray
import org.json.JSONObject

enum class TrainingVisibility { OFF, TEST, ALL }
enum class TrainingMode { LEARN, EXAM, FREE }
enum class KeyAction(val title: String) {
    MESSAGE("Mostrar mensagem / avançar"), APPEND("Acrescentar valor à entrada"),
    CLEAR("Limpar entrada"), BACK("Voltar uma etapa"), RESET("Reiniciar missão")
}
data class TrainingKey(val id: String, val label: String, val action: KeyAction = KeyAction.MESSAGE,
    val value: String = "", val operatorText: String = "", val customerText: String = "",
    val operatorImage: String = "", val customerImage: String = "", val overrideGuided: Boolean = false)
data class TrainingStep(val keyId: String, val operatorText: String, val customerText: String,
    val instruction: String, val operatorImage: String = "", val customerImage: String = "")
data class TrainingMission(val id: String, val title: String, val subtitle: String = "",
    val enabled: Boolean = true, val steps: List<TrainingStep>)
data class TrainingConfig(val visibility: TrainingVisibility = TrainingVisibility.OFF, val revision: Long = 0,
    val keys: List<TrainingKey> = emptyList(), val missions: List<TrainingMission> = emptyList(),
    val left: List<List<String>> = emptyList(), val right: List<List<String>> = emptyList(), val bottom: List<List<String>> = emptyList()) {
    fun visibleTo(master: Boolean) = visibility == TrainingVisibility.ALL || (master && visibility == TrainingVisibility.TEST)
    fun validate(): String? {
        if (keys.isEmpty() || keys.size > 100 || keys.map { it.id }.distinct().size != keys.size) return "Confira as teclas do teclado."
        if (keys.any { !it.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) || it.label.length > 160 }) return "Confira os nomes das teclas (máximo 160 caracteres)."
        if (missions.any { !it.id.matches(Regex("[A-Za-z0-9_-]{1,100}")) || it.title.length > 160 }) return "Confira os títulos (máximo 160 caracteres)."
        if (missions.size > 60 || missions.map { it.id }.distinct().size != missions.size) return "Confira os identificadores dos treinamentos."
        if (missions.isEmpty() || missions.any { it.title.isBlank() || it.steps.isEmpty() || it.steps.size > 100 }) return "Cada treinamento precisa de título e pelo menos uma etapa (máximo 100)."
        val ids = keys.map { it.id }.toSet()
        if (missions.any { m -> m.steps.any { it.keyId !in ids || keys.first { k -> k.id == it.keyId }.label.isBlank() } }) return "Há uma etapa usando uma tecla sem nome ou inexistente."
        if ((left + right + bottom).flatten().any { it !in ids }) return "O teclado contém uma posição inválida."
        val images = keys.flatMap { listOf(it.operatorImage, it.customerImage) } + missions.flatMap { m -> m.steps.flatMap { listOf(it.operatorImage, it.customerImage) } }
        if (images.any { it.isNotBlank() && !it.startsWith("https://") }) return "Use links HTTPS para as imagens."
        if (TrainingCodec.encode(this).toByteArray().size > 700_000) return "O treinamento ultrapassou o tamanho permitido."
        return null
    }
}
object TrainingCodec {
    private fun JSONArray.objects() = (0 until length()).map { getJSONObject(it) }
    fun decode(raw: String): TrainingConfig {
        val o = JSONObject(raw)
        fun rows(name: String) = o.getJSONArray(name).let { a -> (0 until a.length()).map { i ->
            a.getJSONArray(i).let { r -> (0 until r.length()).map { r.getString(it) } }
        } }
        return TrainingConfig(TrainingVisibility.valueOf(o.optString("visibility", "OFF")), o.optLong("revision"),
            o.getJSONArray("keys").objects().map { TrainingKey(it.getString("id"), it.getString("label"), KeyAction.valueOf(it.optString("action", "MESSAGE")),
                it.optString("value"), it.optString("operatorText"), it.optString("customerText"), it.optString("operatorImage"), it.optString("customerImage"), it.optBoolean("overrideGuided")) },
            o.getJSONArray("missions").objects().map { m -> TrainingMission(m.getString("id"), m.getString("title"), m.optString("subtitle"), m.optBoolean("enabled", true),
                m.getJSONArray("steps").objects().map { TrainingStep(it.getString("keyId"), it.optString("operatorText"), it.optString("customerText"), it.optString("instruction"), it.optString("operatorImage"), it.optString("customerImage")) }) },
            rows("left"), rows("right"), rows("bottom"))
    }
    fun encode(c: TrainingConfig): String = JSONObject().apply {
        put("visibility", c.visibility.name); put("revision", c.revision)
        put("left", JSONArray(c.left.map { JSONArray(it) })); put("right", JSONArray(c.right.map { JSONArray(it) })); put("bottom", JSONArray(c.bottom.map { JSONArray(it) }))
        put("keys", JSONArray(c.keys.map { k -> JSONObject().apply {
            put("id",k.id); put("label",k.label); put("action",k.action.name); put("value",k.value)
            put("operatorText",k.operatorText); put("customerText",k.customerText); put("operatorImage",k.operatorImage); put("customerImage",k.customerImage); put("overrideGuided",k.overrideGuided)
        } }))
        put("missions", JSONArray(c.missions.map { m -> JSONObject().apply {
            put("id",m.id); put("title",m.title); put("subtitle",m.subtitle); put("enabled",m.enabled)
            put("steps",JSONArray(m.steps.map { s -> JSONObject().apply {
                put("keyId",s.keyId); put("operatorText",s.operatorText); put("customerText",s.customerText)
                put("instruction",s.instruction); put("operatorImage",s.operatorImage); put("customerImage",s.customerImage)
            } }))
        } }))
    }.toString()
}

data class TrainingState(val missionId: String, val mode: TrainingMode = TrainingMode.LEARN,
    val step: Int = 0, val errors: Int = 0, val finished: Boolean = false, val input: String = "",
    val history: List<String> = emptyList(), val completed: Set<String> = emptySet(), val displayKey: String? = null)
object TrainingEngine {
    fun reset(s: TrainingState) = TrainingState(s.missionId, s.mode, completed = s.completed)
    fun press(config: TrainingConfig, state: TrainingState, id: String): TrainingState {
        val key = config.keys.find { it.id == id && it.label.isNotBlank() } ?: return state
        val mission = config.missions.find { it.id == state.missionId } ?: return state
        if (state.finished && state.mode != TrainingMode.FREE) return state
        val correct = state.mode == TrainingMode.FREE || mission.steps.getOrNull(state.step)?.keyId == id
        if (!correct) {
            val errors = state.errors + 1
            return if (state.mode == TrainingMode.EXAM) reset(state).copy(errors = errors)
            else state.copy(errors = errors, history = (listOf("✕ ${key.label}") + state.history).take(8))
        }
        if (key.action == KeyAction.RESET) return reset(state)
        var s = state.copy(history = (listOf("✓ ${key.label}") + state.history).take(8),
            displayKey = if (state.mode == TrainingMode.FREE || key.overrideGuided) id else null,
            input = when (key.action) { KeyAction.APPEND -> (state.input + key.value).take(120); KeyAction.CLEAR -> ""; KeyAction.BACK -> state.input.dropLast(1); else -> state.input })
        if (state.mode == TrainingMode.FREE) return s
        if (key.action == KeyAction.BACK) return s.copy(step = (state.step - 1).coerceAtLeast(0))
        return if (state.step >= mission.steps.lastIndex) s.copy(finished = true, completed = s.completed + mission.id)
        else s.copy(step = state.step + 1)
    }
    fun render(text: String, state: TrainingState, key: TrainingKey?, mission: TrainingMission) = text
        .replace("{entrada}", state.input).replace("{tecla}", key?.label?.replace('\n',' ').orEmpty()).replace("{missao}", mission.title)
}
