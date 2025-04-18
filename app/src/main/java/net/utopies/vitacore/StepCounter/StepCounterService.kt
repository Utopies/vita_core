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


class StepCounterService : Service(), SensorEventListener {
    @Volatile
    public var countStep = 0
        private set(value){
            field = value
        }

    private val listeners = mutableListOf<WeakReference<(Int) -> Unit>>()
    private lateinit var sensorManager: SensorManager
    private var stepDetectorSensor: Sensor? = null

    override fun onSensorChanged(event: SensorEvent?) {
        if (event != null){
            if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR){
                countStep += event.values[0].toInt()
                listeners.forEach { ref ->
                    ref.get()?.invoke(countStep)
                }
                listeners.removeAll { ref -> ref.get() == null }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
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