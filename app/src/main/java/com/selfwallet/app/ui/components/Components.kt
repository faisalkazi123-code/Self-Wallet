package com.selfwallet.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Checkroom
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.selfwallet.app.data.db.SpendKind
import com.selfwallet.app.ui.TxUi
import com.selfwallet.app.ui.theme.CorporateBlue
import com.selfwallet.app.ui.theme.ExpenseRed
import com.selfwallet.app.ui.theme.IncomeGreen
import com.selfwallet.app.util.Currencies

object CategoryIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "food" to Icons.Filled.Restaurant,
        "coffee" to Icons.Filled.Coffee,
        "grocery" to Icons.Filled.LocalGroceryStore,
        "car" to Icons.Filled.DirectionsCar,
        "bus" to Icons.Filled.DirectionsBus,
        "fuel" to Icons.Filled.LocalGasStation,
        "shopping" to Icons.Filled.ShoppingBag,
        "clothes" to Icons.Filled.Checkroom,
        "bill" to Icons.AutoMirrored.Filled.ReceiptLong,
        "power" to Icons.Filled.Bolt,
        "phone" to Icons.Filled.PhoneAndroid,
        "movie" to Icons.Filled.Movie,
        "games" to Icons.Filled.SportsEsports,
        "health" to Icons.Filled.LocalHospital,
        "fitness" to Icons.Filled.FitnessCenter,
        "flight" to Icons.Filled.Flight,
        "hotel" to Icons.Filled.Hotel,
        "home" to Icons.Filled.Home,
        "school" to Icons.Filled.School,
        "work" to Icons.Filled.Work,
        "cash" to Icons.Filled.Payments,
        "bank" to Icons.Filled.AccountBalance,
        "gift" to Icons.Filled.CardGiftcard,
        "pets" to Icons.Filled.Pets,
        "salary" to Icons.Filled.AttachMoney,
        "savings" to Icons.Filled.Savings,
        "invest" to Icons.AutoMirrored.Filled.TrendingUp,
        "refund" to Icons.Filled.Replay,
        "other" to Icons.Filled.Category,
    )
    private val map = all.toMap()
    fun of(key: String?): ImageVector = map[key] ?: Icons.Filled.Category
}

val Palette: List<Long> = listOf(
    0xFF0F766E, 0xFF14B8A6, 0xFF22C55E, 0xFF84CC16, 0xFFEAB308, 0xFFF97316,
    0xFFEF4444, 0xFFE11D48, 0xFFEC4899, 0xFFA855F7, 0xFF8B5CF6, 0xFF6366F1,
    0xFF3B82F6, 0xFF2563EB, 0xFF0EA5E9, 0xFF06B6D4, 0xFF64748B, 0xFFA16207,
)

fun Long.asColor() = Color(this.toInt())

@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 44.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
fun AmountText(
    amount: Double,
    currency: String,
    isExpense: Boolean,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
) {
    val text = if (hidden) "••••" else Currencies.format(if (isExpense) -amount else amount, currency, showSign = !isExpense)
    Text(
        text,
        modifier = modifier,
        style = style,
        color = if (isExpense) MaterialTheme.colorScheme.onSurface else IncomeGreen,
        maxLines = 1,
    )
}

fun money(amount: Double, currency: String, hidden: Boolean) = if (hidden) "••••" else Currencies.format(amount, currency)

@Composable
fun KindPill(kind: String, modifier: Modifier = Modifier) {
    val corporate = kind == SpendKind.CORPORATE
    val color = if (corporate) CorporateBlue else MaterialTheme.colorScheme.primary
    Text(
        if (corporate) "Corporate" else "Personal",
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelSmall,
        color = color,
    )
}

@Composable
fun TransactionRow(item: TxUi, hidden: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = item.tx
    val color = item.category?.color?.asColor() ?: MaterialTheme.colorScheme.outline
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(CategoryIcons.of(item.category?.icon), color)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.merchant.ifBlank { item.category?.name ?: "Transaction" },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (t.source == "SMS") {
                    Icon(
                        Icons.Filled.Sms, null,
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                }
                val sub = buildList {
                    add(item.category?.name ?: "Uncategorised")
                    item.allocation?.let { add(it.name) }
                }.joinToString(" · ")
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (t.needsReview) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            AmountText(t.amount, t.currency, item.isExpense, hidden)
            if (item.isCorporate) {
                Spacer(Modifier.height(2.dp))
                KindPill(SpendKind.CORPORATE)
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            action()
        }
    }
}

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, tonalElevation = 0.dp) { content() }
    }
}

/** 0 -> 1 animation that plays once when the composable enters. */
@Composable
fun rememberEntrance(durationMs: Int): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(Unit) { anim.animateTo(1f, tween(durationMs, easing = FastOutSlowInEasing)) }
    return anim.value
}

data class Slice(val label: String, val value: Double, val color: Color)

@Composable
fun DonutChart(slices: List<Slice>, modifier: Modifier = Modifier, thickness: Dp = 22.dp, center: @Composable () -> Unit = {}) {
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    val progress = rememberEntrance(700)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = thickness.toPx()
            val d = size.minDimension - stroke
            val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
            drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
            var start = -90f
            val gap = if (slices.size > 1) 2.5f else 0f
            slices.forEach { s ->
                val sweep = (s.value / total * 360f).toFloat() * progress
                if (sweep > gap) {
                    drawArc(s.color, start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
                }
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun BarChart(
    values: List<Double>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    highlight: Int = -1,
    barColor: Color = MaterialTheme.colorScheme.primary,
) {
    val max = (values.maxOrNull() ?: 0.0).takeIf { it > 0 } ?: 1.0
    val progress = rememberEntrance(600)
    val muted = barColor.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    Column(modifier) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            val n = values.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.6f).coerceAtMost(28.dp.toPx())
            values.forEachIndexed { i, v ->
                val h = (v / max).toFloat() * size.height * progress
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2, barW / 2))
                if (h > 0f) {
                    drawRoundRect(
                        if (highlight < 0 || i == highlight) barColor else muted,
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2, barW / 2),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach {
                Text(
                    it,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                )
            }
        }
    }
}

@Composable
fun ProgressLine(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(600), label = "line")
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box(
            Modifier
                .fillMaxWidth(f)
                .height(6.dp)
                .clip(CircleShape)
                .background(color),
        )
    }
}

val ExpenseColor = ExpenseRed
