package com.atala.app.ui.run

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.atala.app.R
import com.atala.app.core.l10n.AppLanguage
import com.atala.app.core.l10n.Fmt
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RunRecord
import com.atala.app.core.run.RunFormat
import com.atala.app.ui.theme.Theme
import java.io.File
import java.util.Locale

/** The two looks of the picture shared from a run: forest green, or white. */
enum class RunShareStyle {
    Dark, Light;

    val title: String get() = if (this == Dark) L("Dark") else L("Light")
    val background: Color get() = if (this == Dark) Theme.forest else Color.White
    /** Titles and figures. */
    val primary: Color get() = if (this == Dark) Color.White else Theme.forest
    /** Date and captions. */
    val secondary: Color get() = if (this == Dark) Theme.greenOnDark else Theme.muted
    val route: Color get() = Theme.orange
    val startDot: Color get() = if (this == Dark) Color.White else Theme.green
    val hairline: Color get() = if (this == Dark) Color.White.copy(alpha = 0.16f) else Theme.cardBorder
    val logo: Color get() = if (this == Dark) Theme.cream else Theme.forest
}

/** The words shared with the picture of a run (RunSummaryShare). */
object RunSummaryShare {
    fun title(run: RunRecord): String = if (run.title.isEmpty()) L("Run") else L(run.title)

    /** "Easy run · 6.10 km in 35:12 (5:46/km) · Tuesday 17 November · Atala" */
    fun text(run: RunRecord): String {
        val parts = mutableListOf(title(run))
        parts += if (run.avgPace > 0) L("%@ in %@ (%@/km)", RunFormat.distance(run.distanceM), Fmt.duration(run.durationSec), Fmt.pace(run.avgPace))
        else L("%@ in %@", RunFormat.distance(run.distanceM), Fmt.duration(run.durationSec))
        parts += Fmt.day(run.date)
        parts += "Atala"
        return parts.joinToString(" · ")
    }
}

/**
 * The picture shared from a run (RunShareCard.swift), portrait 4:5, 1080 × 1350 pixels: title and
 * date, the route drawn large from the GPS points, three big figures and the Atala logo. A run
 * without a route shows its figures only. Drawn straight on a bitmap, so it works off screen.
 */
object RunShareRenderer {
    private const val WIDTH = 360f
    private const val HEIGHT = 450f
    private const val SCALE = 3f

