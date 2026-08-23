package com.kumo.bubu.feature.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.ReportCostCategory
import com.kumo.bubu.domain.model.ReportFuelEconomyPoint
import com.kumo.bubu.domain.model.ReportSource
import com.kumo.bubu.domain.model.toFuelEconomyDisplayText
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
internal fun FuelEconomyChart(report: ReportPresentation, onOpenSource: (ReportSource) -> Unit) {
    val fuel = report.fuelEconomy
    var selectedPoint by remember { mutableStateOf<ReportFuelEconomyPoint?>(null) }
    LaunchedEffect(fuel.points) { selectedPoint = null }
    ChartCard(title = stringResource(R.string.reports_fuel_economy)) {
        when (fuel.points.size) {
            0 -> ReportsMessage(R.string.reports_insufficient_fuel)
            1 -> {
                val point = fuel.points.single()
                Text(
                    text = stringResource(
                        R.string.reports_current_fuel_economy,
                        point.milliKmPerLiter.toFuelEconomyDisplayText(),
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Text(
                    text = stringResource(R.string.reports_fuel_trend_needs_one_more),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            else -> {
                FuelEconomyStats(fuel.points, fuel.averageMilliKmPerLiter)
                FuelEconomyLineChart(
                    points = fuel.points,
                    startEpochDay = fuel.trendStartEpochDay,
                    endEpochDay = fuel.trendEndEpochDay,
                    onPointSelected = { selectedPoint = it },
                )
                Text(
                    text = stringResource(
                        R.string.reports_fuel_trend_period,
                        LocalDate.ofEpochDay(fuel.trendStartEpochDay).toMonthLabel(),
                        LocalDate.ofEpochDay(fuel.trendEndEpochDay).toMonthLabel(),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                )
                selectedPoint?.let { point ->
                    TextButton(
                        onClick = { onOpenSource(ReportSource.Fuel(point.fuelRecordId)) },
                        modifier = Modifier.testTag("reports-selected-fuel-point"),
                    ) {
                        Text(
                            stringResource(
                                R.string.reports_fuel_economy_point,
                                LocalDate.ofEpochDay(point.dateEpochDay).toString(),
                                point.milliKmPerLiter.toFuelEconomyDisplayText(),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FuelEconomyStats(points: List<ReportFuelEconomyPoint>, average: Long?) {
    val latest = points.last()
    val difference = latest.milliKmPerLiter - points[points.lastIndex - 1].milliKmPerLiter
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        FuelStat(R.string.reports_average_fuel_economy, average?.toFuelEconomyDisplayText() ?: "--")
        FuelStat(R.string.reports_latest_fuel_economy, latest.milliKmPerLiter.toFuelEconomyDisplayText())
        FuelStat(R.string.reports_fuel_economy_change, difference.toSignedFuelEconomyDisplayText())
    }
}

@Composable
private fun FuelStat(labelRes: Int, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.tertiary,
            fontWeight = FontWeight.SemiBold,
        )
        Text(stringResource(labelRes), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FuelEconomyLineChart(
    points: List<ReportFuelEconomyPoint>,
    startEpochDay: Long,
    endEpochDay: Long,
    onPointSelected: (ReportFuelEconomyPoint) -> Unit,
) {
    val values = points.map(ReportFuelEconomyPoint::milliKmPerLiter)
    val minimum = values.minOrNull() ?: return
    val maximum = values.maxOrNull() ?: return
    val lineColor = MaterialTheme.colorScheme.tertiary
    val pointColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp)
            .pointerInput(points) {
                detectTapGestures { position ->
                    onPointSelected(points.minBy { point ->
                        kotlin.math.abs(position.x - point.xForTrend(size.width.toFloat(), startEpochDay, endEpochDay))
                    })
                }
            }
            .testTag("reports-fuel-economy-line-chart"),
    ) {
        fun yFor(value: Long): Float = if (maximum == minimum) {
            size.height / 2f
        } else {
            size.height - ((value - minimum).toFloat() / (maximum - minimum).toFloat() * size.height)
        }
        val path = Path()
        points.forEachIndexed { index, point ->
            val x = point.xForTrend(size.width, startEpochDay, endEpochDay)
            val y = yFor(point.milliKmPerLiter)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path = path, color = lineColor, style = Stroke(width = 6f, cap = StrokeCap.Round))
        points.forEachIndexed { index, point ->
            drawCircle(
                color = pointColor,
                radius = 8f,
                center = androidx.compose.ui.geometry.Offset(
                    point.xForTrend(size.width, startEpochDay, endEpochDay),
                    yFor(point.milliKmPerLiter),
                ),
            )
        }
    }
}

private enum class CostSeries { ALL, FUEL, SERVICE }

@Composable
internal fun MonthlyCostChart(report: ReportPresentation, onEvent: (ReportsEvent) -> Unit) {
    var selectedSeries by rememberSaveable { mutableStateOf(CostSeries.ALL) }
    var selectedMonthKey by rememberSaveable { mutableStateOf<String?>(null) }
    val points = report.monthlyTotals.map { point ->
        point.monthKey to when (selectedSeries) {
            CostSeries.ALL -> point.totalCostTwd
            CostSeries.FUEL -> point.fuelCostTwd
            CostSeries.SERVICE -> point.serviceCostTwd
        }
    }
    ChartCard(title = stringResource(R.string.reports_total_cost)) {
        Text(
            text = currency(report.totalCostTwd),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = stringResource(R.string.reports_monthly_average, currency(report.monthlyAverageCostTwd)),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.reports_monthly_cost), style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CostSeries.entries.forEach { series ->
                FilterChip(
                    selected = selectedSeries == series,
                    onClick = { selectedSeries = series },
                    label = { Text(stringResource(series.labelRes())) },
                    modifier = Modifier.testTag("reports-cost-series-${series.name.lowercase()}"),
                )
            }
        }
        if (points.isEmpty()) {
            ReportsMessage(R.string.reports_no_data)
        } else {
            MonthlyCostBarChart(points) { selectedMonthKey = it }
            MonthlyCostLabels(points)
            points.firstOrNull { it.first == selectedMonthKey }?.let { (monthKey, amount) ->
                Text(stringResource(R.string.reports_month_value, monthKey.toShortMonthLabel(), currency(amount)))
            }
        }
    }
}

@Composable
private fun MonthlyCostBarChart(points: List<Pair<String, Long>>, onPointSelected: (String) -> Unit) {
    val maximum = points.maxOf { it.second }.coerceAtLeast(1L)
    val barColor = MaterialTheme.colorScheme.secondary
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(132.dp)
            .pointerInput(points) {
                detectTapGestures { position ->
                    val width = size.width / points.size.toFloat()
                    onPointSelected(points[(position.x / width).toInt().coerceIn(0, points.lastIndex)].first)
                }
            }
            .testTag("reports-monthly-cost-chart"),
    ) {
        val barWidth = size.width / (points.size * 2f)
        points.forEachIndexed { index, (_, amount) ->
            val height = size.height * amount.toFloat() / maximum.toFloat()
            val left = barWidth * (index * 2 + 0.5f)
            drawRoundRect(
                color = barColor,
                topLeft = androidx.compose.ui.geometry.Offset(left, size.height - height),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
            )
        }
    }
}

@Composable
private fun MonthlyCostLabels(points: List<Pair<String, Long>>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        points.forEach { (monthKey, _) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                Text(monthKey.toShortMonthLabel(), style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
internal fun CostPerKmChart(report: ReportPresentation) {
    val cost = report.costPerKm
    ChartCard(title = stringResource(R.string.reports_cost_per_km)) {
        cost.milliTwdPerKm?.let { value ->
            Text(
                text = stringResource(
                    R.string.reports_cost_per_km_value,
                    BigDecimal.valueOf(value, 3).setScale(2, RoundingMode.HALF_UP).toPlainString(),
                ),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            cost.distanceKm?.let { distance ->
                Text(
                    text = stringResource(R.string.reports_distance_in_period, distance),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } ?: ReportsMessage(R.string.reports_insufficient_distance)
    }
}

@Composable
internal fun CategoryChart(report: ReportPresentation) {
    ChartCard(title = stringResource(R.string.reports_category_breakdown)) {
        val maximum = report.categoryTotals.values.maxOrNull()?.takeIf { it > 0 } ?: 1L
        ReportCostCategory.entries.forEach { category ->
            BarRow(stringResource(category.labelRes()), report.categoryTotals[category] ?: 0L, maximum)
        }
    }
}

@Composable
internal fun ServiceCostChart(report: ReportPresentation) {
    ChartCard(title = stringResource(R.string.reports_service_cost)) {
        if (report.serviceMonthlyTotals.isEmpty()) {
            ReportsMessage(R.string.reports_no_data)
        } else {
            val maximum = report.serviceMonthlyTotals.maxOf { maxOf(it.maintenanceCostTwd, it.repairCostTwd) }
                .coerceAtLeast(1L)
            report.serviceMonthlyTotals.forEach { point ->
                BarRow(
                    stringResource(R.string.reports_service_category_label, point.monthKey, stringResource(R.string.reports_category_maintenance)),
                    point.maintenanceCostTwd,
                    maximum,
                )
                BarRow(
                    stringResource(R.string.reports_service_category_label, point.monthKey, stringResource(R.string.reports_category_repair)),
                    point.repairCostTwd,
                    maximum,
                )
            }
        }
    }
}

@Composable
internal fun MileageChart(report: ReportPresentation, onOpenSource: (ReportSource) -> Unit) {
    ChartCard(title = stringResource(R.string.reports_mileage)) {
        if (report.mileage.points.isEmpty()) {
            ReportsMessage(R.string.reports_no_data)
        } else {
            report.mileage.points.forEach { point ->
                TextButton(onClick = { onOpenSource(point.source) }) {
                    Text(stringResource(R.string.reports_mileage_value, LocalDate.ofEpochDay(point.dateEpochDay).toString(), point.odometerKm))
                }
            }
        }
    }
}

@Composable
internal fun ChartCard(title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
            content()
        }
    }
}

@Composable
private fun BarRow(label: String, amount: Long, maximum: Long) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, modifier = Modifier.widthIn(max = 180.dp), style = MaterialTheme.typography.bodySmall)
            Text(currency(amount), style = MaterialTheme.typography.bodySmall)
        }
        LinearProgressIndicator(progress = { amount.toFloat() / maximum.toFloat() }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun currency(amount: Long): String = stringResource(R.string.reports_currency, amount)

private fun CostSeries.labelRes(): Int = when (this) {
    CostSeries.ALL -> R.string.reports_cost_series_all
    CostSeries.FUEL -> R.string.reports_cost_series_fuel
    CostSeries.SERVICE -> R.string.reports_cost_series_service
}

private fun ReportCostCategory.labelRes(): Int = when (this) {
    ReportCostCategory.FUEL -> R.string.reports_category_fuel
    ReportCostCategory.MAINTENANCE -> R.string.reports_category_maintenance
    ReportCostCategory.REPAIR -> R.string.reports_category_repair
    ReportCostCategory.LICENSE_TAX -> R.string.reports_category_license_tax
    ReportCostCategory.ROAD_MAINTENANCE_FEE -> R.string.reports_category_road_fee
    ReportCostCategory.INSURANCE -> R.string.reports_category_insurance
    ReportCostCategory.OTHER -> R.string.reports_category_other
}

private fun Long.toSignedFuelEconomyDisplayText(): String {
    val value = BigDecimal.valueOf(this, 3).setScale(1, RoundingMode.HALF_UP).toPlainString()
    return if (this > 0) "+$value" else value
}

private fun String.toShortMonthLabel(): String = substringAfter('-').trimStart('0') + "月"

private fun ReportFuelEconomyPoint.xForTrend(width: Float, startEpochDay: Long, endEpochDay: Long): Float {
    if (endEpochDay <= startEpochDay) return width / 2f
    return width * ((dateEpochDay - startEpochDay).toFloat() / (endEpochDay - startEpochDay).toFloat())
        .coerceIn(0f, 1f)
}

private fun LocalDate.toMonthLabel(): String = "${year}/${monthValue.toString().padStart(2, '0')}"
