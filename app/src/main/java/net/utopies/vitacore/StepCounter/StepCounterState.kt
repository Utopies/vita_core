package net.utopies.vitacore.StepCounter

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StepCounterState(private val context: Context) {
    //формат даты
    val formatter = DateTimeFormatter.ofPattern("yy.MM.dd")

    // Ключ для SharedPreferences
    private val prefs: SharedPreferences =
        context.getSharedPreferences("step_data", Context.MODE_PRIVATE)

    // Добавить шаги за конкретную дату
    fun addSteps(date: LocalDate, steps: Int) {
        prefs.edit()
            .putInt(date.format(formatter).toString(), steps) // Сохраняем актуальное значение
            .apply()
    }

    //Получить шаги за день
    fun getSteps(date: LocalDate): Int {
        return prefs.getInt(date.format(formatter).toString(), 0)
    }

    //Получить всю историю шагов
    fun getAllSteps(): Map<String, Int> =
        prefs.all
            .filterKeys { it.matches(Regex("\\d{4}\\.\\d{2}\\.\\d{2}")) }// Фильтр по датам
            .mapValues { it.value as Int }
}