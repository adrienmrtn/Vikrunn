package com.atala.app.ui.run

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.atala.app.BuildConfig
import com.atala.app.core.l10n.L
import com.atala.app.core.model.RoutePoint
import com.atala.app.ui.components.rememberHaptics
import com.atala.app.ui.theme.Theme
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The GPS points of a run laid flat (RunShareProjection): longitudes squeezed by the cosine of the
 * latitude so that shapes keep their proportions (equirectangular projection), north at the top.
 * Used by the summary when there are no map tiles and by the shared picture.
 */
class RunRouteProjection private constructor(
    /** Projected points; x grows to the east, y to the south, the smallest of each is 0. */
    val xs: DoubleArray,
    val ys: DoubleArray,
    val width: Double,
    val height: Double,
) {
    /** The points scaled to fit the rectangle, proportions kept, centred. */
    fun fitted(left: Float, top: Float, right: Float, bottom: Float): List<Offset> {
        val rectWidth = right - left
        val rectHeight = bottom - top
        if (rectWidth <= 0 || rectHeight <= 0) return emptyList()
        val w = max(width, 1e-9)
        val h = max(height, 1e-9)
        val scale = min(rectWidth / w, rectHeight / h)
        val offsetX = left + (rectWidth - width * scale) / 2
        val offsetY = top + (rectHeight - height * scale) / 2
        return xs.indices.map { Offset((offsetX + xs[it] * scale).toFloat(), (offsetY + ys[it] * scale).toFloat()) }
    }

    companion object {
        /** Null when there is no route to draw (fewer than two points, or no movement). */
        fun of(route: List<RoutePoint>): RunRouteProjection? {
            if (route.size < 2) return null
            val kept = thinned(route, 600)
            val minLat = kept.minOf { it.lat }
            val maxLat = kept.maxOf { it.lat }
            val minLon = kept.minOf { it.lon }
            val maxLon = kept.maxOf { it.lon }
            val squeeze = max(0.01, cos((minLat + maxLat) / 2 * Math.PI / 180))
            val width = (maxLon - minLon) * squeeze
            val height = maxLat - minLat
            // Less than a few metres in every direction: nothing worth drawing.
            if (max(width, height) <= 0.00003) return null
            return RunRouteProjection(
                DoubleArray(kept.size) { (kept[it].lon - minLon) * squeeze },
                DoubleArray(kept.size) { maxLat - kept[it].lat },
                width, height,
            )
        }

        private fun thinned(route: List<RoutePoint>, limit: Int): List<RoutePoint> {
            if (route.size <= limit) return route
            val step = (route.size - 1).toDouble() / (limit - 1)
            return (0 until limit).map { route[min(route.size - 1, (it * step).roundToInt())] }
        }
    }
}

/** True when a Google Maps key was set at build time (ATALA_MAPS_API_KEY). */
val runMapsAvailable: Boolean get() = BuildConfig.HAS_MAPS_KEY

/**
 * The route of the run at the top of the summary (RunRouteMap). Not interactive, so it scrolls with
 * the page; a tap opens it full screen, where it can be moved and zoomed. Without a Maps key, the
 * route is drawn on a light background instead of map tiles.
 */
@Composable
fun RunRouteMap(route: List<RoutePoint>, caption: String) {
    val haptics = rememberHaptics()
    var expanded by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(24.dp)
    val label = L("Map of your route")
    Box(
        Modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(shape)
            .border(1.dp, Theme.cardBorder, shape),
    ) {
        RouteLayer(route, interactive = false)
        // Covers the map: a tap anywhere opens it full screen.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(role = Role.Button) {
                    haptics.tap()
                    expanded = true
                }
                .semantics { contentDescription = label },
            contentAlignment = Alignment.TopEnd,
        ) {
            Box(
                Modifier
                    .padding(12.dp)
                    .size(40.dp)
                    .shadow(6.dp, CircleShape)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.OpenInFull, null, tint = Theme.ink, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (expanded) {
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            RunRouteFullMap(route, caption, onClose = { expanded = false })
        }
    }
}

