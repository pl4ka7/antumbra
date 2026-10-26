package app.antumbra.tor

import android.content.Context
import java.io.File
import java.util.Locale

/** Tor's GeoIP databases, copied out of the APK when the app version changes. */
object Geo {

    data class Files(val v4: File, val v6: File)

    fun install(context: Context, buildTime: Long): Files? = runCatching {
        val bundled = context.assets.list("geoip")?.toSet().orEmpty()
        if ("geoip" !in bundled || "geoip6" !in bundled) return null
        val dir = File(context.noBackupFilesDir, "geoip").apply { mkdirs() }
        val stamp = File(dir, "version")
        val current = stamp.exists() && stamp.readText() == buildTime.toString()
        for (name in listOf("geoip", "geoip6")) {
            val out = File(dir, name)
            if (!current || !out.exists()) {
                context.assets.open("geoip/$name").use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        stamp.writeText(buildTime.toString())
        Files(File(dir, "geoip"), File(dir, "geoip6"))
    }.getOrNull()

    fun countryName(code: String, locale: Locale = Locale.getDefault()): String =
        Locale.Builder().setRegion(code.uppercase()).build().getDisplayCountry(locale)

    fun flag(code: String): String {
        if (code.length != 2 || !code.all { it.isLetter() }) return ""
        return code.uppercase().map { String(Character.toChars(0x1F1E6 + (it - 'A'))) }.joinToString("")
    }
}
