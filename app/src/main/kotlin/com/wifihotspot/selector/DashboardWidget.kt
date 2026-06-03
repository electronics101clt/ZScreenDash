package com.wifihotspot.selector

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class DashboardWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Widget is first added
    }

    override fun onDisabled(context: Context) {
        // Last widget is removed
    }

    companion object {
        private const val ESP32_URL = "http://192.168.4.1/api/data"
        private val executor = Executors.newSingleThreadExecutor()
        private val mainHandler = Handler(Looper.getMainLooper())

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_dashboard)

            // Set up click intent to open main activity
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.speed_value, pendingIntent)

            // Fetch data from ESP32 in background thread
            executor.execute {
                try {
                    val data = fetchDashboardData()
                    if (data != null) {
                        mainHandler.post {
                            updateWidgetViews(context, appWidgetManager, appWidgetId, views, data)
                        }
                    } else {
                        // Show error state
                        mainHandler.post {
                            views.setTextViewText(R.id.last_update, "Not connected")
                            appWidgetManager.updateAppWidget(appWidgetId, views)
                        }
                    }
                } catch (e: Exception) {
                    mainHandler.post {
                        views.setTextViewText(R.id.last_update, "Error: ${e.message}")
                        appWidgetManager.updateAppWidget(appWidgetId, views)
                    }
                }
            }

            // Initial update
            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun fetchDashboardData(): JSONObject? {
            return try {
                val response = URL(ESP32_URL).readText()
                JSONObject(response)
            } catch (e: Exception) {
                null
            }
        }

        private fun updateWidgetViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews,
            data: JSONObject
        ) {
            try {
                // Update speed
                val speed = data.optDouble("speed", 0.0)
                views.setTextViewText(R.id.speed_value, speed.toInt().toString())

                // Update RPM
                val rpm = data.optDouble("rpm", 0.0)
                val rpmDisplay = (rpm / 1000).toString().take(3)
                views.setTextViewText(R.id.rpm_value, rpmDisplay)

                // Update coolant
                val coolant = data.optDouble("coolant", 0.0)
                views.setTextViewText(R.id.coolant_value, coolant.toInt().toString())

                // Update fuel
                val fuel = data.optDouble("fuel", 0.0)
                views.setTextViewText(R.id.fuel_value, fuel.toInt().toString())

                // Update timestamp
                val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                val currentTime = timeFormat.format(Date())
                views.setTextViewText(R.id.last_update, "Updated: $currentTime")

                appWidgetManager.updateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                views.setTextViewText(R.id.last_update, "Parse error")
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }
    }
}
