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
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import net.utopies.vitacore.R
import java.lang.ref.WeakReference
import java.time.LocalDate
import kotlin.Exception
import kotlin.math.sqrt


class StepCounterService : Service(), SensorEventListener {

    @Volatile
    public var countStep = 0
        private set(value){
            field = value
            setNotificate()
            listeners.removeAll { ref -> ref.get() == null }
            listeners.forEach { ref ->
                ref.get()?.invoke(countStep)
            }
            stepCounterState.addSteps(LocalDate.now(), field)
        }

    private lateinit var stepCounterState: StepCounterState

    private val listeners = mutableListOf<WeakReference<(Int) -> Unit>>()
    private lateinit var notification: Notification
    private lateinit var notificationManager: NotificationManager

    private lateinit var sensorHandlerThread: HandlerThread
    private lateinit var sensorHandler: Handler

    private lateinit var sensorManager: SensorManager
    private var stepDetectorSensor: Sensor? = null
    private var accelerationValue = 0f
    private var previousAcceleration = SensorManager.GRAVITY_EARTH
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var isStep = false

    override fun onSensorChanged(event: SensorEvent?) {
        try {
            event?.let {
                sensorHandler.post {
                    try {

                        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                            val x = event.values[0]
                            val y = event.values[1]
                            val z = event.values[2]

                            previousAcceleration = currentAcceleration
                            currentAcceleration = sqrt(x * x + y * y + z * z)

                            val delta = currentAcceleration - previousAcceleration
                            accelerationValue = accelerationValue * 0.91f + delta

                            if (accelerationValue > 3.83 && !isStep) { // Пороговое значение
                                countStep++
                                isStep = true
                            } else if (accelerationValue < 0) {
                                isStep = false
                            }
                        }
                    }
                    catch (e: Exception){
                        Log.e("StepCounter", "Handler error", e)
                    }
                }
            }
        }
        catch (e: Exception) {
            Log.e("StepCounter", "Handler error", e)
        }
    }

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        sensorHandlerThread = HandlerThread("SensorThread").apply { start() }
        sensorHandler = Handler(sensorHandlerThread.looper)

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        stepDetectorSensor?.let {
            sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_NORMAL, sensorHandler)
        }

        if (stepDetectorSensor == null) {
            Toast.makeText(this, "Ваше устройство не поддерживается", Toast.LENGTH_LONG).show()
            stopSelf()
        }

        setNotificate()

        stepCounterState = StepCounterState(this)
        countStep = stepCounterState.getSteps(LocalDate.now())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (stepDetectorSensor == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        setNotificate()
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        sensorHandlerThread.quitSafely()
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    @SuppressLint("ForegroundServiceType")
    private fun setNotificate() {
        val channel = NotificationChannel(
            "stepmeter",
            "Шагомер",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Канал для уведомлений шагомера"
        }
        notificationManager.createNotificationChannel(channel)


        notification = Notification.Builder(this, "stepmeter")
            .setContentTitle("Вы сделали: ${countStep} шага")
            .setSmallIcon(R.drawable.icon_meter_notification)
            .build()

        startForeground(1, notification)
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