package com.screenrecorder.service

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.view.View

class TouchIndicatorView(context: Context) : View(context) {

    init {
        setWillNotDraw(false)
    }

    private data class TapIndicator(
        val x: Float,
        val y: Float,
        val startTime: Long
    )

    private val indicators = mutableListOf<TapIndicator>()
    private val density = resources.displayMetrics.density
    private val fadeDurationMs = 500L
    private val rippleFadeDurationMs = 400L
    private val strokeWidth = 2f * density

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = this@TouchIndicatorView.strokeWidth
    }

    private val animationHandler = Handler(Looper.getMainLooper())
    private val animationRunnable = Runnable { invalidate() }

    fun addTap(x: Float, y: Float) {
        indicators.add(TapIndicator(x, y, System.currentTimeMillis()))
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val config = IndicatorConfigProvider.get()
        val now = System.currentTimeMillis()
        val baseRadius = config.sizeDp * density
        val parsedColor = parseColorInt(config.color)

        val iterator = indicators.iterator()
        while (iterator.hasNext()) {
            val indicator = iterator.next()
            val age = now - indicator.startTime
            val duration = if (config.shape == "ripple") rippleFadeDurationMs else fadeDurationMs

            if (age > duration) {
                iterator.remove()
                continue
            }

            val progress = age.toFloat() / duration
            val alpha = 1f - progress

            val fillAlpha = (102 * alpha).toInt().coerceIn(0, 255)
            val strokeAlpha = (180 * alpha).toInt().coerceIn(0, 255)

            when (config.shape) {
                "square" -> {
                    val r = baseRadius
                    val cornerRadius = r / 4f
                    fillPaint.color = Color.argb(
                        fillAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    strokePaint.color = Color.argb(
                        strokeAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    canvas.drawRoundRect(
                        indicator.x - r, indicator.y - r,
                        indicator.x + r, indicator.y + r,
                        cornerRadius, cornerRadius,
                        fillPaint
                    )
                    canvas.drawRoundRect(
                        indicator.x - r, indicator.y - r,
                        indicator.x + r, indicator.y + r,
                        cornerRadius, cornerRadius,
                        strokePaint
                    )
                }
                "ripple" -> {
                    val currentRadius = baseRadius * (0.3f + 1.2f * progress)
                    fillPaint.color = Color.argb(
                        fillAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    strokePaint.color = Color.argb(
                        strokeAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    canvas.drawCircle(indicator.x, indicator.y, currentRadius, fillPaint)
                    canvas.drawCircle(indicator.x, indicator.y, currentRadius, strokePaint)
                }
                else -> {
                    val r = baseRadius
                    fillPaint.color = Color.argb(
                        fillAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    strokePaint.color = Color.argb(
                        strokeAlpha,
                        Color.red(parsedColor),
                        Color.green(parsedColor),
                        Color.blue(parsedColor)
                    )
                    canvas.drawCircle(indicator.x, indicator.y, r, fillPaint)
                    canvas.drawCircle(indicator.x, indicator.y, r, strokePaint)
                }
            }
        }

        if (indicators.isNotEmpty()) {
            animationHandler.postDelayed(animationRunnable, 16)
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationHandler.removeCallbacks(animationRunnable)
        indicators.clear()
    }

    private fun parseColorInt(hex: String): Int {
        return try {
            Color.parseColor(hex)
        } catch (e: Exception) {
            Color.WHITE
        }
    }
}

object TouchIndicators {
    private var view: TouchIndicatorView? = null

    fun attach(v: TouchIndicatorView) {
        view = v
    }

    fun detach() {
        view?.let {
            it.post { view = null }
        } ?: run { view = null }
    }

    fun show(x: Float, y: Float) {
        view?.addTap(x, y)
    }
}
