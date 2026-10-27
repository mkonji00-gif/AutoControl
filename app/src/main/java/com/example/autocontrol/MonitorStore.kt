package com.example.autocontrol

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object MonitorStore {
    private const val PREF = "monitor_store"
    private const val STATUS = "status"
    private const val HISTORY = "history"
    private const val RUNNING = "running"

    fun setRunning(context: Context, running: Boolean) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean(RUNNING, running).apply()
    }
    fun isRunning(context: Context): Boolean = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(RUNNING, false)
    fun status(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(STATUS, "대기 중") ?: "대기 중"
    fun history(context: Context): String = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(HISTORY, "이력 없음") ?: "이력 없음"

    fun update(context: Context, state: ScreenState) {
        val prefs = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val now = SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(Date())
        val message = "[$now] ${state.headline}"
        val entries = prefs.getString(HISTORY, "")!!.lines().filter { it.isNotBlank() }
        prefs.edit()
            .putString(STATUS, "${state.headline}\n${state.detail}")
            .putString(HISTORY, (listOf(message) + entries).take(25).joinToString("\n"))
            .apply()
    }
}
