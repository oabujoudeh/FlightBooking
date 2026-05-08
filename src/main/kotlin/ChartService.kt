package com.flightbooking

import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.CategoryChartBuilder
import org.knowm.xchart.PieChartBuilder
import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.style.Styler
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Service for generating admin dashboard charts as PNG byte arrays.
 *
 * Uses the XChart library to build and encode each chart. All functions
 * return the rendered image as a [ByteArray] suitable for serving directly
 * as an HTTP response or embedding in a page.
 */
object ChartService {
    /**
     * Generates a dual-series XY line chart showing booking counts and revenue over time.
     *
     * The X axis represents dates parsed from the `"date"` key in each data entry.
     * Two series are plotted: `"Bookings"` (count per date) and `"Revenue"` (total
     * revenue per date). The chart is rendered at 800×400 pixels.
     *
     * @param data list of maps, each containing:
     *        `"date"` (a `"yyyy-MM-dd"` string), `"count"` (an `Int`), and `"revenue"` (a `Double`)
     * @return the rendered chart as a PNG [ByteArray]
     */
    fun generateBookingsOverTimeChart(data: List<Map<String, Any>>): ByteArray {
        val dates =
            data.map {
                val localDate = LocalDate.parse(it["date"] as String)
                Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            }
        val counts = data.map { (it["count"] as Int).toDouble() }
        val revenue = data.map { it["revenue"] as Double }

        val chart =
            XYChartBuilder()
                .width(800)
                .height(400)
                .title("Bookings Over Time")
                .xAxisTitle("Date")
                .yAxisTitle("Revenue")
                .build()

        chart.styler.legendPosition = Styler.LegendPosition.InsideNW
        chart.styler.isXAxisTicksVisible = true
        chart.styler.xAxisLabelRotation = 45
        chart.styler.chartBackgroundColor = Color.WHITE
        chart.styler.plotBackgroundColor = Color.WHITE
        chart.styler.isPlotGridLinesVisible = true

        chart.addSeries("Bookings", dates, counts)
        chart.addSeries("Revenue", dates, revenue)

        val out = ByteArrayOutputStream()
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG)
        return out.toByteArray()
    }

    /**
     * Generates a pie chart breaking down bookings by status.
     *
     * Each slice represents one status value. Known statuses (`"confirmed"`,
     * `"cancelled"`, `"pending"`) are rendered with fixed colours; any other
     * status falls back to gray. The chart is rendered at 600×400 pixels with
     * the legend positioned outside the plot area on the right.
     *
     * @param data list of maps, each containing `"status"` (a `String`) and
     *        `"count"` (an `Int`)
     * @return the rendered chart as a PNG [ByteArray]
     */
    fun generateBookingStatusChart(data: List<Map<String, Any>>): ByteArray {
        val chart =
            PieChartBuilder()
                .width(600)
                .height(400)
                .title("Booking Status Breakdown")
                .build()

        chart.styler.chartBackgroundColor = Color.WHITE
        chart.styler.plotBackgroundColor = Color.WHITE
        chart.styler.isLegendVisible = true
        chart.styler.legendPosition = Styler.LegendPosition.OutsideE

        val colors =
            mapOf(
                "confirmed" to Color(76, 175, 80),
                "cancelled" to Color(244, 67, 54),
                "pending" to Color(255, 193, 7),
            )

        for (entry in data) {
            val status = entry["status"] as String
            val count = (entry["count"] as Int).toDouble()
            val series = chart.addSeries(status.replaceFirstChar { it.uppercase() }, count)
            series.fillColor = colors[status] ?: Color.GRAY
        }

        val out = ByteArrayOutputStream()
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG)
        return out.toByteArray()
    }

    /**
     * Generates a bar chart showing the busiest routes by booking count.
     *
     * Each bar represents one route, labelled as `"DepartureCity → ArrivalCity"`.
     * Bars are ordered by the order of entries in [data] (typically descending by
     * booking count as returned by the DAO). The chart is rendered at 800×400 pixels.
     *
     * @param data list of maps, each containing `"departureCity"` (a `String`),
     *        `"arrivalCity"` (a `String`), and `"bookingCount"` (an `Int`)
     * @return the rendered chart as a PNG [ByteArray]
     */
    fun generateBusiestRoutesChart(data: List<Map<String, Any>>): ByteArray {
        val labels = data.map { "${it["departureCity"]} → ${it["arrivalCity"]}" }
        val counts = data.map { (it["bookingCount"] as Int).toDouble() }

        val chart =
            CategoryChartBuilder()
                .width(800)
                .height(400)
                .title("Busiest Routes")
                .xAxisTitle("Route")
                .yAxisTitle("Bookings")
                .build()

        chart.styler.chartBackgroundColor = Color.WHITE
        chart.styler.plotBackgroundColor = Color.WHITE
        chart.styler.legendPosition = Styler.LegendPosition.InsideNW
        chart.styler.isLegendVisible = false
        chart.styler.xAxisLabelRotation = 45
        chart.styler.availableSpaceFill = 0.6

        chart.addSeries("Bookings", labels, counts)

        val out = ByteArrayOutputStream()
        BitmapEncoder.saveBitmap(chart, out, BitmapEncoder.BitmapFormat.PNG)
        return out.toByteArray()
    }
}