/** The route full screen: the map can be moved and zoomed. Closed with the round button. */
@Composable
private fun RunRouteFullMap(route: List<RoutePoint>, caption: String, onClose: () -> Unit) {
    val haptics = rememberHaptics()
    Box(Modifier.fillMaxSize().background(Color.White)) {
        RouteLayer(route, interactive = true)
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            val closeLabel = L("Close")
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .size(44.dp)
                    .shadow(8.dp, CircleShape)
                    .background(Color.White, CircleShape)
                    .clickable(role = Role.Button) {
                        haptics.tap()
                        onClose()
                    }
                    .semantics { contentDescription = closeLabel },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Theme.ink, modifier = Modifier.size(20.dp))
            }
            if (caption.isNotEmpty()) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .height(44.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Theme.forest, CircleShape)
                        .padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(caption, style = Theme.font(17, FontWeight.Bold).digits(), color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun RouteLayer(route: List<RoutePoint>, interactive: Boolean) {
    if (runMapsAvailable) GoogleRouteMap(route, interactive) else RouteVector(route)
}

/** The route on Google Maps: a white-edged orange line, a green dot at the start, a black one at the finish. */
@Composable
private fun GoogleRouteMap(route: List<RoutePoint>, interactive: Boolean) {
    val points = remember(route) { route.map { LatLng(it.lat, it.lon) } }
    val camera = rememberCameraPositionState()
    val density = LocalDensity.current
    val padding = if (interactive) 0.35 else 0.5
    val bounds = remember(route, padding) {
        val minLat = route.minOf { it.lat }
        val maxLat = route.maxOf { it.lat }
        val minLon = route.minOf { it.lon }
        val maxLon = route.maxOf { it.lon }
        val latSpan = max(0.003, (maxLat - minLat) * (1 + padding)) / 2
        val lonSpan = max(0.003, (maxLon - minLon) * (1 + padding)) / 2
        val centreLat = (minLat + maxLat) / 2
        val centreLon = (minLon + maxLon) / 2
        LatLngBounds(LatLng(centreLat - latSpan, centreLon - lonSpan), LatLng(centreLat + latSpan, centreLon + lonSpan))
    }
    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = camera,
        uiSettings = MapUiSettings(
            compassEnabled = false,
            indoorLevelPickerEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
            rotationGesturesEnabled = interactive,
            scrollGesturesEnabled = interactive,
            scrollGesturesEnabledDuringRotateOrZoom = interactive,
            tiltGesturesEnabled = interactive,
            zoomControlsEnabled = false,
            zoomGesturesEnabled = interactive,
        ),
        onMapLoaded = { camera.move(CameraUpdateFactory.newLatLngBounds(bounds, 0)) },
    ) {
        Polyline(points = points, color = Color.White, width = with(density) { 8.dp.toPx() })
        Polyline(points = points, color = Theme.orange, width = with(density) { 4.5.dp.toPx() })
        MarkerComposable(state = rememberMarkerState(position = points.first()), anchor = Offset(0.5f, 0.5f)) {
            RouteDot(Theme.green)
        }
        MarkerComposable(state = rememberMarkerState(position = points.last()), anchor = Offset(0.5f, 0.5f)) {
            RouteDot(Theme.ink)
        }
    }
}

@Composable
private fun RouteDot(color: Color) {
    Box(Modifier.size(14.dp).background(Color.White, CircleShape).padding(3.dp).background(color, CircleShape))
}

/** No map tiles: the route drawn with the share card's projection on a light background. */
@Composable
private fun RouteVector(route: List<RoutePoint>) {
    val projection = remember(route) { RunRouteProjection.of(route) }
    Canvas(Modifier.fillMaxSize().background(Color(0xFFEFF3EE))) {
        drawGrid()
        val margin = 28.dp.toPx()
        val points = projection?.fitted(margin, margin, size.width - margin, size.height - margin) ?: return@Canvas
        if (points.isEmpty()) return@Canvas
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, Color.White, style = Stroke(8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(path, Theme.orange, style = Stroke(4.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawCircle(Color.White, 7.dp.toPx(), points.first())
        drawCircle(Theme.green, 4.dp.toPx(), points.first())
        drawCircle(Color.White, 7.dp.toPx(), points.last())
        drawCircle(Theme.ink, 4.dp.toPx(), points.last())
    }
}

/** A faint street-plan grid so the background reads as a map. */
private fun DrawScope.drawGrid() {
    val step = 36.dp.toPx()
    val color = Color(0xFFE2E8E1)
    var x = step / 2
    while (x < size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
        x += step
    }
    var y = step / 2
    while (y < size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
        y += step
    }
}
