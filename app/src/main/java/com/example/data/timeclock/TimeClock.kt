package com.example.data.timeclock

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale

/** All balances use integer minutes. Every day keeps its own expected workload. */
data class Workday(val date: String, val punches: List<Int?> = List(4) { null },
    val expectedMinutes: Int = 465, val dayOff: Boolean = false, val absent: Boolean = false,
    val note: String = "", val edited: Boolean = false,
    val scheduledStart: Int = 825, val scheduledBreak: Int = 70, val extras: List<Int> = emptyList(), val holiday: Boolean = false) {
    val complete get() = punches.size == 4 && punches.all { it != null }
    val actualWorked: Int? get() = if (complete) punches[1]!! - punches[0]!! + punches[3]!! - punches[2]!! else if (absent) 0 else null
    private fun overlap(start: Int, end: Int): Int = (minOf(end, scheduledStart + expectedMinutes + scheduledBreak) - maxOf(start, scheduledStart)).coerceAtLeast(0)
    val regularWorked: Int? get() = if (complete) {
        if (dayOff || holiday) actualWorked else overlap(punches[0]!!, punches[1]!!) + overlap(punches[2]!!, punches[3]!!)
    } else if (absent) 0 else null
    val automaticExtraMinutes: Int get() = if (complete && !dayOff && !holiday) actualWorked!! - regularWorked!! else 0
    val extraMinutes get() = extras.chunked(2).sumOf { it[1] - it[0] }
    val workedMinutes: Int? get() = actualWorked?.plus(extraMinutes)
    val intervalCreditMinutes: Int get() = if (complete && !dayOff && !holiday)
        minOf((scheduledBreak - (punches[2]!! - punches[1]!!)).coerceAtLeast(0), regularWorked!!) else 0
    val regularBalance: Int? get() = regularWorked?.minus(intervalCreditMinutes)?.minus(if (dayOff || holiday) 0 else expectedMinutes)
    val creditMinutes get() = (regularBalance ?: 0).coerceAtLeast(0) + if (complete) extraMinutes + automaticExtraMinutes + intervalCreditMinutes else 0
    val debitMinutes get() = -(regularBalance ?: 0).coerceAtMost(0)
    val balanceMinutes: Int? get() = regularBalance?.plus(extraMinutes + automaticExtraMinutes + intervalCreditMinutes)
    fun occurrences(): List<String> = buildList {
        if (dayOff) add("Folga")
        if (holiday) add("Feriado")
        if (absent) add("Ausência registrada")
        if (!complete && punches.any { it != null }) add("Marcação incompleta • saldo pendente")
        if (complete && !dayOff && !holiday) {
            if (punches[0]!! > scheduledStart) add("Entrada em atraso: ${TimeClock.duration(punches[0]!! - scheduledStart)}")
            val expectedEnd = scheduledStart + expectedMinutes + scheduledBreak
            if (punches[3]!! < expectedEnd) add("Saída antecipada: ${TimeClock.duration(expectedEnd - punches[3]!!)}")
            val interval = punches[2]!! - punches[1]!!
            if (interval != scheduledBreak) add("Intervalo ${if (interval < scheduledBreak) "menor" else "maior"}: ${TimeClock.duration(kotlin.math.abs(interval - scheduledBreak))}")
        }
        if (intervalCreditMinutes > 0) add("Crédito por intervalo menor: ${TimeClock.duration(intervalCreditMinutes)}")
        if (automaticExtraMinutes > 0) add("Trabalho fora da jornada: ${TimeClock.duration(automaticExtraMinutes)}")
        if (creditMinutes > 0) add("Crédito banco de horas: ${TimeClock.duration(creditMinutes)}")
        if (debitMinutes > 0) add("Débito banco de horas: ${TimeClock.duration(debitMinutes)}")
    }
}
data class WorkHours(val start: Int = 13 * 60 + 45, val end: Int = 22 * 60 + 40, val breakMinutes: Int = 70) {
    val expectedMinutes get() = ((end - start + 1440) % 1440) - breakMinutes
    fun validate() { require(start in 0..1439 && end in 0..1439 && breakMinutes >= 0 && expectedMinutes in 1..1440) { "O intervalo deve ser menor que a jornada." } }
}
data class MonthTotals(val worked: Int, val expected: Int, val credit: Int, val debit: Int, val pending: Int) {
    val balance get() = credit - debit
}
object TimeClock {
    val labels = listOf("Entrada", "Saída almoço", "Volta almoço", "Saída")
    fun parseTime(value: String): Int? {
        if (!Regex("\\d{2}:\\d{2}").matches(value.trim())) return null
        val parts = value.trim().split(':').map(String::toInt)
        return if (parts[0] in 0..23 && parts[1] in 0..59) parts[0] * 60 + parts[1] else null
    }
    fun time(minutes: Int?): String = minutes?.let { "%02d:%02d".format((it % 1440) / 60, it % 60) + if (it >= 1440) " +1d" else "" } ?: "—"
    fun duration(minutes: Int, signed: Boolean = false): String = (if (minutes < 0) "−" else if (signed && minutes > 0) "+" else "") + "%02d:%02d".format(kotlin.math.abs(minutes) / 60, kotlin.math.abs(minutes) % 60)
    fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(java.util.Date())
    fun calendar(date: String): Calendar {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
        require(Regex("\\d{4}-\\d{2}-\\d{2}").matches(date))
        return Calendar.getInstance().apply { time = requireNotNull(parser.parse(date)) }
    }
    fun displayDate(date: String): String = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(calendar(date).time)
    /** Only a contiguous prefix is valid; times may cross midnight once. */
    fun normalize(times: List<Int?>): List<Int?> {
        require(times.size == 4)
        var previous = -1; var offset = 0; var gap = false
        val result = times.map { time ->
            if (time == null) { gap = true; null } else {
                require(!gap && time in 0..1439) { "Preencha as batidas na ordem: entrada, almoço, volta e saída." }
                if (time + offset < previous) offset += 1440
                val value = time + offset
                require(offset <= 1440 && (previous < 0 || value >= previous)) { "Confira a ordem dos horários." }
                previous = value; value
            }
        }
        if (result[0] != null && result.filterNotNull().isNotEmpty()) require(result.filterNotNull().last() - result[0]!! <= 1440) { "A jornada não pode ultrapassar 24 horas." }
        return result
    }
    fun validate(day: Workday) {
        calendar(day.date)
        require(day.expectedMinutes in 0..1440 && day.note.length <= 500 && day.punches.size == 4)
        require(!day.absent || day.punches.all { it == null }) { "Uma ausência não pode ter batidas." }
        var previous = -1; var gap = false
        day.punches.forEach { minute ->
            if (minute == null) gap = true else { require(!gap && minute in 0..2879 && minute >= previous); previous = minute }
        }
        val values = day.punches.filterNotNull()
        require(values.isEmpty() || values.last() - values.first() <= 1440)
        require(day.scheduledStart in 0..1439 && day.scheduledBreak in 0..1439)
        require(day.extras.size in listOf(0, 2, 4) && (day.extras.isEmpty() || day.complete)) { "Conclua as quatro batidas antes de adicionar períodos extras." }
        val intervals = if (day.complete) listOf(day.punches[0]!! to day.punches[1]!!, day.punches[2]!! to day.punches[3]!!) + day.extras.chunked(2).map { it[0] to it[1] } else emptyList()
        intervals.forEach { require(it.first in 0..2879 && it.second in 0..2879 && it.second > it.first) { "Confira a entrada e saída de cada período." } }
        val ordered = intervals.sortedBy { it.first }
        require(ordered.zipWithNext().all { (a, b) -> a.second <= b.first }) { "Os períodos de trabalho não podem se sobrepor." }
        require(ordered.isEmpty() || ordered.last().second - ordered.first().first <= 1440) { "A jornada total não pode ultrapassar 24 horas." }
    }
    fun totals(days: List<Workday>): MonthTotals {
        val completed = days.filter { it.balanceMinutes != null }
        return MonthTotals(completed.sumOf { it.workedMinutes ?: 0 }, completed.sumOf { if (it.dayOff || it.holiday) 0 else it.expectedMinutes },
            completed.sumOf { it.creditMinutes }, completed.sumOf { it.debitMinutes },
            days.count { !it.absent && !it.complete && it.punches.any { p -> p != null } })
    }
    fun monthDates(month: String): List<String> {
        val cal = calendar("$month-01")
        return (1..cal.getActualMaximum(Calendar.DAY_OF_MONTH)).map { "$month-${it.toString().padStart(2, '0')}" }
    }
    fun periodDates(month: String, closingDay: Int = 24): List<String> {
        if (closingDay == 0) return monthDates(month)
        require(closingDay in 1..31)
        val start = calendar("${shiftMonth(month, -1)}-01")
        start.set(Calendar.DAY_OF_MONTH, closingDay.coerceAtMost(start.getActualMaximum(Calendar.DAY_OF_MONTH)))
        start.add(Calendar.DAY_OF_MONTH, 1)
        val end = calendar("$month-01").apply { set(Calendar.DAY_OF_MONTH, closingDay.coerceAtMost(getActualMaximum(Calendar.DAY_OF_MONTH))) }
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return buildList { while (!start.after(end)) { add(format.format(start.time)); start.add(Calendar.DAY_OF_MONTH, 1) } }
    }
    fun competence(date: String, closingDay: Int): String {
        val month = date.take(7)
        return if (closingDay == 0 || date in periodDates(month, closingDay)) month else shiftMonth(month, 1)
    }
    fun shiftMonth(month: String, delta: Int): String = calendar("$month-01").apply { add(Calendar.MONTH, delta) }.let { SimpleDateFormat("yyyy-MM", Locale.ROOT).format(it.time) }
}
