package com.codexlocal.tilabo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Meteo pour la Jasette (Open-Meteo : gratuit, sans cle).
 * Lieu : la ville nommee, sinon la position du telephone, sinon la derniere ville demandee.
 */
object Meteo {

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tilabo", Context.MODE_PRIVATE)

    class PositionRequise : Exception("Dis-moi une ville (ex. « météo à Normandin ») ou permets la position du téléphone.")

    private fun geocoder(ville: String): JSONObject {
        val j = JSONObject(Web.telecharger("https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" +
            URLEncoder.encode(ville.trim(), "UTF-8")))
        val r = j.optJSONArray("results")?.optJSONObject(0)
            ?: throw IllegalArgumentException("Ville introuvable : $ville")
        val nom = listOf(r.optString("name"), r.optString("admin1"), r.optString("country_code"))
            .filter { it.isNotBlank() }.distinct().joinToString(", ")
        return JSONObject().put("lat", r.optDouble("latitude")).put("lon", r.optDouble("longitude")).put("lieu", nom)
    }

    private fun position(ctx: Context): JSONObject? {
        if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
        val lm = ctx.getSystemService(LocationManager::class.java)
        val loc = runCatching {
            listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
                .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull() ?: return null
        @Suppress("DEPRECATION")
        val nom = runCatching {
            Geocoder(ctx, Locale.CANADA_FRENCH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()?.let {
                listOfNotNull(it.locality ?: it.subAdminArea, it.adminArea).joinToString(", ")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Ta position"
        return JSONObject().put("lat", loc.latitude).put("lon", loc.longitude).put("lieu", nom)
    }

    fun obtenir(ctx: Context, ville: String?): JSONObject {
        val lieu = when {
            !ville.isNullOrBlank() -> geocoder(ville).also { prefs(ctx).edit().putString("ville", ville.trim()).apply() }
            else -> position(ctx) ?: prefs(ctx).getString("ville", null)?.let { geocoder(it) } ?: throw PositionRequise()
        }
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${lieu.getDouble("lat")}&longitude=${lieu.getDouble("lon")}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,precipitation" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=4&wind_speed_unit=kmh"
        val j = JSONObject(Web.telecharger(url))
        return JSONObject().put("lieu", lieu.getString("lieu"))
            .put("actuel", j.optJSONObject("current") ?: JSONObject())
            .put("jours", j.optJSONObject("daily") ?: JSONObject())
    }
}
