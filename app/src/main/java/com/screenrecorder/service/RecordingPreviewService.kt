package com.screenrecorder.service

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import com.screenrecorder.editor.ZoomEditorActivity
import com.screenrecorder.player.PlaybackActivity
import java.io.File

class RecordingPreviewService : Service() {

    private lateinit var windowManager: WindowManager
    private var container: FrameLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var filePath: String? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var dismissTask: Runnable? = null

    companion object {
        private const val TAG = "RecordingPreview"
        private const val ACTION_SHOW = "com.screenrecorder.preview.SHOW"
        private const val EXTRA_FILE_PATH = "file_path"

        private const val SCREEN_RATIO = 0.50f
        private const val OVERLAY_ALPHA = 0.55f
        private const val PHONE_CORNER_DP = 14f
        private const val BEZEL_DP = 10f
        private const val SCREEN_CORNER_DP = 8f
        private const val BUTTON_HEIGHT_DP = 44f
        private const val BUTTON_GAP_DP = 12f
        private const val GAP_DP = 20f
        private const val BADGE_HEIGHT_DP = 28f

        private const val DISPLAY_MS = 15000L
        private const val ANIM_ENTER_MS = 500L
        private const val FLASH_MS = 120L
        private const val BUTTON_FADE_DELAY_MS = 450L
        private const val BUTTON_FADE_MS = 200L

        private val PHONE_BG = 0xFF000000.toInt()
        private val PHONE_EDGE = 0xFF2A2A2A.toInt()
        private val OVERLAY_BG = 0xCC000000.toInt()
        private val BUTTON_BG = 0xFF3A3A3A.toInt()
        private val BUTTON_TEXT = 0xFFFFFFFF.toInt()
        private val SHARE_BG = 0xFFE53935.toInt()
        private val BADGE_BG = 0xAA000000.toInt()
        private val BADGE_TEXT = 0xFFFFFFFF.toInt()

        fun show(context: Context, filePath: String) {
            val intent = Intent(context, RecordingPreviewService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_FILE_PATH, filePath)
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SHOW) {
            filePath = intent.getStringExtra(EXTRA_FILE_PATH)
            showPreview()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        dismissTask?.let { mainHandler.removeCallbacks(it) }
        container?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        container = null
        super.onDestroy()
    }

    private fun showPreview() {
        if (container != null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) return

        val path = filePath ?: return
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return

        val d = resources.displayMetrics.density
        val metrics = DisplayMetrics().also { windowManager.defaultDisplay.getMetrics(it) }
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels

        val cardWidth = (screenW * SCREEN_RATIO).toInt()
        val cornerPx = (PHONE_CORNER_DP * d)
        val bezelPx = (BEZEL_DP * d).toInt()
        val btnH = (BUTTON_HEIGHT_DP * d).toInt()
        val btnGapPx = (BUTTON_GAP_DP * d).toInt()
        val gapPx = (GAP_DP * d).toInt()
        val badgeH = (BADGE_HEIGHT_DP * d).toInt()

        val scrW = cardWidth - 2 * bezelPx

        // --- Load thumbnail ---
        val thumbnail = loadThumbnail(path, scrW)
        val scrH = if (thumbnail != null) {
            (thumbnail.height.toFloat() * (scrW.toFloat() / thumbnail.width)).toInt()
        } else {
            (scrW * 16f / 9f).toInt()
        }

        val frameH = scrH + 2 * bezelPx

        // ============================================================
        //  BUILD VIEWS
        // ============================================================

        // --- Thumbnail ---
        val thumbView = ImageView(this).apply {
            setImageBitmap(thumbnail)
            scaleType = ImageView.ScaleType.FIT_XY
            setOnClickListener { openVideoInApp(path) }
        }

        // --- Badge ---
        val durStr = getDurationString(path)
        val sizeStr = formatFileSize(file.length())
        val badge = TextView(this).apply {
            text = "$durStr  \u2022  $sizeStr"
            textSize = 10f
            setTextColor(BADGE_TEXT)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        val badgeBg = GradientDrawable().apply {
            setColor(BADGE_BG)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, (6 * d), (6 * d), (6 * d), (6 * d))
        }
        badge.background = badgeBg
        val badgeFrame = FrameLayout(this).apply {
            addView(badge, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, badgeH
            ).apply { gravity = Gravity.BOTTOM })
        }

        // --- Screen content (thumbnail + badge) ---
        val screenContent = FrameLayout(this).apply {
            addView(thumbView, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, scrH
            ))
            addView(badgeFrame, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.BOTTOM })
            background = GradientDrawable().apply {
                setColor(PHONE_BG)
                cornerRadius = SCREEN_CORNER_DP * d
            }
            clipToOutline = true
        }

        // --- Phone mockup frame with even thin bezels ---
        val phoneFrame = FrameLayout(this).apply {
            addView(screenContent, FrameLayout.LayoutParams(scrW, scrH).apply {
                setMargins(bezelPx, bezelPx, bezelPx, bezelPx)
            })
            val bg = GradientDrawable().apply {
                setColor(PHONE_BG)
                setStroke(2, PHONE_EDGE)
                cornerRadius = cornerPx
            }
            background = bg
            clipToOutline = true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                elevation = 12f * d
            }
        }

        // --- Buttons ---
        val editBtn = createPillButton("Edit", BUTTON_BG) {
            ZoomEditorActivity.start(this, path)
        }
        val shareBtn = createPillButton("Share", SHARE_BG) {
            shareVideo(path)
        }
        val closeBtn = createPillButton("Close", BUTTON_BG) {
            dismiss()
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(editBtn, LinearLayout.LayoutParams(0, btnH, 1f).apply {
                marginEnd = btnGapPx / 2
            })
            addView(shareBtn, LinearLayout.LayoutParams(0, btnH, 1f).apply {
                marginStart = btnGapPx / 2
                marginEnd = btnGapPx / 2
            })
            addView(closeBtn, LinearLayout.LayoutParams(0, btnH, 1f).apply {
                marginStart = btnGapPx / 2
            })
        }

        // --- Card wrapper (phone frame + buttons, vertically stacked) ---
        val cardWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(phoneFrame, LinearLayout.LayoutParams(cardWidth, frameH))
            addView(btnRow, LinearLayout.LayoutParams(cardWidth, btnH).apply {
                topMargin = gapPx
            })
        }

        // Set initial state for animation
        cardWrapper.pivotX = 0f
        cardWrapper.pivotY = 0f
        cardWrapper.scaleX = 0.3f
        cardWrapper.scaleY = 0.3f
        cardWrapper.alpha = 0f

        // Buttons start invisible
        editBtn.alpha = 0f
        shareBtn.alpha = 0f
        closeBtn.alpha = 0f

        // --- Container (full-screen overlay + card) ---
        val overlay = View(this).apply {
            setBackgroundColor(OVERLAY_BG)
            alpha = 0f
        }
        container = FrameLayout(this).apply {
            clipChildren = false
            addView(overlay, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            addView(cardWrapper, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER })
        }

        params = WindowManager.LayoutParams(
            screenW, screenH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.RGBA_8888
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        try {
            windowManager.addView(container, params)

            // --- FLASH effect (like a camera shutter) ---
            val flash = View(this).apply { setBackgroundColor(Color.WHITE) }
            container!!.addView(flash, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            val flashAnim = ValueAnimator.ofFloat(0.8f, 0f)
            flashAnim.duration = FLASH_MS
            flashAnim.interpolator = AccelerateDecelerateInterpolator()
            flashAnim.addUpdateListener { a -> flash.alpha = a.animatedValue as Float }
            flashAnim.addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) {
                    container?.let { try { it.removeView(flash) } catch (_: Exception) {} }
                }
                override fun onAnimationCancel(animation: Animator) {
                    container?.let { try { it.removeView(flash) } catch (_: Exception) {} }
                }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            flashAnim.start()

            // --- Overlay fade in ---
            val overlayAnim = ObjectAnimator.ofFloat(overlay, "alpha", 0f, OVERLAY_ALPHA)
            overlayAnim.duration = ANIM_ENTER_MS
            overlayAnim.interpolator = AccelerateDecelerateInterpolator()

            // --- Card spring-in animation ---
            val scaleX = ObjectAnimator.ofFloat(cardWrapper, "scaleX", 0.3f, 1.05f, 1f)
            scaleX.duration = ANIM_ENTER_MS
            scaleX.interpolator = OvershootInterpolator(1.8f)

            val scaleY = ObjectAnimator.ofFloat(cardWrapper, "scaleY", 0.3f, 1.05f, 1f)
            scaleY.duration = ANIM_ENTER_MS
            scaleY.interpolator = OvershootInterpolator(1.8f)

            val fadeIn = ObjectAnimator.ofFloat(cardWrapper, "alpha", 0f, 1f)
            fadeIn.duration = ANIM_ENTER_MS

            val enterSet = AnimatorSet()
            enterSet.playTogether(overlayAnim, scaleX, scaleY, fadeIn)
            enterSet.start()

            // --- Buttons fade in after card animation ---
            mainHandler.postDelayed({
                val btnFade1 = ObjectAnimator.ofFloat(editBtn, "alpha", 0f, 1f)
                btnFade1.duration = BUTTON_FADE_MS
                val btnFade2 = ObjectAnimator.ofFloat(shareBtn, "alpha", 0f, 1f)
                btnFade2.duration = BUTTON_FADE_MS
                val btnFade3 = ObjectAnimator.ofFloat(closeBtn, "alpha", 0f, 1f)
                btnFade3.duration = BUTTON_FADE_MS
                AnimatorSet().apply { playTogether(btnFade1, btnFade2, btnFade3) }.start()

                // Start auto-dismiss countdown after buttons appear
                scheduleDismiss()
            }, BUTTON_FADE_DELAY_MS)

            Log.d(TAG, "Preview shown")
        } catch (e: Exception) {
            Log.e(TAG, "showPreview failed", e)
            container = null
        }
    }

    // ----------------------------------------------------------------
    //  Pill button builder
    // ----------------------------------------------------------------

    private fun createPillButton(text: String, bgColor: Int, onClick: () -> Unit): View {
        val btn = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(BUTTON_TEXT)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val bg = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = (BUTTON_HEIGHT_DP / 2f * resources.displayMetrics.density)
        }
        btn.background = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            RippleDrawable(
                android.content.res.ColorStateList.valueOf(Color.argb(40, 255, 255, 255)),
                bg, null
            )
        } else bg
        btn.setOnClickListener { onClick() }
        return btn
    }

    // ----------------------------------------------------------------
    //  Actions
    // ----------------------------------------------------------------

    private fun openVideoInApp(path: String) {
        Log.d(TAG, "openVideoInApp: $path")
        val file = File(path)
        if (!file.exists()) { Log.e(TAG, "File not found: $path"); return }
        if (container == null) return
        PlaybackActivity.start(this, path)
        mainHandler.post { dismiss() }
    }

    private fun shareVideo(path: String) {
        Log.d(TAG, "shareVideo: $path")
        try {
            val file = File(path)
            if (!file.exists()) { Log.e(TAG, "File not found: $path"); return }
            if (container == null) return
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "video/mp4"
                putExtra(Intent.EXTRA_STREAM, uri)
                clipData = ClipData.newRawUri("", uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            IntentProxyActivity.launch(this, Intent.createChooser(intent, "Share Recording"))
            mainHandler.post { dismiss() }
        } catch (e: Exception) {
            Log.e(TAG, "Share failed", e)
            Toast.makeText(this, "Share failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ----------------------------------------------------------------
    //  UI helpers
    // ----------------------------------------------------------------

    private fun scheduleDismiss() {
        dismissTask?.let { mainHandler.removeCallbacks(it) }
        dismissTask = Runnable {
            animateOut()
        }
        mainHandler.postDelayed(dismissTask!!, DISPLAY_MS)
    }

    private fun animateOut() {
        val c = container ?: return
        val cardView = c.getChildAt(1) ?: return
        val overlay = c.getChildAt(0) ?: return

        val fadeOverlay = ObjectAnimator.ofFloat(overlay, "alpha", overlay.alpha, 0f)
        fadeOverlay.duration = 250
        fadeOverlay.interpolator = AccelerateDecelerateInterpolator()

        val shrinkX = ObjectAnimator.ofFloat(cardView, "scaleX", cardView.scaleX, 0.5f)
        shrinkX.duration = 250
        val shrinkY = ObjectAnimator.ofFloat(cardView, "scaleY", cardView.scaleY, 0.5f)
        shrinkY.duration = 250
        val fadeCard = ObjectAnimator.ofFloat(cardView, "alpha", cardView.alpha, 0f)
        fadeCard.duration = 250

        AnimatorSet().apply {
            playTogether(fadeOverlay, shrinkX, shrinkY, fadeCard)
            addListener(object : Animator.AnimatorListener {
                override fun onAnimationEnd(animation: Animator) { stopSelf() }
                override fun onAnimationCancel(animation: Animator) { stopSelf() }
                override fun onAnimationStart(animation: Animator) {}
                override fun onAnimationRepeat(animation: Animator) {}
            })
            start()
        }
    }

    private fun dismiss() {
        dismissTask?.let { mainHandler.removeCallbacks(it) }
        dismissTask = null
        animateOut()
    }

    private fun loadThumbnail(path: String, targetWidthPx: Int): Bitmap? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            return retriever.frameAtTime?.let { frame ->
                val s = targetWidthPx.toFloat() / frame.width
                Bitmap.createScaledBitmap(frame, targetWidthPx, (frame.height * s).toInt(), true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Thumbnail load failed", e)
            return null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun getDurationString(path: String): String {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(path)
            val ms = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            val secs = (ms / 1000).toInt()
            return String.format("%02d:%02d", secs / 60, secs % 60)
        } catch (e: Exception) {
            return "00:00"
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024f)
            else -> String.format("%.1f MB", bytes / (1024f * 1024f))
        }
    }
}
