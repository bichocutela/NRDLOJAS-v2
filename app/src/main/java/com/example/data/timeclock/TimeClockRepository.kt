package com.example.data.timeclock

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

/** Personal control stays on the device and is separated between signed-in accounts. */
class TimeClockRepository(context: Context) {
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
            .put("expected", day.expectedMinutes).put("dayOff", day.dayOff).put("absent", day.absent).put("note", day.note).put("edited", day.edited).put("scheduledStart", day.scheduledStart).put("scheduledBreak", day.scheduledBreak).put("extras", JSONArray(day.extras)).put("holiday", day.holiday))
        write(root.put("days", rows))
    }
    fun punch(date: String, index: Int, minute: Int, dayOff: Boolean): Workday {
        val day = days().firstOrNull { it.date == date } ?: Workday(date, expectedMinutes = hours().expectedMinutes, dayOff = dayOff, scheduledStart = hours().start, scheduledBreak = hours().breakMinutes)
        require(!day.absent && day.punches.indexOfFirst { it == null } == index) { "Essa batida já foi registrada. Use Corrigir dia para alterar." }
        require(minute >= (day.punches.filterNotNull().lastOrNull() ?: 0)) { "O horário é anterior à última batida." }
        return day.copy(dayOff = day.dayOff || dayOff, punches = day.punches.toMutableList().apply { this[index] = minute }).also { save(it) }
    }
    private fun write(root: JSONObject) { check(prefs.edit().putString(key(), root.toString()).commit()) { "Não foi possível salvar no aparelho." } }
}
