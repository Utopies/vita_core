package net.utopies.vitacore

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.Manifest
import android.annotation.SuppressLint
import android.os.Bundle
import android.os.IBinder
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import net.utopies.vitacore.R.layout.main_layout
import net.utopies.vitacore.StepCounter.StepCounterService

class MainActivity : ComponentActivity() {
    private var stepCounterService: StepCounterService? = null
    private var isBound = false
    private lateinit var tvStepCount: TextView
    private var lastUpdateTime: Long = 0
    private val updateInterval = 1000L // 1 секунда

    private val updateSteps = { steps: Int ->
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastUpdateTime >= updateInterval) {
            lastUpdateTime = currentTime
            runOnUiThread {
                tvStepCount.text = "Шаги: $steps"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Запустить и привязать сервис
        Intent(this, StepCounterService::class.java).also { intent ->
            startForegroundService(intent) // Для долгой работы в фоне
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onStart() {
        super.onStart()
        setContentView(main_layout)
        tvStepCount = findViewById(R.id.tv_step_count)
        updateSteps.invoke(stepCounterService?.countStep ?: 0)

        tvStepCount.text = "Шаги: ${stepCounterService?.countStep?: 0}"
    }

    override fun onDestroy() {
        super.onDestroy()
        stepCounterService?.unregisterListener(updateSteps)
        if (isBound) {
            unbindService(serviceConnection)
            isBound = false
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as StepCounterService.LocalBinder
            stepCounterService = binder.getService()
            stepCounterService?.registerListener(updateSteps)
            isBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            stepCounterService?.unregisterListener(updateSteps)
            stepCounterService = null
            isBound = false
        }
    }
}