    /** Null when the card could not be rendered (the text is shared instead). */
    fun render(context: Context, run: RunRecord, style: RunShareStyle): Bitmap? = runCatching {
        val bitmap = Bitmap.createBitmap((WIDTH * SCALE).toInt(), (HEIGHT * SCALE).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(SCALE, SCALE)
        draw(context, canvas, run, style)
        bitmap
    }.getOrNull()

    private fun draw(context: Context, canvas: Canvas, run: RunRecord, style: RunShareStyle) {
        val rtl = AppLanguage.isRightToLeft
        canvas.drawColor(style.background.toArgb())
        val left = 26f
        val right = WIDTH - 26f
        val contentWidth = right - left
        fun x(start: Float, width: Float): Float = if (rtl) WIDTH - start - width else start

        // Heading
        var y = 28f
        val datePaint = paint(13f, 600, style.secondary)
        val date = TextUtils.ellipsize(Fmt.day(run.date), datePaint, contentWidth, TextUtils.TruncateAt.END).toString()
        y += -datePaint.ascent()
        canvas.drawText(date, x(left, datePaint.measureText(date)), y, datePaint)
        y += datePaint.descent() + 4f
        val title = titleLayout(RunSummaryShare.title(run), style, contentWidth)
        canvas.save()
        canvas.translate(left, y)
        title.draw(canvas)
        canvas.restore()
        y += title.height

        // Bottom: logo, hairline, figures.
        val logoHeight = 16f
        val logoTop = HEIGHT - 22f - logoHeight
        val hairline = logoTop - 14f - 1f
        val figuresBottom = hairline - 18f
        val figuresHeight = 52f
        val figuresTop = figuresBottom - figuresHeight

        val projection = RunRouteProjection.of(run.route)
        val distanceValue = RunFormat.kmValue(run.distanceM)
        val paceValue = if (run.avgPace > 0) Fmt.pace(run.avgPace) else "--"
        val figures = mutableListOf<Triple<String, String?, String>>()
        if (projection != null) {
            drawRoute(canvas, projection, style, left, y + 10f, right, figuresTop - 10f)
            figures += Triple(distanceValue, "km", L("Distance"))
        } else {
            drawHero(canvas, style, distanceValue, left, y, figuresTop, contentWidth, rtl)
        }
        figures += Triple(Fmt.duration(run.durationSec), null, L("Time"))
        figures += Triple(paceValue, "/km", L("Pace"))

        val gap = 10f
        val column = (contentWidth - gap * (figures.size - 1)) / figures.size
        figures.forEachIndexed { index, (value, unit, label) ->
            val start = left + index * (column + gap)
            drawFigure(canvas, style, value, unit, label, x(start, column), column, figuresTop, rtl)
        }

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = style.hairline.toArgb() }
        canvas.drawRect(left, hairline, right, hairline + 1f, line)

        ContextCompat.getDrawable(context, R.drawable.logo_atala)?.mutate()?.let { logo ->
            val ratio = if (logo.intrinsicHeight > 0) logo.intrinsicWidth.toFloat() / logo.intrinsicHeight else 4f
            val width = logoHeight * ratio
            val start = x(left, width)
            logo.setBounds(0, 0, (width * SCALE).toInt(), (logoHeight * SCALE).toInt())
            logo.colorFilter = PorterDuffColorFilter(style.logo.toArgb(), PorterDuff.Mode.SRC_IN)
            canvas.save()
            canvas.translate(start, logoTop)
            canvas.scale(1 / SCALE, 1 / SCALE)
            logo.draw(canvas)
            canvas.restore()
        }
    }

