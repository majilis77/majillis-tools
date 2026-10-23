package com.sandbox.autoclick

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.concurrent.ConcurrentHashMap

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_RULES = "rules"
        const val ACTION_STOP = "com.sandbox.autoclick.STOP_CAPTURE"
        private const val CHANNEL_ID = "sandbox_capture"
        private const val NOTIFICATION_ID = 41
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var workerThread: HandlerThread? = null
    private var worker: Handler? = null
    private var screenWidth = 0
    private var screenHeight = 0
    private var density = 0
    private var rules: List<CommandRule> = emptyList()
    private val lastTriggered = ConcurrentHashMap<Int, Long>()
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopCapture(); return START_NOT_STICKY }
        createNotificationChannel()
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Sandbox AutoClicker")
            .setContentText("Captura de pantalla y reglas activas")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        if (projection == null) {
            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val data = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                else @Suppress("DEPRECATION") (intent?.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
            if (resultCode == 0 || data == null) { stopCapture(); return START_NOT_STICKY }
            rules = try {
                RuleInterpreter().parseAll(intent?.getStringExtra(EXTRA_RULES).orEmpty())
            } catch (_: Exception) {
                stopCapture(); return START_NOT_STICKY
            }
            startProjection(resultCode, data)
        }
        return START_NOT_STICKY
    }

    private fun startProjection(resultCode: Int, data: Intent) {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        screenWidth = metrics.widthPixels
        screenHeight = metrics.heightPixels
        density = metrics.densityDpi
        workerThread = HandlerThread("SandboxCapture").also { it.start() }
        worker = Handler(workerThread!!.looper)
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(resultCode, data).also { p ->
            p.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopCapture() }
            }, worker)
        }
        reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 3)
        virtualDisplay = projection?.createVirtualDisplay(
            "SandboxAutoClickerCapture", screenWidth, screenHeight, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader?.surface, null, worker
        )
        running = true
        worker?.postDelayed(::checkRules, 300L)
    }

    private fun checkRules() {
        if (!running) return
        val now = System.currentTimeMillis()
        rules.forEachIndexed { index, rule ->
            val last = lastTriggered[index] ?: 0L
            if (now - last < rule.waitMs.coerceAtLeast(250L)) return@forEachIndexed
            val color = readPixel(rule.testX, rule.testY) ?: return@forEachIndexed
            val expected = android.graphics.Color.parseColor(rule.colorHex)
            if ((color and 0x00FFFFFF) == (expected and 0x00FFFFFF)) {
                lastTriggered[index] = now
                AutoClickService.instance?.performClick(rule.actionX, rule.actionY)
            }
        }
        worker?.postDelayed(::checkRules, 250L)
    }

    private fun readPixel(x: Int, y: Int): Int? {
        if (x !in 0 until screenWidth || y !in 0 until screenHeight) return null
        var image: Image? = null
        return try {
            image = reader?.acquireLatestImage() ?: return null
            val plane = image.planes[0]
            val buffer = plane.buffer
            val offset = y * plane.rowStride + x * plane.pixelStride
            if (offset + 3 >= buffer.limit()) return null
            val r = buffer.get(offset).toInt() and 0xff
            val g = buffer.get(offset + 1).toInt() and 0xff
            val b = buffer.get(offset + 2).toInt() and 0xff
            android.graphics.Color.rgb(r, g, b)
        } catch (_: Exception) { null }
        finally { image?.close() }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Captura de pantalla", NotificationManager.IMPORTANCE_LOW))
        }
    }

    private fun stopCapture() {
        running = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        virtualDisplay = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        workerThread?.quitSafely()
        workerThread = null
        worker = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running = false
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        workerThread?.quitSafely()
        super.onDestroy()
    }
}
