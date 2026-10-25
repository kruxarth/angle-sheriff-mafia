package com.angelsheriffmafia.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.angelsheriffmafia.app.R
import com.angelsheriffmafia.app.ui.theme.Type
import com.angelsheriffmafia.app.ui.theme.Western
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/* ---------- Backgrounds ---------- */

/** Mustard sunburst used for day screens. */
@Composable
fun SunburstBackground(centerYFraction: Float = 0.7f, content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Western.Mustard)
            .drawBehind {
                val center = Offset(size.width / 2, size.height * centerYFraction)
                val radius = size.maxDimension * 1.5f
                val rays = 26
                val step = 360f / rays
                for (i in 0 until rays) {
                    val a1 = Math.toRadians((i * step).toDouble())
                    val a2 = Math.toRadians((i * step + step / 2).toDouble())
                    val path = Path().apply {
                        moveTo(center.x, center.y)
                        lineTo(center.x + radius * cos(a1).toFloat(), center.y + radius * sin(a1).toFloat())
                        lineTo(center.x + radius * cos(a2).toFloat(), center.y + radius * sin(a2).toFloat())
                        close()
                    }
                    drawPath(path, Western.MustardLight)
                }
            },
        content = content,
    )
}

/** Night sky with fixed stars: quiet and dark so neighbours can't read the screen. */
@Composable
fun NightBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Western.NightTop, Western.Night, Western.NightBottom)))
            .drawBehind {
                // A deterministic scatter so the sky doesn't shimmer between recompositions.
                var seed = 17
                fun next(): Float {
                    seed = (seed * 1103515245 + 12345) and 0x7fffffff
                    return (seed % 10000) / 10000f
                }
                repeat(70) {
                    val x = next() * size.width
                    val y = next() * size.height * 0.75f
                    val r = (0.8f + next() * 1.6f) * density
                    drawCircle(if (next() > 0.3f) Western.Cream else Western.Lavender, r, Offset(x, y), alpha = 0.55f + next() * 0.45f)
                }
            },
    ) {
        // The town asleep along the bottom edge.
        Image(
            painterResource(R.drawable.art_town_night),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.BottomCenter,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp),
        )
        content()
    }
}

/** Wooden plank wall behind role cards. */
@Composable
fun WoodBackground(content: @Composable BoxScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Western.Wood)
            .drawBehind {
                val plank = 62.dp.toPx()
                var x = 0f
                var i = 0
                while (x < size.width) {
                    drawRect(if (i % 2 == 0) Color(0xFF8C5629) else Color(0xFF83491F), Offset(x, 0f), Size(plank, size.height))
                    drawRect(Western.WoodDark, Offset(x + plank - 3.dp.toPx(), 0f), Size(3.dp.toPx(), size.height))
                    x += plank
                    i++
                }
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.Transparent, Western.Ink.copy(alpha = 0.55f)),
                        center = Offset(size.width / 2, size.height * 0.4f),
                        radius = size.maxDimension * 0.75f,
                    ),
                )
            },
        content = content,
    )
}

@Composable
fun Moon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        drawCircle(Western.Cream.copy(alpha = 0.10f), r + 10.dp.toPx())
        drawCircle(Western.Cream, r)
        drawCircle(Color(0xFFD6C48C).copy(alpha = 0.75f), r * 0.18f, Offset(r * 0.68f, r * 0.8f))
        drawCircle(Color(0xFFD6C48C).copy(alpha = 0.7f), r * 0.13f, Offset(r * 1.25f, r * 1.3f))
        drawCircle(Western.Ink, r, style = Stroke(3.dp.toPx()))
    }
}

/* ---------- Labels and cards ---------- */

/** Ribbon-shaped label with notched ends, used for phase names. */
private object RibbonShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val notch = with(density) { 12.dp.toPx() }
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width, 0f)
            lineTo(size.width - notch, size.height / 2)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            lineTo(notch, size.height / 2)
            close()
        })
    }
}

