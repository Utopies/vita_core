package net.utopies.vitacore

import android.graphics.Color
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.annotation.SuppressLint
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import net.utopies.vitacore.R.layout.main_layout
import net.utopies.vitacore.StepCounter.StepCounterService
import net.utopies.vitacore.StepCounter.StepCounterState
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), View.OnClickListener {
    private var stepCounterService: StepCounterService? = null
    private var isBoundStepServer = false
    private var isWorkedStepServer = false
    private lateinit var btnStop: Button
    private lateinit var stepCounterState: StepCounterState

    @SuppressLint("SetTextI18n")
    private val updateSteps = { steps: Int ->
        runOnUiThread {
            findViewById<TextView>(R.id.tv_step_count).text = "Шаги: $steps"
            findViewById<TextView>(R.id.tv_distance).text =
                "Км: ${"%.2f".format(steps * 0.00060F)}"
            findViewById<TextView>(R.id.tv_calories).text =
                "Ккал: ${(steps * 0.00060F * 45).roundToInt()}"

            updateChart()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        stepCounterState = StepCounterState(this)

        setContentView(main_layout)
        buttonRegister()
        inintDailyStepChart()

        findViewById<TextView>(R.id.tv_step_count).text = "Шаги: [ нажмите Start]"
    }

    override fun onDestroy() {
        super.onDestroy()
        stepCounterService?.unregisterListener(updateSteps)
        if (isBoundStepServer) {
            unbindService(serviceConnection)
            isBoundStepServer = false
        }
    }

    override fun onClick(v: View?) {
        when(v?.id){
            R.id.btn_stop -> {
                if (isWorkedStepServer == true)
                    onStopStepService()
                else
                    onStartStepService()
            }
        }
    }

    fun getLast30Days(): List<Float> {
        val allDailyValues = ArrayList<Float>()
        val nowDate = LocalDate.now()

        for (n in 0..29) {
            val date = nowDate.minusDays(29 - n.toLong())
            allDailyValues.add(stepCounterState.getSteps(date).toFloat())
        }

        return allDailyValues // Убрали .reversed()
    }

    private fun inintDailyStepChart() {
        findViewById<BarChart>(R.id.dailyBarChart).apply {
            // Инициализация данных
            val allDailyValues = getLast30Days()

            // Создание данных для графика
            val barEntries = allDailyValues.mapIndexed { index, value ->
                BarEntry(index.toFloat(), value)
            }

            val color = 0xFFDB8707.toInt()

            val dailyDataSet = BarDataSet(barEntries, "Daily Data").apply {
                valueTextSize = 14f
                valueTextColor = 0xFFDB8707.toInt()
                //color(this@apply.color) // Сохраняем оригинальный цвет столбцов (если нужно изменить - заменить на color)
            }

            data = BarData(dailyDataSet).apply {
                barWidth = 0.9f
            }

            // Настройка внешнего вида
            setFitBars(true)
            configureXAxisLabels(this, allDailyValues)

            with(xAxis) {
                textSize = 14f
                textColor = color
            }

            with(axisLeft) {
                textSize = 14f
                textColor = color
            }

            axisRight.apply {
                isEnabled = false // Отключаем правую ось
            }

            legend.apply {
                textSize = 16f
                textColor = color
            }

            description.apply {
                textSize = 12f
                textColor = color
                text = " "
            }

            // Настройка отображения
            setVisibleXRangeMaximum(5f)
            moveViewToX(allDailyValues.size.toFloat())
            invalidate()
        }
    }

    private fun configureXAxisLabels(barChart: BarChart, dailyValues: List<Float>) {
        val color = Color.parseColor("#DB8707")

        barChart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            granularity = 1f
            setDrawGridLines(false)
            textColor = color

            // Генерация списка дат
            val dates = mutableListOf<String>()
            val today = LocalDate.now()
            val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")

            for (i in 0 until dailyValues.size) { // Исправлено на 0 until
                val date = today.minusDays((dailyValues.size - i - 1).toLong())
                dates.add(date.format(dateFormatter))
            }

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val index = value.toInt()
                    return dates.getOrNull(index) ?: ""
                }
            }
        }
    }

    private fun updateChart() {
        val chart = findViewById<BarChart>(R.id.dailyBarChart)
        val allDailyValues = getLast30Days()

        val newEntries = allDailyValues.mapIndexed { index, value ->
            BarEntry(index.toFloat(), value)
        }

        val newDataSet = BarDataSet(newEntries, "Daily Data").apply {
            valueTextSize = 14f
            valueTextColor = 0xFFDB8707.toInt()
        }

        chart.data = BarData(newDataSet).apply {
            barWidth = 0.9f
        }

        configureXAxisLabels(chart, allDailyValues)
        chart.invalidate()
    }

    private fun buttonRegister(){
        btnStop = findViewById(R.id.btn_stop)
        btnStop.setOnClickListener(this)
    }

    @SuppressLint("SetTextI18n")
    private fun onStopStepService(){
        if(isBoundStepServer == true)
            unbindService(serviceConnection)
        Intent(this, StepCounterService::class.java).also { intent ->
            stopService(intent)
        }
        btnStop.text = "Start"
        isWorkedStepServer = false
    }

    @SuppressLint("SetTextI18n")
    private fun onStartStepService() {
        val sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        if (sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) == null) {
            Toast.makeText(this, "Устройство не поддерживается", Toast.LENGTH_LONG).show()
            return
        }
        // Запуск сервиса, если сенсор доступен
        Intent(this, StepCounterService::class.java).also { intent ->
            startForegroundService(intent)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
        btnStop.text = "Stop"
        isWorkedStepServer = true
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StepCounterService.LocalBinder
            stepCounterService = binder.getService()
            stepCounterService?.registerListener(updateSteps)
            isBoundStepServer = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stepCounterService?.unregisterListener(updateSteps)
            stepCounterService = null
            isBoundStepServer = false
        }
    }

}