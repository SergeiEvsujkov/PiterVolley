package ru.example.pitervolley.data

import android.content.Context

/**
 * Фильтры экранов, которые должны сохраняться между запусками приложения.
 * Раньше они жили только в rememberSaveable и сбрасывались после закрытия.
 */
object UiState {

    private const val PREFS = "ui_state"
    private const val KEY_DIV_GENDER = "div_gender"
    private const val KEY_DIV_YEAR = "div_year"
    private const val KEY_CAL_GENDER = "cal_gender"
    private const val KEY_CAL_DIVISION = "cal_division"
    private const val KEY_CAL_MONTH = "cal_month"

    private lateinit var prefs: android.content.SharedPreferences

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    var divisionsGender: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_DIV_GENDER, "all") ?: "all" else "all"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString(KEY_DIV_GENDER, value).apply()
        }

    var divisionsYear: Int
        get() = if (::prefs.isInitialized) prefs.getInt(KEY_DIV_YEAR, 0) else 0
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putInt(KEY_DIV_YEAR, value).apply()
        }

    var calendarGender: String
        get() = if (::prefs.isInitialized) prefs.getString(KEY_CAL_GENDER, "all") ?: "all" else "all"
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putString(KEY_CAL_GENDER, value).apply()
        }

    var calendarDivision: Int
        get() = if (::prefs.isInitialized) prefs.getInt(KEY_CAL_DIVISION, 0) else 0
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putInt(KEY_CAL_DIVISION, value).apply()
        }

    var calendarMonthMode: Boolean
        get() = if (::prefs.isInitialized) prefs.getBoolean(KEY_CAL_MONTH, false) else false
        set(value) {
            if (::prefs.isInitialized) prefs.edit().putBoolean(KEY_CAL_MONTH, value).apply()
        }
}