@Composable
fun Ribbon(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RibbonShape)
            .background(Western.Ink)
            .padding(horizontal = 26.dp, vertical = 6.dp),
    ) {
        Text(text.uppercase(), style = Type.label, color = Western.Mustard, maxLines = 1)
    }
}

/** A stamped label, like ink on a poster. */
@Composable
fun Stamp(text: String, modifier: Modifier = Modifier, color: Color = Western.Stamp, angle: Float = -4f) {
    Box(
        modifier
            .rotate(angle)
            .border(3.dp, color)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text.uppercase(), style = Type.label, color = color)
    }
}

/** Offset ink shadow behind a shape, the cartoon look used across buttons and cards. */
fun Modifier.inkShadow(offsetX: Dp = 0.dp, offsetY: Dp = 6.dp, corner: Dp = 10.dp, color: Color = Western.Ink) = drawBehind {
    drawRoundRect(
        color,
        topLeft = Offset(offsetX.toPx(), offsetY.toPx()),
        size = size,
        cornerRadius = CornerRadius(corner.toPx()),
    )
}

/** Weathered paper note. */
@Composable
fun PaperNote(modifier: Modifier = Modifier, angle: Float = -1.2f, content: @Composable () -> Unit) {
    Box(
        modifier
            .rotate(angle)
            .inkShadow(4.dp, 5.dp, 2.dp)
            .background(Western.Paper, RoundedCornerShape(2.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) { content() }
}

/** A speech bubble for the cartoon characters. */
@Composable
fun SpeechBubble(text: String, modifier: Modifier = Modifier, angle: Float = 0f) {
    Box(
        modifier
            .rotate(angle)
            .inkShadow(3.dp, 3.dp, 16.dp)
            .background(Color.White, RoundedCornerShape(16.dp))
            .border(3.dp, Western.Ink, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    ) {
        Text(text, style = Type.title.copy(fontSize = 17.sp), color = Western.Ink)
    }
}

/* ---------- Buttons ---------- */

@Composable
private fun Rivets() {
    Row(Modifier.fillMaxSize().padding(horizontal = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Rivet()
        Spacer(Modifier.weight(1f))
        Rivet()
    }
}

@Composable
private fun Rivet() {
    Box(Modifier.size(8.dp).background(Western.Rivet, CircleShape))
}

/** The main call-to-action button: chunky, ink outline, drop shadow, rivets. */
@Composable
fun BigButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Western.Magenta,
    textColor: Color = Western.Cream,
    enabled: Boolean = true,
    progress: Float? = null,
    height: Dp = 64.dp,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .inkShadow(color = if (enabled) Western.Ink else Color.Black)
            .clip(shape)
            .background(if (enabled) color else Western.NightDim)
            .border(3.dp, Western.Ink, shape)
            .clickable(enabled = enabled, onClick = onClick, role = Role.Button),
        contentAlignment = Alignment.Center,
    ) {
        Rivets()
        val size = when {
            text.length > 22 -> 16.sp
            text.length > 17 -> 18.sp
            else -> 21.sp
        }
        Text(
            text.uppercase(),
            style = Type.button.copy(fontSize = size),
            maxLines = 1,
            color = if (enabled) textColor else Color(0xFFBDB8EC),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        if (progress != null) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(7.dp)
                    .background(Western.Mustard),
            )
        }
    }
}

/** Quiet text button for secondary actions. Still a 48dp touch target. */
@Composable
fun LinkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Western.Ink) {
    Box(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick, role = Role.Button)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Type.bodyStrong.copy(fontSize = 16.sp), color = color, textDecoration = TextDecoration.Underline)
    }
}

