package net.utopies.vitacore.StepCounter

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.IBinder
import android.widget.Toast
import net.utopies.vitacore.R


import java.lang.ref.WeakReference
import kotlin.math.sqrt


class StepCounterService : Service(), SensorEventListener {
    @Volatile
    public var countStep = 0
        private set(value){
            field = value
            listeners.forEach { ref ->
                ref.get()?.invoke(countStep)
            }
            listeners.removeAll { ref -> ref.get() == null }
        }

    private val listeners = mutableListOf<WeakReference<(Int) -> Unit>>()
    private lateinit var sensorManager: SensorManager
    private var stepDetectorSensor: Sensor? = null

    private var accelerationValue = 0f
    private var previousAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var isStep = false

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                previousAcceleration = currentAcceleration
                currentAcceleration = sqrt(x * x + y * y + z * z)

                val delta = currentAcceleration - previousAcceleration
                accelerationValue = accelerationValue * 0.9f + delta

                if (accelerationValue > 3.5 && !isStep) { // Пороговое значение
                    countStep++
                    isStep = true
                } else if (accelerationValue < 0) {
                    isStep = false
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepDetectorSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        if (stepDetectorSensor == null) {
            Toast.makeText(this, "Ваше устройство не поддерживается", Toast.LENGTH_LONG).show()
            stopSelf()
        }

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    @SuppressLint("ForegroundServiceType")
    private fun createNotificationChannel(){
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel("stepmeter") == null) {
            val channel = NotificationChannel(
                "stepmeter",
                "Шагомер",
                NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)

            val notification: Notification = Notification.Builder(this, "stepmeter")
                .setContentTitle("Сервис работает")
                .setSmallIcon(R.drawable.icon_meter_notification)
                .build()

            startForeground(1, notification)
        }
    }

    public fun registerListener(listener: (Int) -> Unit) {
        listeners.add(WeakReference(listener))
    }

    // Отмена регистрации
    public fun unregisterListener(listener: (Int) -> Unit) {
        listeners.removeAll { it.get() == listener }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder() // Возвращаем объект Binder
    }

    inner class LocalBinder : Binder() {
        fun getService(): StepCounterService = this@StepCounterService
    }
}