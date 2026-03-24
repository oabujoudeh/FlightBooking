package com.flightbooking

import org.knowm.xchart.XYChartBuilder
import org.knowm.xchart.BitmapEncoder
import org.knowm.xchart.style.Styler
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.Date
import java.time.ZoneId

object ChartService {

    fun generateBookingsOverTimeChart(data: List<Map<String, Any>>): ByteArray {
        val dates = data.map {
            val localDate = LocalDate.parse(it["date"] as String)
            Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
        }
        val counts = data.map { (it["count"] as Int).toDouble() }
        val revenue = data.map { it["revenue"] as Double }

        val chart = XYChartBuilder()
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
}
