package app.antumbra.core

/**
 * Registrable-domain approximation used for first-party circuit isolation.
 * Keep in sync with assets/extensions/isolation/background.js.
 */
object Domains {

    private val secondLevel = setOf(
        "co.uk", "org.uk", "ac.uk", "gov.uk", "com.au", "net.au", "org.au", "co.nz", "co.jp",
        "co.in", "co.za", "com.br", "com.mx", "com.ar", "com.tr", "com.cn", "com.tw", "co.kr",
    )

    fun base(host: String): String {
        val h = host.lowercase().trimEnd('.')
        if (h.isEmpty() || h.contains(':') || h.all { it.isDigit() || it == '.' }) return h
        val labels = h.split('.')
        if (labels.size <= 2) return h
        val lastTwo = labels.takeLast(2).joinToString(".")
        return if (lastTwo in secondLevel) labels.takeLast(3).joinToString(".") else lastTwo
    }
}
