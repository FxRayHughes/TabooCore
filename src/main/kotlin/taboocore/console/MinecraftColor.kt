package taboocore.console

/**
 * Minecraft color code → ANSI escape sequence converter
 *
 * Supports:
 * - Legacy §/& color codes: §a, &b, etc.
 * - Legacy §/& formatting codes: §l (bold), §o (italic), etc.
 * - Hex RGB colors (1.16+): §x§R§R§G§G§B§B / &x&R&R&G&G&B&B → ANSI 24-bit true color
 */
object MinecraftColor {

    private const val ESC = "\u001B"
    const val RESET = "${ESC}[0m"
    const val GREEN = "${ESC}[92m"
    const val YELLOW = "${ESC}[93m"
    const val RED = "${ESC}[91m"

    private val COLOR_MAP = mapOf(
        '0' to "${ESC}[30m",    // Black
        '1' to "${ESC}[34m",    // Dark Blue
        '2' to "${ESC}[32m",    // Dark Green
        '3' to "${ESC}[36m",    // Dark Aqua
        '4' to "${ESC}[31m",    // Dark Red
        '5' to "${ESC}[35m",    // Dark Purple
        '6' to "${ESC}[33m",    // Gold
        '7' to "${ESC}[37m",    // Gray
        '8' to "${ESC}[90m",    // Dark Gray
        '9' to "${ESC}[94m",    // Blue
        'a' to "${ESC}[92m",    // Green
        'b' to "${ESC}[96m",    // Aqua
        'c' to "${ESC}[91m",    // Red
        'd' to "${ESC}[95m",    // Light Purple
        'e' to "${ESC}[93m",    // Yellow
        'f' to "${ESC}[97m",    // White
        'k' to "${ESC}[8m",     // Obfuscated
        'l' to "${ESC}[1m",     // Bold
        'm' to "${ESC}[9m",     // Strikethrough
        'n' to "${ESC}[4m",     // Underline
        'o' to "${ESC}[3m",     // Italic
        'r' to RESET,           // Reset
    )

    fun toAnsi(text: String): String {
        if ('§' !in text && '&' !in text) return text
        val sb = StringBuilder(text.length)
        var hasColor = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if ((c == '§' || c == '&') && i + 1 < text.length) {
                val code = text[i + 1].lowercaseChar()
                // Hex color: §x§R§R§G§G§B§B
                if ((code == 'x') && i + 13 < text.length) {
                    val hex = tryParseHex(text, i)
                    if (hex != null) {
                        val r = hex shr 16 and 0xFF
                        val g = hex shr 8 and 0xFF
                        val b = hex and 0xFF
                        sb.append("${ESC}[38;2;${r};${g};${b}m")
                        hasColor = true
                        i += 14 // skip §x + 6 * §X
                        continue
                    }
                }
                // Standard color/format code
                val ansi = COLOR_MAP[code]
                if (ansi != null) {
                    sb.append(ansi)
                    hasColor = true
                    i += 2
                    continue
                }
            }
            sb.append(c)
            i++
        }
        if (hasColor) sb.append(RESET)
        return sb.toString()
    }

    /**
     * Try to parse hex color at position: §x§R§R§G§G§B§B
     * Returns RGB int or null if invalid.
     */
    private fun tryParseHex(text: String, start: Int): Int? {
        // start points to § of §x, need 6 more §X pairs after it
        val hex = StringBuilder(6)
        for (j in 0 until 6) {
            val idx = start + 2 + j * 2
            if (idx + 1 >= text.length) return null
            val prefix = text[idx]
            if (prefix != '§' && prefix != '&') return null
            hex.append(text[idx + 1])
        }
        return hex.toString().toIntOrNull(16)
    }
}
