package com.example.autocontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.DisplayMetrics
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Read-only screen-state monitor. Does not send gestures, close ads,
 * press next/skip, or start another rewarded ad.
 * Captured images are processed in memory only.
 */
class CaptureMonitorService : Service() {
    companion object {
        const val ACTION_START = "com.example.autocontrol.START"
        const val ACTION_STOP = "com.example.autocontrol.STOP"
        const val EXTRA_RESULT_CODE = "capture_result_code"
        const val EXTRA_RESULT_DATA = "capture_result_data"
        private const val CHANNEL_ID = "autocontrol_monitor"
        private const val NOTIFICATION_ID = 1102
        private const val MIN_INTERVAL_MS = 2300L
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var scanner: TextScanner? = null
    private val processing = AtomicBoolean(false)
    private var lastFrameAt = 0L
    private var lastStateId = ""
    private var active = false
    private var closing = false
    private var callback: MediaProjection.Callback? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val mgr = getSystemService(NotificationManager::class.java)
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "광고 화면 상태 알림", NotificationManager.IMPORTANCE_LOW).apply {
                description = "화면의 광고 단계 및 보상 문구를 인식한 상태를 표시합니다"
            }
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopSelf(); return START_NOT_STICKY }
            ACTION_START -> {
                if (active) return START_NOT_STICKY
                val code = intent.getIntExtra(EXTRA_RESULT_CODE, android.app.Activity.RESULT_CANCELED)
                val data = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    (intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent)
                }
                if (code != android.app.Activity.RESULT_OK || data == null) {
                    stopSelf(); return START_NOT_STICKY
                }
                showForeground("화면 인식 시작 중")
                try {
                    startCapture(code, data)
                } catch (e: Exception) {
                    MonitorStore.update(this, ScreenState(ScreenState.Kind.UNKNOWN, "화면 캡처 실패", e.message ?: "권한과 Android 버전을 확인하세요."))
                    stopSelf()
                }
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun showForeground(message: String) {
        val n = createNotification(message)
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun createNotification(message: String): Notification {
        val launch = Intent(this, MainActivity::class.java)
        val action = PendingIntent.getActivity(this, 1, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stopIntent = Intent(this, CaptureMonitorService::class.java).setAction(ACTION_STOP)
        val stop = PendingIntent.getService(this, 2, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("AutoControl 화면 모니터")
            .setContentText(message)
            .setContentIntent(action)
            .addAction(Notification.Action.Builder(null, "중지", stop).build())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        val manager = getSystemService(MediaProjectionManager::class.java)
        val capture = manager.getMediaProjection(resultCode, data)
            ?: throw IllegalStateException("MediaProjection 취득 실패")
        projection = capture

        val handlerThread = HandlerThread("AutoControlCapture")
        handlerThread.start()
        workerThread = handlerThread
        val handler = Handler(handlerThread.looper)
        workerHandler = handler
        scanner = TextScanner()

        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.getRealMetrics(metrics)
        val w = metrics.widthPixels
        val h = metrics.heightPixels

        val cb = object : MediaProjection.Callback() {
            override fun onStop() { Handler(Looper.getMainLooper()).post { stopSelf() } }
        }
        callback = cb
        capture.registerCallback(cb, Handler(Looper.getMainLooper()))
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        imageReader = reader
        reader.setOnImageAvailableListener({ r ->
            val image = try { r.acquireLatestImage() } catch (_: Exception) { null }
            if (image != null) {
                try {
                    val now = SystemClock.elapsedRealtime()
                    if (active && now - lastFrameAt >= MIN_INTERVAL_MS && processing.compareAndSet(false, true)) {
                        lastFrameAt = now
                        val plane = image.planes.first()
                        val pxStride = plane.pixelStride
                        if (pxStride != 4) {
                            processing.set(false)
                        } else {
                            val rowStride = plane.rowStride
                            val bytesPerRow = w * 4
                            val pixels = ByteArray(bytesPerRow * h)
                            val data = plane.buffer
                            for (row in 0 until h) {
                                data.position(row * rowStride)
                                data.get(pixels, row * bytesPerRow, bytesPerRow)
                            }
                            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixels))
                            val recognizer = scanner
                            if (recognizer == null) {
                                bitmap.recycle()
                                processing.set(false)
                            } else {
                                recognizer.scan(bitmap) { text ->
                                    try {
                                        if (active) handleText(text)
                                    } finally {
                                        bitmap.recycle()
                                        processing.set(false)
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    processing.set(false)
                } finally {
                    image.close()
                }
            }
        }, handler)

        active = true
        MonitorStore.setRunning(this, true)
        virtualDisplay = capture.createVirtualDisplay(
            "AutoControlScreen", w, h, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface,
            null, handler
        )
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification("모니터 실행 중 · 약 2~3초 간격"))
    }

    private fun handleText(text: String) {
        val state = ScreenStateClassifier.detect(text)
        if (state.id == lastStateId) return
        lastStateId = state.id
        MonitorStore.update(this, state)
        // Notification is informational only. It cannot tap an advertising UI.
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, createNotification(state.headline))
    }

    override fun onDestroy() {
        active = false
        MonitorStore.setRunning(this, false)
        if (!closing) {
            closing = true
            imageReader?.setOnImageAvailableListener(null, null)
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            val projectionToStop = projection
            val cb = callback
            if (projectionToStop != null && cb != null) projectionToStop.unregisterCallback(cb)
            projectionToStop?.stop()
            projection = null
            callback = null
            workerThread?.quitSafely()
            workerThread = null
            workerHandler = null
            scanner?.close()
            scanner = null
        }
        super.onDestroy()
    }
}
