package pe.registroacademico.nativo.ui.consultas.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import pe.registroacademico.nativo.domain.DateUtils
import pe.registroacademico.nativo.domain.PorGradoItem
import pe.registroacademico.nativo.domain.PorNivelItem
import pe.registroacademico.nativo.domain.SerieDiariaPunto

val ColorTeal = Color(0xFF00897B)
val ColorAmber = Color(0xFFF57F17)
val ColorRed = Color(0xFFD32F2F)
val ColorNavy = Color(0xFF1E3A8A)
val ColorPurple = Color(0xFF7E57C2)
val ColorPalette = listOf(ColorNavy, ColorAmber, ColorTeal, ColorRed, ColorPurple, Color(0xFF00ACC1))

@Composable
fun GraficoTendencia(
    serie: List<SerieDiariaPunto>,
    modifier: Modifier = Modifier
) {
    if (serie.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(180.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin datos de tendencia",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val textMeasurer = rememberTextMeasurer()
    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    val textStyle = TextStyle(
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            val width = size.width
            val height = size.height
            val bottomPadding = 24.dp.toPx()
            val topPadding = 20.dp.toPx()
            val leftPadding = 8.dp.toPx()
            val rightPadding = 32.dp.toPx()

            val chartWidth = width - leftPadding - rightPadding
            val chartHeight = height - topPadding - bottomPadding

            if (chartWidth <= 0 || chartHeight <= 0) return@Canvas

            // Max count of students to scale stacked bars
            val maxCount = maxOf(serie.maxOfOrNull { it.presentes } ?: 1, 1).toFloat()
            val stepX = chartWidth / serie.size

            // Draw grid line for 0 and 100% on right axis
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, topPadding),
                end = Offset(width - rightPadding, topPadding),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = gridColor,
                start = Offset(leftPadding, height - bottomPadding),
                end = Offset(width - rightPadding, height - bottomPadding),
                strokeWidth = 1.dp.toPx()
            )

            // Draw 100% and 0% text on right
            val text100 = textMeasurer.measure("100%", textStyle)
            drawText(
                textLayoutResult = text100,
                topLeft = Offset(width - rightPadding + 4.dp.toPx(), topPadding - text100.size.height / 2f)
            )
            val text0 = textMeasurer.measure("0%", textStyle)
            drawText(
                textLayoutResult = text0,
                topLeft = Offset(width - rightPadding + 4.dp.toPx(), height - bottomPadding - text0.size.height / 2f)
            )

            val barWidth = minOf(stepX * 0.5f, 24.dp.toPx())
            val linePoints = mutableListOf<Offset>()

            serie.forEachIndexed { i, punto ->
                val centerX = leftPadding + (i * stepX) + (stepX / 2f)
                val puntuales = punto.presentes - punto.tardes
                val tardes = punto.tardes

                // Stacked bar heights
                val totalPresentesHeight = (punto.presentes / maxCount) * chartHeight
                val puntualesHeight = if (punto.presentes > 0) (puntuales.toFloat() / punto.presentes) * totalPresentesHeight else 0f
                val tardesHeight = totalPresentesHeight - puntualesHeight

                val barLeft = centerX - (barWidth / 2f)
                val barBottom = height - bottomPadding

                // Draw Puntuales (Teal) at bottom
                if (puntualesHeight > 0) {
                    drawRoundRect(
                        color = ColorTeal,
                        topLeft = Offset(barLeft, barBottom - puntualesHeight),
                        size = Size(barWidth, puntualesHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // Draw Tardanzas (Amber) on top
                if (tardesHeight > 0) {
                    drawRoundRect(
                        color = ColorAmber,
                        topLeft = Offset(barLeft, barBottom - totalPresentesHeight),
                        size = Size(barWidth, tardesHeight),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // Line point for % asistencia (scale 0..100 to chartHeight)
                val pctNormalized = punto.pct.coerceIn(0, 100) / 100f
                val lineY = height - bottomPadding - (pctNormalized * chartHeight)
                linePoints.add(Offset(centerX, lineY))

                // Date label at bottom
                val dayLabel = DateUtils.fmtDay(punto.fecha)
                val measuredDate = textMeasurer.measure(dayLabel, textStyle)
                drawText(
                    textLayoutResult = measuredDate,
                    topLeft = Offset(centerX - (measuredDate.size.width / 2f), height - bottomPadding + 6.dp.toPx())
                )
            }

            // Draw line chart connecting percentages
            if (linePoints.size > 1) {
                val path = Path()
                linePoints.forEachIndexed { idx, pt ->
                    if (idx == 0) path.moveTo(pt.x, pt.y)
                    else path.lineTo(pt.x, pt.y)
                }
                drawPath(
                    path = path,
                    color = ColorNavy,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // Draw points on the line
            linePoints.forEach { pt ->
                drawCircle(
                    color = Color.White,
                    radius = 4.dp.toPx(),
                    center = pt
                )
                drawCircle(
                    color = ColorNavy,
                    radius = 3.dp.toPx(),
                    center = pt
                )
            }
        }

        // Leyenda
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemLeyenda(color = ColorTeal, etiqueta = "Puntuales")
            Spacer(modifier = Modifier.width(16.dp))
            ItemLeyenda(color = ColorAmber, etiqueta = "Tardanzas")
            Spacer(modifier = Modifier.width(16.dp))
            ItemLeyenda(color = ColorNavy, etiqueta = "% Asistencia")
        }
    }
}

@Composable
fun GraficoEstadoHoy(
    puntuales: Int,
    tardes: Int,
    ausentes: Int,
    pct: Int,
    modifier: Modifier = Modifier
) {
    val total = puntuales + tardes + ausentes
    val textMeasurer = rememberTextMeasurer()
    val primaryTextColor = MaterialTheme.colorScheme.onSurface
    val secondaryTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(170.dp)
                .padding(12.dp)
        ) {
            val strokeWidth = 24.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)

            if (total == 0) {
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            } else {
                var currentAngle = -90f

                val sweepP = (puntuales.toFloat() / total) * 360f
                if (sweepP > 0) {
                    drawArc(
                        color = ColorTeal,
                        startAngle = currentAngle,
                        sweepAngle = sweepP,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    currentAngle += sweepP
                }

                val sweepT = (tardes.toFloat() / total) * 360f
                if (sweepT > 0) {
                    drawArc(
                        color = ColorAmber,
                        startAngle = currentAngle,
                        sweepAngle = sweepT,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                    currentAngle += sweepT
                }

                val sweepA = (ausentes.toFloat() / total) * 360f
                if (sweepA > 0) {
                    drawArc(
                        color = ColorRed,
                        startAngle = currentAngle,
                        sweepAngle = sweepA,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                }
            }

            // Centro: % y etiqueta "Presentes"
            val textPct = textMeasurer.measure(
                text = "$pct%",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryTextColor
                )
            )
            drawText(
                textLayoutResult = textPct,
                topLeft = Offset(center.x - textPct.size.width / 2f, center.y - textPct.size.height / 2f - 8.dp.toPx())
            )

            val textSub = textMeasurer.measure(
                text = "Presentes",
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = secondaryTextColor
                )
            )
            drawText(
                textLayoutResult = textSub,
                topLeft = Offset(center.x - textSub.size.width / 2f, center.y + 10.dp.toPx())
            )
        }

        // Leyenda
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ItemLeyenda(color = ColorTeal, etiqueta = "Puntuales: $puntuales")
            ItemLeyenda(color = ColorAmber, etiqueta = "Tardanzas: $tardes")
            ItemLeyenda(color = ColorRed, etiqueta = "Ausentes: $ausentes")
        }
    }
}

@Composable
fun GraficoPorGrado(
    grados: List<PorGradoItem>,
    metaPct: Int = 85,
    modifier: Modifier = Modifier
) {
    if (grados.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(100.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin datos de ciclos para hoy",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        grados.forEach { item ->
            val barColor = when {
                item.pct >= metaPct -> ColorTeal
                item.pct >= 60 -> ColorAmber
                else -> ColorRed
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.key,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${item.pct}% (${item.presentes}/${item.total})",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                ) {
                    // Barra con guía de meta
                    Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                        val trackHeight = size.height
                        val totalW = size.width

                        // Fondo track
                        drawRoundRect(
                            color = Color.LightGray.copy(alpha = 0.25f),
                            size = Size(totalW, trackHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )

                        // Barra de progreso
                        val barW = (item.pct.coerceIn(0, 100) / 100f) * totalW
                        if (barW > 0) {
                            drawRoundRect(
                                color = barColor,
                                size = Size(barW, trackHeight),
                                cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                            )
                        }

                        // Línea guía de meta (85%)
                        val metaX = (metaPct / 100f) * totalW
                        drawLine(
                            color = ColorAmber,
                            start = Offset(metaX, 0f),
                            end = Offset(metaX, trackHeight),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GraficoAlumnosPorCarrera(
    niveles: List<PorNivelItem>,
    modifier: Modifier = Modifier
) {
    if (niveles.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(140.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Sin carreras configuradas",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val total = niveles.sumOf { it.total }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Canvas(
            modifier = Modifier
                .size(150.dp)
                .padding(10.dp)
        ) {
            val strokeWidth = 22.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val arcSize = Size(radius * 2, radius * 2)
            val topLeft = Offset(center.x - radius, center.y - radius)

            if (total == 0) {
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.4f),
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
            } else {
                var startAngle = -90f
                niveles.forEachIndexed { index, item ->
                    val sweep = (item.total.toFloat() / total) * 360f
                    if (sweep > 0) {
                        val color = ColorPalette[index % ColorPalette.size]
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweep
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            niveles.forEachIndexed { index, item ->
                val color = ColorPalette[index % ColorPalette.size]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(color, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${item.nivel}: ${item.total}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ItemLeyenda(
    color: Color,
    etiqueta: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = etiqueta,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
