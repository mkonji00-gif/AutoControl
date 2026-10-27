package com.example.autocontrol

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

/** UI: explicit user-controlled capture + offline screenshot test. */
class MainActivity : Activity() {
    companion object {
        private const val REQUEST_CAPTURE = 701
        private const val REQUEST_IMAGE = 702
        private const val REQUEST_NOTIFICATION = 703
    }
    private lateinit var currentState: TextView
    private lateinit var history: TextView
    private lateinit var testResult: TextView
    private var scanner: TextScanner? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(buildUi())
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATION)
        }
    }

    private fun text(value: String, sp: Float = 15f, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = sp
        setTextColor(Color.rgb(36, 45, 59))
        setPadding(0, 12, 0, 12)
        if (bold) setTypeface(null, android.graphics.Typeface.BOLD)
    }

    private fun button(value: String, action: () -> Unit): Button = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
    }

    private fun buildUi(): ScrollView {
        val outer = ScrollView(this).apply { setBackgroundColor(Color.rgb(246, 248, 251)) }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 40, 32, 40)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        outer.addView(column, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(text("AutoControl 0.2", 25f, true))
        column.addView(text("광고 화면 상태 인식 · 사용자 조작 보조", 14f))
        column.addView(text("이 앱은 화면 문구를 분석하지만 광고 버튼을 자동으로 누르거나 광고를 재실행하지 않습니다."))
        column.addView(button("화면 분석 시작 (캡처 동의)") { askForCapture() })
        column.addView(button("화면 분석 중지") { stopMonitor() })
        column.addView(button("사진 1장 불러와 인식 테스트") { chooseImage() })
        column.addView(button("상태 새로고침") { refresh() })

        column.addView(text("현재 인식 상태", 18f, true))
        currentState = text("대기 중")
        column.addView(currentState)
        column.addView(text("최근 상태 변경 기록", 18f, true))
        history = text("기록 없음", 13f)
        column.addView(history)
        column.addView(text("사진 분석 결과", 18f, true))
        testResult = text("사진을 선택하면 Ad N of M / Reward granted / 설치 안내 문구를 분석합니다.", 13f)
        column.addView(testResult)
        column.addView(text("참고: 닫기(X)와 ≫, ▶| 아이콘은 OCR만으로 신뢰성 있게 판별하기 어렵습니다. 필요한 화면 조작은 직접 수행하세요.", 13f))
        column.addView(button("화면 캡처 관련 시스템 설정") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.parse("package:$packageName")
            })
        })
        return outer
    }

    private fun askForCapture() {
        if (MonitorStore.isRunning(this)) {
            Toast.makeText(this, "먼저 분석을 중지하세요", Toast.LENGTH_SHORT).show()
            return
        }
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    private fun stopMonitor() {
        startService(Intent(this, CaptureMonitorService::class.java).setAction(CaptureMonitorService.ACTION_STOP))
        currentState.text = "중지 요청됨"
    }

    private fun chooseImage() {
        val select = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(select, REQUEST_IMAGE)
    }

    @Deprecated("Legacy callback retained so this project works without AndroidX dependencies")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQUEST_CAPTURE -> {
                val start = Intent(this, CaptureMonitorService::class.java).apply {
                    action = CaptureMonitorService.ACTION_START
                    putExtra(CaptureMonitorService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureMonitorService.EXTRA_RESULT_DATA, data)
                }
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(start) else startService(start)
                currentState.text = "화면 분석 시작 중 · 알림 패널에서 상태를 확인할 수 있습니다"
            }
            REQUEST_IMAGE -> {
                val uri = data.data ?: return
                try {
                    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                    val maxDim = maxOf(opts.outWidth, opts.outHeight)
                    val factor = if (maxDim > 2000) 2 else 1
                    val options = BitmapFactory.Options().apply { inSampleSize = factor }
                    val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                        ?: throw IllegalArgumentException("지원되지 않는 이미지입니다")
                    testResult.text = "이미지에서 글자를 인식하는 중..."
                    val localScanner = scanner ?: TextScanner().also { scanner = it }
                    localScanner.scan(bmp) { recognized ->
                        val state = ScreenStateClassifier.detect(recognized)
                        testResult.text = "${state.headline}\n${state.detail}\n\n[인식된 글자]\n${recognized.take(1200).ifBlank { "인식된 문구 없음" }}"
                        bmp.recycle()
                    }
                } catch (e: Exception) {
                    testResult.text = "사진 분석 실패: ${e.message}"
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::currentState.isInitialized) refresh()
    }

    private fun refresh() {
        val running = if (MonitorStore.isRunning(this)) "실행 중" else "중지 상태"
        currentState.text = "[$running]\n${MonitorStore.status(this)}"
        history.text = MonitorStore.history(this)
    }

    override fun onDestroy() {
        scanner?.close()
        scanner = null
        super.onDestroy()
    }
}