    /** Two lines at most, shrunk down to 70 % when needed. */
    private fun titleLayout(text: String, style: RunShareStyle, width: Float): StaticLayout {
        var size = 26f
        while (true) {
            val textPaint = paint(size, 700, style.primary)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, textPaint, width.toInt())
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(2)
                .setEllipsize(TextUtils.TruncateAt.END)
                .setIncludePad(false)
                .build()
            val overflow = layout.lineCount > 2 || (layout.lineCount == 2 && layout.getEllipsisCount(1) > 0)
            if (!overflow || size <= 26f * 0.7f) return layout
            size -= 1f
        }
    }

    /** The route drawn large: a soft halo, the line, then the start and finish dots. */
    private fun drawRoute(canvas: Canvas, projection: RunRouteProjection, style: RunShareStyle, left: Float, top: Float, right: Float, bottom: Float) {
        val margin = 10f
        val points = projection.fitted(left + margin, top + margin, right - margin, bottom - margin)
        if (points.size < 2) return
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
        }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        stroke.color = style.route.copy(alpha = 0.22f).toArgb()
        stroke.strokeWidth = 13f
        canvas.drawPath(path, stroke)
        stroke.color = style.route.toArgb()
        stroke.strokeWidth = 4.5f
        canvas.drawPath(path, stroke)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fun dot(x: Float, y: Float, color: Color) {
            fill.color = style.background.toArgb()
            canvas.drawCircle(x, y, 8f, fill)
            fill.color = color.toArgb()
            canvas.drawCircle(x, y, 5f, fill)
        }
        dot(points.first().x, points.first().y, style.startDot)
        dot(points.last().x, points.last().y, style.route)
    }

    /** Without a route, the distance takes the place of the drawing. */
    private fun drawHero(canvas: Canvas, style: RunShareStyle, distance: String, left: Float, top: Float, bottom: Float, width: Float, rtl: Boolean) {
        val caption = paint(12f, 600, style.secondary).apply { letterSpacing = 1f / 12f }
        val captionText = L("Distance").uppercase(AppLanguage.locale)
        var valueSize = 96f
        val unitPaint = paint(28f, 700, style.route)
        val unitWidth = unitPaint.measureText("km")
        var valuePaint = paint(valueSize, 700, style.primary).apply { letterSpacing = -3f / valueSize }
        while (valuePaint.measureText(distance) + 8f + unitWidth > width && valueSize > 48f) {
            valueSize -= 2f
            valuePaint = paint(valueSize, 700, style.primary).apply { letterSpacing = -3f / valueSize }
        }
        val blockHeight = (caption.descent() - caption.ascent()) + (valuePaint.descent() - valuePaint.ascent())
        var y = top + (bottom - top - blockHeight) / 2 - caption.ascent()
        val captionWidth = caption.measureText(captionText)
        canvas.drawText(captionText, if (rtl) left + width - captionWidth else left, y, caption)
        y += caption.descent() - valuePaint.ascent()
        val valueWidth = valuePaint.measureText(distance)
        if (rtl) {
            canvas.drawText(distance, left + width - valueWidth, y, valuePaint)
            canvas.drawText("km", left + width - valueWidth - 8f - unitWidth, y, unitPaint)
        } else {
            canvas.drawText(distance, left, y, valuePaint)
            canvas.drawText("km", left + valueWidth + 8f, y, unitPaint)
        }
    }

    private fun drawFigure(canvas: Canvas, style: RunShareStyle, value: String, unit: String?, label: String, start: Float, width: Float, top: Float, rtl: Boolean) {
        val unitPaint = paint(13f, 700, style.primary)
        val unitWidth = unit?.let { unitPaint.measureText(it) + 3f } ?: 0f
        var size = 28f
        var valuePaint = paint(size, 700, style.primary).apply { letterSpacing = -0.5f / size }
        while (valuePaint.measureText(value) + unitWidth > width && size > 14f) {
            size -= 1f
            valuePaint = paint(size, 700, style.primary).apply { letterSpacing = -0.5f / size }
        }
        val baseline = top + 30f
        val valueWidth = valuePaint.measureText(value)
        if (rtl) {
            canvas.drawText(value, start + width - valueWidth, baseline, valuePaint)
            unit?.let { canvas.drawText(it, start + width - valueWidth - unitWidth, baseline, unitPaint) }
        } else {
            canvas.drawText(value, start, baseline, valuePaint)
            unit?.let { canvas.drawText(it, start + valueWidth + 3f, baseline, unitPaint) }
        }
        val labelPaint = paint(11f, 600, style.secondary).apply { letterSpacing = 0.8f / 11f }
        val text = TextUtils.ellipsize(label.uppercase(AppLanguage.locale), labelPaint, width, TextUtils.TruncateAt.END).toString()
        val labelWidth = labelPaint.measureText(text)
        canvas.drawText(text, if (rtl) start + width - labelWidth else start, baseline + 6f - labelPaint.ascent(), labelPaint)
    }

    private fun paint(size: Float, weight: Int, color: Color): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color.toArgb()
        typeface = if (Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, weight, false)
        else Typeface.create(Typeface.DEFAULT, if (weight >= 600) Typeface.BOLD else Typeface.NORMAL)
        fontFeatureSettings = "tnum"
    }

    /** Saves the picture in the cache and opens the system share sheet with it and the text. */
    fun share(context: Context, run: RunRecord, bitmap: Bitmap?) {
        val text = RunSummaryShare.text(run)
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, text)
        val uri = bitmap?.let { picture ->
            runCatching {
                val folder = File(context.cacheDir, "shared").apply { mkdirs() }
                val file = File(folder, "atala-run-${run.id.toString().take(8).lowercase(Locale.ROOT)}.png")
                file.outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
            }.getOrNull()
        }
        if (uri != null) {
            intent.type = "image/png"
            intent.putExtra(Intent.EXTRA_STREAM, uri)
            intent.clipData = ClipData.newRawUri(RunSummaryShare.title(run), uri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            intent.type = "text/plain"
        }
        val chooser = Intent.createChooser(intent, L("Share your run"))
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(chooser) }
    }
}
