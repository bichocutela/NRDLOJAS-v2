package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import org.json.JSONArray
import org.json.JSONObject

/** Device-first manual dates, isolated by account. Firestore queues writes offline. */
class ManualDaysOffRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences("manual_days_off", Context.MODE_PRIVATE)
    private fun account() = FirebaseAuth.getInstance().currentUser?.takeUnless { it.isAnonymous }?.uid
    fun hasAccount() = account() != null
    private fun key() = account() ?: "guest"
    private fun records(): JSONObject = runCatching { JSONObject(prefs.getString(key(), "{}") ?: "{}") }.getOrDefault(JSONObject())
    fun schedules(): List<WorkSchedule> {
        val data = records()
        return data.keys().asSequence().mapNotNull { monthKey ->
            val period = monthKey.split("-").mapNotNull(String::toIntOrNull)
            val row = data.optJSONObject(monthKey) ?: return@mapNotNull null
            if (period.size != 2) return@mapNotNull null
            val values = row.optJSONArray("days") ?: JSONArray()
            WorkSchedule(monthKey, period[0], period[1], listOf(WorkScheduleEmployee(
                registration = "1", name = "", daysOff = (0 until values.length()).map { values.optInt(it) }.distinct().sorted()
            )), row.optLong("updatedAt"))
        }.sortedByDescending { it.monthKey }.toList()
    }
    fun save(year: Int, month: Int, days: List<Int>) {
        require(year in 2000..2100 && month in 1..12)
        val max = java.util.GregorianCalendar(year, month - 1, 1).getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        require(days.all { it in 1..max })
        val monthKey = "%04d-%02d".format(year, month)
        val timestamp = System.currentTimeMillis()
        val data = records().put(monthKey, JSONObject().put("days", JSONArray(days.distinct().sorted())).put("updatedAt", timestamp).put("pending", true))
        check(prefs.edit().putString(key(), data.toString()).commit())
        cacheReminders()
        account()?.let { uid -> upload(uid, monthKey, data.getJSONObject(monthKey)) }
    }
    private fun upload(uid: String, monthKey: String, row: JSONObject) {
        val days = row.getJSONArray("days")
        FirebaseFirestore.getInstance().collection("manual_days_off").document(uid).collection("months").document(monthKey)
            .set(mapOf("days" to (0 until days.length()).map { days.getInt(it) }, "updatedAt" to row.getLong("updatedAt")))
            .addOnSuccessListener {
                val current = runCatching { JSONObject(prefs.getString(uid, "{}") ?: "{}") }.getOrDefault(JSONObject())
                if (current.optJSONObject(monthKey)?.optLong("updatedAt") == row.getLong("updatedAt")) {
                    current.getJSONObject(monthKey).put("pending", false)
                    prefs.edit().putString(uid, current.toString()).apply()
                }
            }
    }
    private fun cacheReminders() {
        com.example.util.ScheduleReminderWorker.cacheSchedules(context, schedules(), "1")
        com.example.util.PersonalClockWorker.schedule(context)
    }
    fun observe() = callbackFlow<List<WorkSchedule>> {
        var remote: com.google.firebase.firestore.ListenerRegistration? = null
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(schedules()); cacheReminders() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        val auth = FirebaseAuth.getInstance()
        val authListener = FirebaseAuth.AuthStateListener {
            remote?.remove()
            val uid = account()
            if (uid != null) {
                val local = records()
                local.keys().forEach { monthKey -> val row = local.getJSONObject(monthKey); if (row.optBoolean("pending")) upload(uid, monthKey, row) }
                remote = FirebaseFirestore.getInstance().collection("manual_days_off").document(uid).collection("months")
                    .addSnapshotListener { snapshot, error ->
                        if (error == null && snapshot != null && account() == uid) {
                            val merged = records()
                            snapshot.documents.forEach { doc ->
                                val timestamp = doc.getLong("updatedAt") ?: 0L
                                val previous = merged.optJSONObject(doc.id)
                                if (previous == null || (!previous.optBoolean("pending") && timestamp >= previous.optLong("updatedAt"))) {
                                    merged.put(doc.id, JSONObject().put("days", JSONArray(doc.get("days") as? List<*> ?: emptyList<Int>())).put("updatedAt", timestamp))
                                }
                            }
                            prefs.edit().putString(uid, merged.toString()).apply()
                        }
                    }
            }
            trySend(schedules())
            cacheReminders()
        }
        auth.addAuthStateListener(authListener)
        awaitClose { auth.removeAuthStateListener(authListener); remote?.remove(); prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
