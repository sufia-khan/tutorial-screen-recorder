package com.screenrecorder.service

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.model.RecordingSession
import com.screenrecorder.model.RecordingState

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayContainer: FrameLayout? = null
    private var contentView: LinearLayout? = null
    private var collapsedContent: View? = null
    private var expandedContent: LinearLayout? = null
    private var collapsedTimer: TextView? = null
    private var expandedTimer: TextView? = null
    private var pauseIcon: ImageView? = null
    private var stopIcon: ImageView? = null
    private var collapseIcon: ImageView? = null
    private var expanded = false
    private var overlayParams: WindowManager.LayoutParams? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var autoCollapseTask: Runnable? = null
    private var timerTask: Runnable? = null

    companion object {
        private const val TAG = "FloatingOverlay"

        private const val ACTION_SHOW = "com.screenrecorder.overlay.SHOW"
        private const val ACTION_HIDE = "com.screenrecorder.overlay.HIDE"

        private const val COLLAPSED_W_DP = 56
        private const val COLLAPSED_H_DP = 36
        private const val EXPANDED_W_DP = 212
        private const val EXPANDED_H_DP = 52
        private const val ANIM_MS = 220L
        private const val SNAP_ANIM_MS = 160L
        private const val EDGE_MARGIN_DP = 4
        private const val AUTO_COLLAPSE_DELAY_MS = 2500L
        private const val BTN_SIZE_DP = 34
        private const val ICON_SIZE_DP = 18
        private const val COLLAPSED_TIMER_SP = 12f
        private const val EXPANDED_TIMER_SP = 15f

        private const val COLOR_BG = 0xDD1C1C1C.toInt()
        private const val COLOR_BTN_BG = 0x26FFFFFF.toInt()
        private const val COLOR_ICON = 0xF0FFFFFF.toInt()
        private const val COLOR_TIMER = 0xFFFFFFFF.toInt()
        private const val COLOR_ACCENT = 0xFFFFFFFF.toInt()
        private const val ICON_STROKE_DP = 2f

        fun show(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_SHOW
            })
        }

        fun hide(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_HIDE
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTimerTask()
        stopAutoCollapse()
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayContainer != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val d = resources.displayMetrics.density
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val colWPx = (COLLAPSED_W_DP * d).toInt()
        val colHPx = (COLLAPSED_H_DP * d).toInt()
        val btnSizePx = (BTN_SIZE_DP * d).toInt()
        val edgeMarginPx = (EDGE_MARGIN_DP * d).toInt()
        val iconSizePx = (ICON_SIZE_DP * d).toInt()
        val strokePx = ICON_STROKE_DP * d

        collapsedTimer = TextView(this).apply {
            text = "00:00"
            textSize = COLLAPSED_TIMER_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TIMER)
            gravity = Gravity.CENTER
        }
        collapsedContent = FrameLayout(this).apply {
            addView(collapsedTimer, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            setOnClickListener { if (!expanded) expand() }
        }

        expandedTimer = TextView(this).apply {
            text = "00:00"
            textSize = EXPANDED_TIMER_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TIMER)
            gravity = Gravity.CENTER
        }

        pauseIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
        val pauseBtn = createIconCircle(btnSizePx, pauseIcon!!).apply {
            setOnClickListener {
                val action = if (RecordingSession.state == RecordingState.RECORDING) {
                    ScreenRecorderService.pause(this@FloatingOverlayService)
                    RecordingState.PAUSED
                } else {
                    ScreenRecorderService.resume(this@FloatingOverlayService)
                    RecordingState.RECORDING
                }
                updatePauseIcon(action)
                resetAutoCollapseTimer()
            }
        }

        stopIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
        val stopBtn = createIconCircle(btnSizePx, stopIcon!!).apply {
            setOnClickListener { ScreenRecorderService.stop(this@FloatingOverlayService) }
        }

        collapseIcon = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
        val collapseBtn = createIconCircle(btnSizePx, collapseIcon!!).apply {
            setOnClickListener { collapse() }
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val sp = (4 * d).toInt()
            addView(pauseBtn, LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                setMargins(0, 0, sp, 0)
            })
            addView(stopBtn, LinearLayout.LayoutParams(btnSizePx, btnSizePx).apply {
                setMargins(0, 0, sp, 0)
            })
            addView(collapseBtn, LinearLayout.LayoutParams(btnSizePx, btnSizePx))
        }

        expandedContent = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * d).toInt(), 0, (8 * d).toInt(), 0)
            addView(expandedTimer, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { weight = 1f })
            addView(btnRow)
            visibility = View.GONE
        }

        contentView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(collapsedContent)
            addView(expandedContent)
            background = GradientDrawable().apply {
                setColor(COLOR_BG)
                cornerRadius = (COLLAPSED_H_DP / 2f * d)
            }
        }

        overlayContainer = FrameLayout(this).apply {
            addView(contentView)
            setOnTouchListener(OverlayTouchListener())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 6f * d
                clipToOutline = true
            }
        }

        var startX = screenW - colWPx - edgeMarginPx
        var startY = (screenH / 3)

        val savedX = RecordingPreferences.getOverlayX(this)
        val savedY = RecordingPreferences.getOverlayY(this)
        if (savedX != -1 && savedY != -1) {
            startX = if (savedX < screenW / 2) edgeMarginPx else screenW - colWPx - edgeMarginPx
            startY = savedY.coerceIn(0, screenH - colHPx)
        }

        overlayParams = WindowManager.LayoutParams(
            colWPx, colHPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = startX
            y = startY
        }

        try {
            windowManager.addView(overlayContainer, overlayParams!!)
            drawIcons(iconSizePx, strokePx, d)
            startTimerTask()
            startAutoCollapseDelayed()
            Log.d(TAG, "Overlay shown")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed", e)
            overlayContainer = null
        }
    }

    private fun drawIcons(sizePx: Int, strokePx: Float, d: Float) {
        pauseIcon?.setImageDrawable(createPauseIcon(sizePx, strokePx))
        stopIcon?.setImageDrawable(createStopIcon(sizePx, d))
        collapseIcon?.setImageDrawable(createChevronIcon(sizePx, strokePx, up = false))
    }

    private fun hideOverlay() {
        stopTimerTask()
        stopAutoCollapse()
        removeOverlay()
    }

    private fun removeOverlay() {
        overlayContainer?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayContainer = null
        overlayParams = null
        stopSelf()
    }

    private fun expand() {
        if (expanded || overlayContainer == null || overlayParams == null) return
        expanded = true

        val p = overlayParams ?: return
        val container = overlayContainer ?: return
        val cv = contentView
        val density = resources.displayMetrics.density

        val startW = p.width
        val endW = (EXPANDED_W_DP * density).toInt()
        val startH = p.height
        val endH = (EXPANDED_H_DP * density).toInt()
        val startCorner = COLLAPSED_H_DP / 2f * density
        val endCorner = EXPANDED_H_DP / 2f * density
        val bg = cv?.background as? GradientDrawable

        expandedContent?.visibility = View.VISIBLE
        collapsedContent?.visibility = View.INVISIBLE

        animateOverlay(container, p, bg, startW, endW, startH, endH, startCorner, endCorner) {
            collapsedContent?.visibility = View.GONE
            startAutoCollapseDelayed()
        }
    }

    private fun collapse() {
        if (!expanded || overlayContainer == null || overlayParams == null) return
        expanded = false
        stopAutoCollapse()

        val p = overlayParams ?: return
        val container = overlayContainer ?: return
        val cv = contentView
        val density = resources.displayMetrics.density

        val startW = p.width
        val endW = (COLLAPSED_W_DP * density).toInt()
        val startH = p.height
        val endH = (COLLAPSED_H_DP * density).toInt()
        val startCorner = EXPANDED_H_DP / 2f * density
        val endCorner = COLLAPSED_H_DP / 2f * density
        val bg = cv?.background as? GradientDrawable

        expandedContent?.visibility = View.INVISIBLE
        collapsedContent?.visibility = View.VISIBLE

        animateOverlay(container, p, bg, startW, endW, startH, endH, startCorner, endCorner) {
            expandedContent?.visibility = View.GONE
            collapsedContent?.visibility = View.VISIBLE
        }
    }

    private fun animateOverlay(
        container: View, params: WindowManager.LayoutParams,
        bg: GradientDrawable?,
        startW: Int, endW: Int, startH: Int, endH: Int,
        startCorner: Float, endCorner: Float,
        onEnd: () -> Unit
    ) {
        val density = resources.displayMetrics.density
        val screenW = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.widthPixels
        val edgeMarginPx = (EDGE_MARGIN_DP * density).toInt()

        val dockRight = (params.x + startW / 2) > screenW / 2
        val startXLocal = params.x
        val endX = if (dockRight) screenW - endW - edgeMarginPx else edgeMarginPx

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ANIM_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                params.width = (startW + ((endW - startW) * f)).toInt()
                params.height = (startH + ((endH - startH) * f)).toInt()
                params.x = (startXLocal + ((endX - startXLocal) * f)).toInt()
                bg?.cornerRadius = startCorner + ((endCorner - startCorner) * f)
                try { windowManager.updateViewLayout(container, params) } catch (_: Exception) { }
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) { onEnd() }
            })
        }.start()
    }

    private fun startAutoCollapseDelayed() {
        stopAutoCollapse()
        if (!RecordingPreferences.isAutoCollapseEnabled(this)) return
        val delayMs = RecordingPreferences.getAutoCollapseDelayMs(this).coerceAtLeast(500L)
        autoCollapseTask = Runnable { if (expanded) collapse() }
        mainHandler.postDelayed(autoCollapseTask!!, delayMs)
    }

    private fun resetAutoCollapseTimer() {
        if (expanded) { stopAutoCollapse(); startAutoCollapseDelayed() }
    }

    private fun stopAutoCollapse() {
        autoCollapseTask?.let { mainHandler.removeCallbacks(it) }
        autoCollapseTask = null
    }

    private fun startTimerTask() {
        stopTimerTask()
        val r = object : Runnable {
            override fun run() {
                updateUI()
                if (timerTask == this) mainHandler.postDelayed(this, 500)
            }
        }
        timerTask = r
        mainHandler.post(r)
    }

    private fun stopTimerTask() {
        timerTask?.let { mainHandler.removeCallbacks(it) }
        timerTask = null
    }

    private fun updateUI() {
        val secs = RecordingSession.elapsedSeconds
        val time = String.format("%02d:%02d", secs / 60, secs % 60)
        collapsedTimer?.text = time
        expandedTimer?.text = time
        updatePauseIcon(RecordingSession.state)
    }

    private fun updatePauseIcon(state: RecordingState) {
        val isPaused = state == RecordingState.PAUSED
        val d = resources.displayMetrics.density
        val sizePx = (ICON_SIZE_DP * d).toInt()
        pauseIcon?.setImageDrawable(
            if (isPaused) createPlayIcon(sizePx) else createPauseIcon(sizePx, ICON_STROKE_DP * d)
        )
    }

    private fun createIconCircle(sizePx: Int, icon: ImageView): View {
        val btn = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(sizePx, sizePx)
            addView(icon, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
        }
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(COLOR_BTN_BG)
        }
        btn.background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(80, 255, 255, 255)),
                bg, null
            )
        } else bg
        return btn
    }

    private fun createPauseIcon(size: Int, stroke: Float): BitmapDrawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.FILL
        }
        val barW = size / 5f
        val gap = size / 4f
        val h = size * 0.6f
        val cy = size / 2f
        val r = stroke
        c.drawRoundRect(RectF(gap, cy - h / 2, gap + barW, cy + h / 2), r, r, p)
        c.drawRoundRect(RectF(size - gap - barW, cy - h / 2, size - gap, cy + h / 2), r, r, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createPlayIcon(size: Int): BitmapDrawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.FILL
        }
        val cx = size / 2f; val cy = size / 2f; val r = size * 0.35f
        val path = android.graphics.Path().apply {
            moveTo(cx - r * 0.8f, cy - r * 0.9f)
            lineTo(cx + r * 0.7f, cy)
            lineTo(cx - r * 0.8f, cy + r * 0.9f)
            close()
        }
        c.drawPath(path, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createStopIcon(size: Int, d: Float): BitmapDrawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ACCENT; style = Paint.Style.FILL
        }
        val margin = size * 0.24f
        val corner = (2 * d).coerceAtLeast(2f)
        c.drawRoundRect(RectF(margin, margin, size - margin, size - margin), corner, corner, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createChevronIcon(size: Int, stroke: Float, up: Boolean): BitmapDrawable {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.STROKE
            strokeWidth = stroke * 1.5f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val cx = size / 2f; val cy = size / 2f; val r = size * 0.3f
        if (up) {
            c.drawLine(cx - r, cy + r * 0.5f, cx, cy - r * 0.5f, paint)
            c.drawLine(cx, cy - r * 0.5f, cx + r, cy + r * 0.5f, paint)
        } else {
            c.drawLine(cx - r, cy - r * 0.5f, cx, cy + r * 0.5f, paint)
            c.drawLine(cx, cy + r * 0.5f, cx + r, cy - r * 0.5f, paint)
        }
        return BitmapDrawable(resources, bmp)
    }

    private inner class OverlayTouchListener : View.OnTouchListener {
        private var initialX = 0; private var initialY = 0
        private var startTouchX = 0f; private var startTouchY = 0f
        private var dragging = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val p = overlayParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = p.x; initialY = p.y
                    startTouchX = event.rawX; startTouchY = event.rawY
                    dragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - startTouchX).toInt()
                    val dy = (event.rawY - startTouchY).toInt()
                    if (Math.abs(dx) > 5 || Math.abs(dy) > 5) dragging = true
                    p.x = initialX + dx; p.y = initialY + dy
                    try { windowManager.updateViewLayout(v, p) } catch (_: Exception) { }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) {
                        if (expanded) collapse() else expand()
                    } else {
                        if (RecordingPreferences.isSnapToEdgeEnabled(this@FloatingOverlayService)) {
                            snapToEdge(v, p)
                        }
                        RecordingPreferences.setOverlayY(this@FloatingOverlayService, p.y)
                    }
                    return true
                }
            }
            return false
        }

        private fun snapToEdge(v: View, p: WindowManager.LayoutParams) {
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
            val density = resources.displayMetrics.density
            val edgeMarginPx = (EDGE_MARGIN_DP * density).toInt()
            val centerX = p.x + p.width / 2
            val dockLeft = centerX < metrics.widthPixels / 2
            val targetX = if (dockLeft) edgeMarginPx else metrics.widthPixels - p.width - edgeMarginPx
            if (kotlin.math.abs(p.x - targetX) < 10) return
            ValueAnimator.ofInt(p.x, targetX).apply {
                duration = SNAP_ANIM_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    p.x = anim.animatedValue as Int
                    try { windowManager.updateViewLayout(v, p) } catch (_: Exception) { }
                }
                start()
            }
        }
    }
}
