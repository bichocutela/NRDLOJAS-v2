package com.example.util

/** Avoids reanimating system surfaces for every download poll. Completion stays immediate. */
object XiaomiProgressPolicy {
    fun shouldPost(previous: Int, next: Int, elapsedMillis: Long): Boolean {
        if (previous == next) return false
        if (previous < 0 || next == 100) return true
        return elapsedMillis >= 1500 && (next - previous >= 2 || elapsedMillis >= 5000)
    }
}
