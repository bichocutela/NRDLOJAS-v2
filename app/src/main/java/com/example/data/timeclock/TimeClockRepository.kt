package com.example.data.timeclock

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/** Personal control stays on the device and is separated between signed-in accounts. */
class TimeClockRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = context.getSharedPreferences("personal_time_clock", Context.MODE_PRIVATE)
    private fun key() = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid ?: "guest"
    private fun data() = runCatching { JSONObject(prefs.getString(key(), "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun hours(): WorkHours = data().optJSONObject("hours")?.let { WorkHours(it.optInt("start", 825), it.optInt("end", 1360), it.optInt("break", 70)) } ?: WorkHours()
    fun closingDay(): Int = data().optInt("closingDay", 24).coerceIn(0, 31)
    fun saveClosingDay(day: Int) { require(day in 0..31); write(data().put("closingDay", day)) }
    fun saveHours(hours: WorkHours) {
        hours.validate()
        write(data().put("hours", JSONObject().put("start", hours.start).put("end", hours.end).put("break", hours.breakMinutes)))
    }
    fun days(): List<Workday> {
        val rows = data().optJSONObject("days") ?: return emptyList()
        return rows.keys().asSequence().mapNotNull { date -> runCatching {
            val row = rows.getJSONObject(date); val punches = row.getJSONArray("punches")
            Workday(date, (0..3).map { if (punches.isNull(it)) null else punches.getInt(it) }, row.getInt("expected"),
                row.optBoolean("dayOff"), row.optBoolean("absent"), row.optString("note"), row.optBoolean("edited"), row.optInt("scheduledStart", 825), row.optInt("scheduledBreak", 70),
                row.optJSONArray("extras")?.let { values -> (0 until values.length()).map { values.getInt(it) } } ?: emptyList(), row.optBoolean("holiday"))
                .also(TimeClock::validate)
        }.getOrNull() }.toList()
    }
    fun save(day: Workday) {
        TimeClock.validate(day)
        val root = data(); val rows = root.optJSONObject("days") ?: JSONObject()
        rows.put(day.date, JSONObject().put("punches", JSONArray().apply { day.punches.forEach { put(it ?: JSONObject.NULL) } })
            .put("modifiedAt", System.currentTimeMillis()).put("expected", day.expectedMinutes).put("dayOff", day.dayOff).put("absent", day.absent).put("note", day.note).put("edited", day.edited).put("scheduledStart", day.scheduledStart).put("scheduledBreak", day.scheduledBreak).put("extras", JSONArray(day.extras)).put("holiday", day.holiday))
        write(root.put("days", rows))
    }
    fun punch(date: String, index: Int, minute: Int, dayOff: Boolean): Workday {
        val day = days().firstOrNull { it.date == date } ?: Workday(date, expectedMinutes = hours().expectedMinutes, dayOff = dayOff, scheduledStart = hours().start, scheduledBreak = hours().breakMinutes)
        require(!day.absent && day.punches.indexOfFirst { it == null } == index) { "Essa batida já foi registrada. Use Corrigir dia para alterar." }
        require(minute >= (day.punches.filterNotNull().lastOrNull() ?: 0)) { "O horário é anterior à última batida." }
        return day.copy(dayOff = day.dayOff || dayOff, punches = day.punches.toMutableList().apply { this[index] = minute }).also { save(it) }
    }
    private fun write(root: JSONObject) {
        val account = key()
        root.put("updatedAt", System.currentTimeMillis()).put("pending", true)
        check(prefs.edit().putString(account, root.toString()).commit()) { "Não foi possível salvar no aparelho." }
        com.example.util.PersonalClockWorker.schedule(appContext)
    }
    fun lunchAt() = data().optInt("lunchAt", 1020)
    fun saveLunchAt(minute: Int) { require(minute in 0..1439); write(data().put("lunchAt", minute)) }
    fun reminders() = data().optBoolean("reminders")
    fun setReminders(enabled: Boolean) { write(data().put("reminders", enabled)) }
    fun templates(): Map<String, WorkHours> {
        val rows = data().optJSONObject("templates") ?: return emptyMap()
        return rows.keys().asSequence().associateWith { name -> rows.getJSONObject(name).let { WorkHours(it.getInt("start"), it.getInt("end"), it.getInt("break")) } }
    }
    fun saveTemplate(name: String, hours: WorkHours) {
        hours.validate(); require(name.trim().length in 1..40)
        val root = data(); val rows = root.optJSONObject("templates") ?: JSONObject()
        require(rows.has(name.trim()) || rows.length() < 20) { "Limite de 20 jornadas." }
        rows.put(name.trim(), JSONObject().put("start", hours.start).put("end", hours.end).put("break", hours.breakMinutes))
        write(root.put("templates", rows))
    }
    fun applyTemplate(date: String, hours: WorkHours) {
        hours.validate()
        val previous = days().firstOrNull { it.date == date } ?: Workday(date)
        save(previous.copy(expectedMinutes = hours.expectedMinutes, scheduledStart = hours.start, scheduledBreak = hours.breakMinutes))
    }
    fun adjustments(): List<ClockAdjustment> {
        val rows = data().optJSONArray("adjustments") ?: return emptyList()
        return (0 until rows.length()).mapNotNull { index -> runCatching { rows.getJSONObject(index).let { ClockAdjustment(it.getString("id"), it.getString("date"), it.getInt("minutes"), it.getString("reason")) } }.getOrNull() }
    }
    fun addAdjustment(value: ClockAdjustment) {
        value.validate()
        val root = data(); val rows = root.optJSONArray("adjustments") ?: JSONArray()
        rows.put(JSONObject().put("id", value.id).put("date", value.date).put("minutes", value.minutes).put("reason", value.reason))
        write(root.put("adjustments", rows))
    }
    fun removeAdjustment(id: String) {
        val root = data(); val removed = root.optJSONObject("removedAdjustments") ?: JSONObject(); removed.put(id, System.currentTimeMillis()); root.put("removedAdjustments", removed)
        write(root.put("adjustments", JSONArray(adjustments().filter { it.id != id }.map { JSONObject().put("id", it.id).put("date", it.date).put("minutes", it.minutes).put("reason", it.reason) })))
    }
    fun backupStatus(): String = if (key() == "guest") "Salvo no aparelho · entre na conta para backup" else if (data().optBoolean("pending")) "Salvo no aparelho · backup pendente" else "Salvo no aparelho e na conta"
    /** Merge by record, so an offline edit does not replace unrelated dates from another device. */
    suspend fun sync() {
        val uid = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid ?: return
        val local = data()
        val ref = com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("personal_time_clock").document(uid)
        val merged = ref.firestore.runTransaction { transaction ->
            val remote = transaction.get(ref).getString("payload")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
            val merged = if (local.optLong("updatedAt") >= remote.optLong("updatedAt")) JSONObject(local.toString()) else JSONObject(remote.toString())
            val dayRows = JSONObject()
            listOf(remote, local).sortedBy { it.optLong("updatedAt") }.forEach { source -> source.optJSONObject("days")?.let { rows -> rows.keys().forEach { date ->
                val old = dayRows.optJSONObject(date); val incoming = rows.getJSONObject(date)
                if (old == null || incoming.optLong("modifiedAt", source.optLong("updatedAt")) >= old.optLong("modifiedAt")) dayRows.put(date, incoming)
            } } }
            val removed = JSONObject(); val entries = linkedMapOf<String, JSONObject>()
            listOf(remote, local).forEach { source ->
                source.optJSONObject("removedAdjustments")?.let { rows -> rows.keys().forEach { id -> removed.put(id, rows.getLong(id)) } }
                source.optJSONArray("adjustments")?.let { rows -> (0 until rows.length()).forEach { i -> val entry = rows.getJSONObject(i); entries[entry.getString("id")] = entry } }
            }
            merged.put("days", dayRows).put("removedAdjustments", removed).put("adjustments", JSONArray(entries.filterKeys { !removed.has(it) }.values.toList())).put("pending", false)
            transaction.set(ref, mapOf("payload" to merged.toString(), "updatedAt" to merged.optLong("updatedAt")))
            merged
        }.await()
        val stamp = local.optLong("updatedAt")
        if (key() == uid && data().optLong("updatedAt") == stamp) prefs.edit().putString(uid, merged.toString()).commit()
    }
    fun observeChanges(onChange: () -> Unit): () -> Unit {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> onChange() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