/** A name painted on a wooden plank. Used by every night pick and vote grid. */
@Composable
fun PlankButton(
    name: String,
    selected: Boolean,
    enabled: Boolean,
    dead: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: String? = null,
) {
    val shape = RoundedCornerShape(8.dp)
    Box(modifier.height(62.dp)) {
        val base = Modifier
            .fillMaxSize()
            .let { if (dead) it.rotate(-2.5f) else it }
        val styled = when {
            dead -> base
                .clip(shape)
                .background(Western.Night.copy(alpha = 0.4f))
                .border(2.dp, Color(0xFF57549A), shape)
            selected -> base
                .drawBehind {
                    drawRoundRect(Western.Mustard.copy(alpha = 0.35f), Offset(-6.dp.toPx(), -6.dp.toPx()), Size(size.width + 12.dp.toPx(), size.height + 12.dp.toPx()), CornerRadius(14.dp.toPx()))
                }
                .inkShadow(offsetY = 5.dp, corner = 8.dp)
                .clip(shape)
                .background(Western.Mustard)
                .border(3.dp, Western.Ink, shape)
            else -> base
                .inkShadow(offsetY = 5.dp, corner = 8.dp)
                .clip(shape)
                .background(if (enabled) Western.Wood else Color(0xFF5E3A20))
                .drawBehind {
                    var y = 10.dp.toPx()
                    while (y < size.height) {
                        drawLine(Color(0x52391C0A), Offset(0f, y), Offset(size.width, y), 2.dp.toPx())
                        y += 21.dp.toPx()
                    }
                }
                .border(3.dp, Western.Ink, shape)
        }
        Box(
            styled
                .clickable(enabled = enabled && !dead, onClick = onClick, role = Role.RadioButton)
                .semantics { contentDescription = name + if (dead) ", out of the game" else if (selected) ", selected" else "" },
            contentAlignment = Alignment.Center,
        ) {
            if (!dead) Rivets()
            Text(
                name.uppercase(),
                style = Type.plank.copy(
                    fontSize = when {
                        name.length > 11 -> 14.sp
                        name.length > 8 -> 16.sp
                        else -> Type.plank.fontSize
                    },
                ),
                overflow = TextOverflow.Ellipsis,
                color = when {
                    dead -> Western.LavenderDim
                    selected -> Western.Ink
                    enabled -> Western.Cream
                    else -> Western.Cream.copy(alpha = 0.45f)
                },
                textDecoration = if (dead) TextDecoration.LineThrough else null,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
        }
        if (tag != null && !dead) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = (-10).dp)
                    .background(Western.Stamp, RoundedCornerShape(4.dp))
                    .border(2.dp, Western.Ink, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(tag.uppercase(), style = Type.label.copy(fontSize = 10.sp), color = Western.Cream)
            }
        }
    }
}

/** Two-column name grid. */
@Composable
fun <T> PlankGrid(items: List<T>, modifier: Modifier = Modifier, cell: @Composable (T, Modifier) -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        items.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { cell(it, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/* ---------- Press-and-hold ---------- */

/** Tracks whether a finger is down on this element. */
fun Modifier.pressState(onPressChange: (Boolean) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        onPressChange(true)
        waitForUpOrCancellation()
        onPressChange(false)
    }
}

/**
 * A button that fires only after being held down, so a nudge or a bump of the
 * phone can't open a secret screen.
 */
@Composable
fun HoldButton(
    title: String,
    subtitle: String,
    onHeld: () -> Unit,
    modifier: Modifier = Modifier,
    holdMillis: Int = 700,
) {
    var pressed by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    val currentOnHeld by rememberUpdatedState(onHeld)
    LaunchedEffect(pressed) {
        if (pressed) {
            progress.animateTo(1f, tween(holdMillis, easing = LinearEasing))
            currentOnHeld()
        } else {
            progress.snapTo(0f)
        }
    }
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .inkShadow(color = Color.Black)
            .clip(shape)
            .background(Western.Mustard)
            .drawBehind { drawRect(Western.MustardLight, size = Size(size.width * progress.value, size.height)) }
            .border(3.dp, Western.Ink, shape)
            .pressState { pressed = it }
            .semantics {
                role = Role.Button
                contentDescription = "$title. $subtitle"
                onClick { currentOnHeld(); true }
            },
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Fingerprint(40.dp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title.uppercase(), style = Type.button.copy(fontSize = 20.sp), color = Western.Ink, maxLines = 1)
                Text(subtitle, style = Type.body.copy(fontSize = 14.sp, lineHeight = 17.sp), color = Western.Ink)
            }
        }
    }
}

