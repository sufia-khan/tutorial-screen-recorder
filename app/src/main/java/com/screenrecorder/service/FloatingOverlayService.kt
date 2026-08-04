package com.screenrecorder.service

import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
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
import android.widget.TextView
import com.screenrecorder.MainActivity
import com.screenrecorder.manager.RecordingPreferences
import com.screenrecorder.model.RecorderRuntime
import com.screenrecorder.model.RecordingState
import com.screenrecorder.model.shouldShowOverlay
import kotlin.math.cos
import kotlin.math.sin

class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayContainer: FrameLayout? = null
    private var contentView: FrameLayout? = null
    private var timerView: TextView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var radiusPx = 0
    private var expanded = false
    private var dockSide = DockSide.RIGHT
    private var windowAnimator: ValueAnimator? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var timerTask: Runnable? = null
    private var autoCollapseTask: Runnable? = null

    private lateinit var pauseIconView: ImageView
    private lateinit var collapseIconView: ImageView
    private lateinit var pauseBtn: View
    private lateinit var stopBtn: View
    private lateinit var collapseBtn: View
    private lateinit var homeBtn: View
    private lateinit var settingsBtn: View
    private val arcButtonViews = mutableListOf<View>()

    private val expandedW: Int
        get() {
            val d = resources.displayMetrics.density
            val gap = (ARC_GAP_DP * d).toInt()
            val btnR = (BTN_TOUCH_DP / 2f * d).toInt()
            return radiusPx + gap + 2 * btnR
        }

    private val expandedH: Int
        get() {
            val d = resources.displayMetrics.density
            val gap = (ARC_GAP_DP * d).toInt()
            val btnR = (BTN_TOUCH_DP / 2f * d).toInt()
            return radiusPx * 2 + 2 * (gap + 2 * btnR)
        }

    companion object {
        private const val TAG = "FloatingOverlay"

        private const val ACTION_SHOW = "com.screenrecorder.overlay.SHOW"
        private const val ACTION_HIDE = "com.screenrecorder.overlay.HIDE"
        private const val ACTION_COLLAPSE = "com.screenrecorder.overlay.COLLAPSE"

        private const val SNAP_ANIM_MS = 160L
        private const val EXPAND_ANIM_MS = 220L
        private const val FLY_STAGGER_MS = 25L
        private const val VERTICAL_MARGIN_DP = 8
        private const val TIMER_SP = 14f
        private const val CONTENT_PADDING_DP = 12
        private const val ARC_GAP_DP = 4
        private const val BTN_TOUCH_DP = 32
        private const val ICON_DP = 14

        private const val COLOR_BG_BASE = 0xFF1C1C1C.toInt()
        private const val COLOR_TIMER = 0xFFFFFFFF.toInt()
        private const val COLOR_ICON = 0xFF000000.toInt()
        private const val COLOR_STOP = 0xFFE53935.toInt()
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

        fun collapse(context: Context) {
            context.startService(Intent(context, FloatingOverlayService::class.java).apply {
                action = ACTION_COLLAPSE
            })
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "[DEBUG-lock] onCreate")
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "[DEBUG-lock] onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            ACTION_COLLAPSE -> collapse()
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

        val radiusDp = RecordingPreferences.getOverlaySize(this)
        radiusPx = (radiusDp * d).toInt()
        val collapsedW = radiusPx
        val collapsedH = radiusPx * 2
        val verticalMarginPx = (VERTICAL_MARGIN_DP * d).toInt()

        var startX = screenW - collapsedW
        var startY = (screenH / 3).coerceIn(verticalMarginPx, screenH - collapsedH - verticalMarginPx)

        val savedX = RecordingPreferences.getOverlayX(this)
        val savedY = RecordingPreferences.getOverlayY(this)
        if (savedX != -1 && savedY != -1) {
            dockSide = if (savedX < screenW / 2) DockSide.LEFT else DockSide.RIGHT
            startX = if (dockSide == DockSide.LEFT) 0 else screenW - collapsedW
            startY = savedY.coerceIn(verticalMarginPx, screenH - collapsedH - verticalMarginPx)
        }

        timerView = TextView(this).apply {
            text = RecorderRuntime.formatElapsed(0)
            textSize = TIMER_SP
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(COLOR_TIMER)
            gravity = Gravity.CENTER
        }

        contentView = FrameLayout(this).apply {
            setPadding(
                (CONTENT_PADDING_DP * d).toInt(),
                (CONTENT_PADDING_DP * d).toInt(),
                (CONTENT_PADDING_DP * d).toInt(),
                (CONTENT_PADDING_DP * d).toInt()
            )
            addView(timerView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            background = createHalfCircleDrawable(radiusPx, dockSide)
        }

        pauseIconView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
        pauseBtn = createActionButton(pauseIconView) {
            val action = if (RecorderRuntime.state == RecordingState.RECORDING) {
                ScreenRecorderService.pause(this@FloatingOverlayService)
                RecordingState.PAUSED
            } else {
                ScreenRecorderService.resume(this@FloatingOverlayService)
                RecordingState.RECORDING
            }
            updatePauseIcon(action)
            collapse()
        }

        stopBtn = createActionButton(ImageView(this)) {
            ScreenRecorderService.stop(this@FloatingOverlayService)
            collapse()
        }

        collapseIconView = ImageView(this).apply { scaleType = ImageView.ScaleType.CENTER }
        collapseBtn = createActionButton(collapseIconView) { collapse() }

        homeBtn = createActionButton(ImageView(this)) {
            collapse()
            IntentProxyActivity.launch(
                this@FloatingOverlayService,
                Intent(this@FloatingOverlayService, MainActivity::class.java)
            )
        }

        settingsBtn = createActionButton(ImageView(this)) {
            collapse()
            IntentProxyActivity.launch(
                this@FloatingOverlayService,
                Intent(this@FloatingOverlayService, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_OPEN_SCREEN, MainActivity.VALUE_SCREEN_SETTINGS)
                }
            )
        }

        overlayContainer = FrameLayout(this).apply {
            addView(contentView)
            addView(homeBtn)
            addView(pauseBtn)
            addView(stopBtn)
            addView(settingsBtn)
            addView(collapseBtn)
            setOnTouchListener(OverlayTouchListener())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 6f * d
                clipToOutline = true
            }
        }

        overlayParams = WindowManager.LayoutParams(
            collapsedW, collapsedH,
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
            layoutForDock(dockSide, windowSize = false)
            drawIcons()
            startTimerTask()
            Log.d(TAG, "Overlay shown (collapsed)")
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed", e)
            overlayContainer = null
        }
    }

    private fun createActionButton(icon: ImageView, onClick: () -> Unit): View {
        val d = resources.displayMetrics.density
        val touchPx = (BTN_TOUCH_DP * d).toInt()
        return FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(touchPx, touchPx)
            addView(icon, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
            setOnClickListener { onClick() }
            visibility = View.GONE
        }
    }

    private fun hideOverlay() {
        stopTimerTask()
        stopAutoCollapse()
        removeOverlay()
    }

    private fun removeOverlay() {
        Log.d(TAG, "[DEBUG-lock] removeOverlay container=${overlayContainer != null}")
        windowAnimator?.cancel()
        arcButtonViews.forEach { it.animate().cancel() }
        overlayContainer?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        overlayContainer = null
        contentView = null
        timerView = null
        overlayParams = null
        arcButtonViews.clear()
        stopSelf()
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
        val snap = RecorderRuntime.snapshot()
        val shouldShow = shouldShowOverlay(
            snap.state,
            RecorderRuntime.pausedByLock,
            RecorderRuntime.deviceLocked
        )
        if (!shouldShow) {
            hideOverlay()
            return
        }
        timerView?.text = RecorderRuntime.formatElapsed(snap.elapsedSeconds)
        updatePauseIcon(snap.state)
    }

    private fun expand(animate: Boolean = true) {
        if (expanded) return
        expanded = true
        val p = overlayParams ?: return
        val container = overlayContainer ?: return
        stopAutoCollapse()
        layoutForDock(dockSide, windowSize = false)

        if (animate) {
            animateWindowSize(container, p, expand = true)
            flyOutButtons(show = true)
        } else {
            applyWindowSize(p, expanded = true)
            arcButtonViews.forEach { it.alpha = 1f; it.translationX = 0f; it.visibility = View.VISIBLE }
        }
        setWatchOutsideTouches(true)
        startAutoCollapseDelayed()
    }

    private fun collapse() {
        if (!expanded) return
        expanded = false
        stopAutoCollapse()
        val p = overlayParams ?: return
        val container = overlayContainer ?: return
        layoutForDock(dockSide, windowSize = false)

        flyOutButtons(show = false)
        animateWindowSize(container, p, expand = false)
        setWatchOutsideTouches(false)
    }

    private fun animateWindowSize(container: View, p: WindowManager.LayoutParams, expand: Boolean) {
        windowAnimator?.cancel()
        val screenW = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.widthPixels

        val startW = p.width
        val startH = p.height
        val endW = if (expand) expandedW else radiusPx
        val endH = if (expand) expandedH else radiusPx * 2
        val startX = p.x
        val endX = if (dockSide == DockSide.LEFT) 0 else screenW - endW

        windowAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = EXPAND_ANIM_MS
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                p.width = (startW + ((endW - startW) * f)).toInt()
                p.height = (startH + ((endH - startH) * f)).toInt()
                p.x = (startX + ((endX - startX) * f)).toInt()
                pinContentToWindow(p)
                try { windowManager.updateViewLayout(container, p) } catch (_: Exception) { }
            }
        }.also { it.start() }
    }

    private fun applyWindowSize(p: WindowManager.LayoutParams, expanded: Boolean) {
        val screenW = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.widthPixels
        p.width = if (expanded) expandedW else radiusPx
        p.height = if (expanded) expandedH else radiusPx * 2
        p.x = if (dockSide == DockSide.LEFT) 0 else screenW - p.width
        pinContentToWindow(p)
        overlayContainer?.let {
            try { windowManager.updateViewLayout(it, p) } catch (_: Exception) { }
        }
    }

    private fun pinContentToWindow(p: WindowManager.LayoutParams) {
        contentView?.layoutParams = FrameLayout.LayoutParams(radiusPx, radiusPx * 2).apply {
            leftMargin = if (dockSide == DockSide.LEFT) 0 else p.width - radiusPx
            topMargin = (p.height - radiusPx * 2) / 2
        }
    }

    private fun flyOutButtons(show: Boolean) {
        val d = resources.displayMetrics.density
        val btnR = (BTN_TOUCH_DP / 2f * d).toInt()
        val flyDist = (radiusPx + (ARC_GAP_DP * d).toInt() + btnR).toFloat()
        val direction = if (dockSide == DockSide.LEFT) -1f else 1f

        arcButtonViews.forEachIndexed { i, v ->
            v.animate().cancel()
            v.visibility = View.VISIBLE
            v.alpha = if (show) 0f else 1f
            v.translationX = if (show) direction * flyDist else 0f
            v.animate()
                .translationX(if (show) 0f else direction * flyDist)
                .alpha(if (show) 1f else 0f)
                .setDuration(EXPAND_ANIM_MS)
                .setStartDelay(i * FLY_STAGGER_MS)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    if (!show) {
                        v.alpha = 1f
                        v.translationX = 0f
                        v.visibility = View.GONE
                    }
                }
                .start()
        }
    }

    private fun setWatchOutsideTouches(enabled: Boolean) {
        val p = overlayParams ?: return
        p.flags = if (enabled) {
            p.flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        } else {
            p.flags and WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
        }
        overlayContainer?.let {
            try { windowManager.updateViewLayout(it, p) } catch (_: Exception) { }
        }
    }

    private fun startAutoCollapseDelayed() {
        stopAutoCollapse()
        if (!RecordingPreferences.isAutoCollapseEnabled(this)) return
        val delayMs = RecordingPreferences.getAutoCollapseDelayMs(this).coerceAtLeast(500L)
        autoCollapseTask = Runnable { if (expanded) collapse() }
        mainHandler.postDelayed(autoCollapseTask!!, delayMs)
    }

    private fun stopAutoCollapse() {
        autoCollapseTask?.let { mainHandler.removeCallbacks(it) }
        autoCollapseTask = null
    }

    private fun createHalfCircleDrawable(radiusPx: Int, side: DockSide): GradientDrawable {
        val opacity = RecordingPreferences.getOverlayOpacity(this).coerceIn(30, 100)
        val alpha = (255 * opacity / 100).coerceIn(0, 255)
        val r = radiusPx.toFloat()
        return GradientDrawable().apply {
            setColor(Color.argb(alpha, Color.red(COLOR_BG_BASE), Color.green(COLOR_BG_BASE), Color.blue(COLOR_BG_BASE)))
            setCornerRadii(when (side) {
                DockSide.RIGHT -> floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r)
                DockSide.LEFT -> floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
            })
        }
    }

    private fun layoutForDock(side: DockSide, windowSize: Boolean) {
        val sideChanged = side != dockSide
        dockSide = side
        val d = resources.displayMetrics.density
        val gap = (ARC_GAP_DP * d).toInt()
        val btnR = (BTN_TOUCH_DP / 2f * d).toInt()
        val dOff = radiusPx + gap + btnR
        val expW = radiusPx + gap + 2 * btnR
        val expH = radiusPx * 2 + 2 * (gap + 2 * btnR)
        val yBase = expH / 2

        val angles = intArrayOf(0, 45, 90, 135, 180)
        val views = listOf(homeBtn, pauseBtn, stopBtn, settingsBtn, collapseBtn)
        views.forEachIndexed { i, v ->
            val rad = Math.toRadians(angles[i].toDouble())
            val left = if (side == DockSide.RIGHT) {
                expW - (dOff * sin(rad)).toInt()
            } else {
                (dOff * sin(rad)).toInt()
            }
            val top = (yBase - (dOff * cos(rad)).toInt())
            val lp = FrameLayout.LayoutParams((BTN_TOUCH_DP * d).toInt(), (BTN_TOUCH_DP * d).toInt()).apply {
                leftMargin = left
                topMargin = top
            }
            v.layoutParams = lp
            if (!arcButtonViews.contains(v)) arcButtonViews.add(v)
        }

        val bg = contentView?.background as? GradientDrawable
        bg?.setCornerRadii(when (side) {
            DockSide.RIGHT -> floatArrayOf(
                radiusPx.toFloat(), radiusPx.toFloat(), 0f, 0f, 0f, 0f,
                radiusPx.toFloat(), radiusPx.toFloat()
            )
            DockSide.LEFT -> floatArrayOf(
                0f, 0f, radiusPx.toFloat(), radiusPx.toFloat(),
                radiusPx.toFloat(), radiusPx.toFloat(), 0f, 0f
            )
        })

        if (sideChanged) updateCollapseIcon()

        if (windowSize) {
            applyWindowSize(overlayParams ?: return, expanded)
        }
    }

    private fun drawIcons() {
        val d = resources.displayMetrics.density
        val iconPx = (ICON_DP * d).toInt()
        val strokePx = ICON_STROKE_DP * d
        (homeBtn as? ViewGroup)?.getChildAt(0)?.let {
            (it as ImageView).setImageDrawable(createHomeIcon(iconPx))
        }
        pauseIconView.setImageDrawable(createPauseIcon(iconPx, strokePx))
        (stopBtn as? ViewGroup)?.getChildAt(0)?.let {
            (it as ImageView).setImageDrawable(createStopIcon(iconPx, d))
        }
        (settingsBtn as? ViewGroup)?.getChildAt(0)?.let {
            (it as ImageView).setImageDrawable(createGearIcon(iconPx, strokePx))
        }
        updateCollapseIcon()
    }

    private fun updateCollapseIcon() {
        if (!::collapseIconView.isInitialized) return
        val d = resources.displayMetrics.density
        val iconPx = (ICON_DP * d).toInt()
        collapseIconView.setImageDrawable(
            createChevronIcon(iconPx, ICON_STROKE_DP * d, towardEdge = dockSide == DockSide.RIGHT)
        )
    }

    private fun updatePauseIcon(state: RecordingState) {
        if (!::pauseIconView.isInitialized) return
        val isPaused = state == RecordingState.PAUSED
        val d = resources.displayMetrics.density
        val iconPx = (ICON_DP * d).toInt()
        pauseIconView.setImageDrawable(
            if (isPaused) createPlayIcon(iconPx) else createPauseIcon(iconPx, ICON_STROKE_DP * d)
        )
    }

    private fun iconCanvas(size: Int): Pair<Bitmap, Canvas> {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        return bmp to Canvas(bmp)
    }

    private fun createPauseIcon(size: Int, stroke: Float): BitmapDrawable {
        val (bmp, c) = iconCanvas(size)
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
        val (bmp, c) = iconCanvas(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.FILL
        }
        val cx = size / 2f; val cy = size / 2f; val r = size * 0.35f
        val path = Path().apply {
            moveTo(cx - r * 0.8f, cy - r * 0.9f)
            lineTo(cx + r * 0.7f, cy)
            lineTo(cx - r * 0.8f, cy + r * 0.9f)
            close()
        }
        c.drawPath(path, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createStopIcon(size: Int, d: Float): BitmapDrawable {
        val (bmp, c) = iconCanvas(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_STOP; style = Paint.Style.FILL
        }
        val margin = size * 0.24f
        val corner = (2 * d).coerceAtLeast(2f)
        c.drawRoundRect(RectF(margin, margin, size - margin, size - margin), corner, corner, p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createChevronIcon(size: Int, stroke: Float, towardEdge: Boolean): BitmapDrawable {
        val (bmp, c) = iconCanvas(size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.STROKE
            strokeWidth = stroke * 1.5f
            strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val cx = size / 2f; val cy = size / 2f; val r = size * 0.3f
        if (towardEdge) {
            c.drawLine(cx - r, cy - r * 0.55f, cx + r * 0.4f, cy, paint)
            c.drawLine(cx + r * 0.4f, cy, cx - r, cy + r * 0.55f, paint)
        } else {
            c.drawLine(cx + r, cy - r * 0.55f, cx - r * 0.4f, cy, paint)
            c.drawLine(cx - r * 0.4f, cy, cx + r, cy + r * 0.55f, paint)
        }
        return BitmapDrawable(resources, bmp)
    }

    private fun createHomeIcon(size: Int): BitmapDrawable {
        val (bmp, c) = iconCanvas(size)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.FILL
        }
        val cx = size / 2f; val cy = size / 2f
        val roof = Path().apply {
            moveTo(cx, cy - size * 0.38f)
            lineTo(cx - size * 0.42f, cy - size * 0.05f)
            lineTo(cx + size * 0.42f, cy - size * 0.05f)
            close()
        }
        c.drawPath(roof, p)
        c.drawRect(RectF(
            cx - size * 0.26f, cy - size * 0.05f,
            cx + size * 0.26f, cy + size * 0.38f
        ), p)
        return BitmapDrawable(resources, bmp)
    }

    private fun createGearIcon(size: Int, stroke: Float): BitmapDrawable {
        val (bmp, c) = iconCanvas(size)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON; style = Paint.Style.STROKE
            strokeWidth = stroke * 1.4f
            strokeCap = Paint.Cap.ROUND
        }
        val cx = size / 2f; val cy = size / 2f
        val inner = size * 0.12f
        val outer = size * 0.34f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            c.drawLine(
                cx + inner * cos(a).toFloat(),
                cy + inner * sin(a).toFloat(),
                cx + outer * cos(a).toFloat(),
                cy + outer * sin(a).toFloat(),
                paint
            )
        }
        c.drawCircle(cx, cy, size * 0.17f, paint)
        return BitmapDrawable(resources, bmp)
    }

    private enum class DockSide { LEFT, RIGHT }

    private fun dockSideFromX(centerX: Int): DockSide {
        val screenW = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.widthPixels
        return if (centerX < screenW / 2) DockSide.LEFT else DockSide.RIGHT
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
                    val screenW = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.widthPixels
                    val screenH = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }.heightPixels
                    val verticalMarginPx = (VERTICAL_MARGIN_DP * resources.displayMetrics.density).toInt()
                    p.x = (initialX + dx).coerceIn(0, screenW - p.width)
                    p.y = (initialY + dy).coerceIn(verticalMarginPx, screenH - p.height - verticalMarginPx)
                    try { windowManager.updateViewLayout(v, p) } catch (_: Exception) { }
                    layoutForDock(dockSideFromX(p.x + p.width / 2), windowSize = false)
                    if (expanded) startAutoCollapseDelayed()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        if (RecordingPreferences.isSnapToEdgeEnabled(this@FloatingOverlayService)) {
                            snapToEdge(v, p)
                        } else {
                            layoutForDock(dockSideFromX(p.x + p.width / 2), windowSize = false)
                        }
                        RecordingPreferences.setOverlayY(this@FloatingOverlayService, p.y)
                    } else {
                        if (expanded) collapse() else expand()
                    }
                    return true
                }
                MotionEvent.ACTION_OUTSIDE -> {
                    if (expanded) collapse()
                    return true
                }
            }
            return false
        }

        private fun snapToEdge(v: View, p: WindowManager.LayoutParams) {
            val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
            val centerX = p.x + p.width / 2
            val dockLeft = centerX < metrics.widthPixels / 2
            val targetX = if (dockLeft) 0 else metrics.widthPixels - p.width
            if (kotlin.math.abs(p.x - targetX) < 10) return
            ValueAnimator.ofInt(p.x, targetX).apply {
                duration = SNAP_ANIM_MS
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { anim ->
                    p.x = anim.animatedValue as Int
                    try { windowManager.updateViewLayout(v, p) } catch (_: Exception) { }
                }
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        layoutForDock(dockSideFromX(p.x + p.width / 2), windowSize = false)
                    }
                })
                start()
            }
        }
    }
}