/**
 * Round thumb pad that reports whether it's held. Secret content shows only while
 * the thumb is down, so a glance over a shoulder catches nothing.
 */
@Composable
fun ThumbPad(label: String, hint: String, onPressChange: (Boolean) -> Unit, modifier: Modifier = Modifier, light: Boolean = true) {
    var pressed by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(80.dp)
                .drawBehind {
                    drawCircle(Western.Mustard.copy(alpha = if (pressed) 0.5f else 0.3f), size.minDimension / 2 + 8.dp.toPx())
                }
                .clip(CircleShape)
                .background(if (pressed) Western.MustardLight else Western.Mustard)
                .border(3.dp, Western.Ink, CircleShape)
                .pressState {
                    pressed = it
                    onPressChange(it)
                }
                // With TalkBack, a double tap shows the secret and another hides it.
                .semantics {
                    role = Role.Button
                    contentDescription = "$label. $hint"
                    onClick {
                        pressed = !pressed
                        onPressChange(pressed)
                        true
                    }
                },
            contentAlignment = Alignment.Center,
        ) { Fingerprint(44.dp) }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = Type.button.copy(fontSize = 19.sp), color = if (light) Western.Cream else Western.Ink)
            Text(hint, style = Type.body.copy(fontSize = 14.5.sp, lineHeight = 19.sp), color = if (light) Color(0xFFF6E2C4) else Western.Ink)
        }
    }
}

@Composable
fun Fingerprint(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val stroke = Stroke(w * 0.06f, cap = StrokeCap.Round)
        val c = Offset(w / 2, w * 0.52f)
        // Nested loops that open at different angles read as a thumbprint.
        for (i in 0..4) {
            val rx = w * (0.07f + 0.085f * i)
            val ry = rx * 1.22f
            drawArc(
                Western.Ink,
                startAngle = 115f + i * 9f,
                sweepAngle = 310f - i * 28f,
                useCenter = false,
                topLeft = Offset(c.x - rx, c.y - ry),
                size = Size(rx * 2, ry * 2),
                style = stroke,
            )
        }
    }
}

/** Five-point star, filled or outlined. Used for turn progress. */
@Composable
fun Star(size: Dp, fill: Color?, outline: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2
        val inner = r * 0.45f
        val path = Path()
        for (i in 0 until 10) {
            val rad = if (i % 2 == 0) r else inner
            val a = -PI / 2 + i * PI / 5
            val p = Offset(r + rad * cos(a).toFloat(), r + rad * sin(a).toFloat())
            if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
        }
        path.close()
        if (fill != null) drawPath(path, fill)
        drawPath(path, outline, style = Stroke(r * 0.16f))
    }
}

@Composable
fun TurnStars(current: Int, total: Int, modifier: Modifier = Modifier) {
    Row(
        modifier.semantics { contentDescription = "Turn ${current + 1} of $total" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until total) {
            when {
                i < current -> Star(13.dp, Color(0xFF8F8AC9), Color(0xFF8F8AC9))
                i == current -> Star(18.dp, Western.Mustard, Western.Ink)
                else -> Star(13.dp, null, Color(0xFF8F8AC9))
            }
        }
    }
}

/** Speaker glyph for "hear it again". */
@Composable
fun SpeakerIcon(size: Dp, color: Color = Western.Ink) {
    Canvas(Modifier.size(size)) {
        val s = this.size.width / 24f
        val stroke = Stroke(2.2f * s, cap = StrokeCap.Round)
        val body = Path().apply {
            moveTo(4 * s, 9 * s); lineTo(8 * s, 9 * s); lineTo(13 * s, 5 * s); lineTo(13 * s, 19 * s)
            lineTo(8 * s, 15 * s); lineTo(4 * s, 15 * s); close()
        }
        drawPath(body, color, style = stroke)
        drawArc(color, -50f, 100f, false, Offset(12 * s, 8 * s), Size(8 * s, 8 * s), style = stroke)
        drawArc(color, -55f, 110f, false, Offset(11 * s, 5 * s), Size(13 * s, 14 * s), style = stroke)
    }
}
