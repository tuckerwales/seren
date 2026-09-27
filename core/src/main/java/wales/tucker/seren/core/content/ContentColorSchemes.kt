package wales.tucker.seren.core.content

/**
 * A named color scheme for user content shown on a styled canvas (terminal output, code): default
 * foreground and background, cursor and the 16 ANSI colors. The ids and names are the same in every
 * Seren app, so a favorite scheme means the same thing everywhere.
 */
data class ContentColorScheme(
    val id: String,
    val name: String,
    val foreground: Int,
    val background: Int,
    val cursor: Int,
    val ansi: IntArray,
) {
    init {
        require(ansi.size == 16)
    }

    val isDark: Boolean
        get() {
            val r = (background shr 16) and 0xFF
            val g = (background shr 8) and 0xFF
            val b = background and 0xFF
            return (0.299 * r + 0.587 * g + 0.114 * b) < 128
        }

    override fun equals(other: Any?): Boolean = other is ContentColorScheme && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

object ContentColorSchemes {
    private fun c(hex: Long): Int = (0xFF000000L or hex).toInt()
    private fun ansi(vararg hex: Long): IntArray = IntArray(16) { c(hex[it]) }

    val DEFAULT = ContentColorScheme(
        id = "midnight", name = "Midnight",
        foreground = c(0xE6E6EF), background = c(0x0F1117), cursor = c(0x7AA2F7),
        ansi = ansi(
            0x1A1D26, 0xF7768E, 0x9ECE6A, 0xE0AF68, 0x7AA2F7, 0xBB9AF7, 0x7DCFFF, 0xC0CAF5,
            0x444B6A, 0xFF7A93, 0xB9F27C, 0xFFC777, 0x82AAFF, 0xC099FF, 0x86E1FC, 0xFFFFFF,
        ),
    )

    val ALL: List<ContentColorScheme> = listOf(
        DEFAULT,
        ContentColorScheme(
            "dracula", "Dracula", c(0xF8F8F2), c(0x282A36), c(0xF8F8F2),
            ansi(
                0x21222C, 0xFF5555, 0x50FA7B, 0xF1FA8C, 0xBD93F9, 0xFF79C6, 0x8BE9FD, 0xF8F8F2,
                0x6272A4, 0xFF6E6E, 0x69FF94, 0xFFFFA5, 0xD6ACFF, 0xFF92DF, 0xA4FFFF, 0xFFFFFF,
            ),
        ),
        ContentColorScheme(
            "catppuccin-mocha", "Catppuccin Mocha", c(0xCDD6F4), c(0x1E1E2E), c(0xF5E0DC),
            ansi(
                0x45475A, 0xF38BA8, 0xA6E3A1, 0xF9E2AF, 0x89B4FA, 0xF5C2E7, 0x94E2D5, 0xBAC2DE,
                0x585B70, 0xF38BA8, 0xA6E3A1, 0xF9E2AF, 0x89B4FA, 0xF5C2E7, 0x94E2D5, 0xA6ADC8,
            ),
        ),
        ContentColorScheme(
            "nord", "Nord", c(0xD8DEE9), c(0x2E3440), c(0xD8DEE9),
            ansi(
                0x3B4252, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x88C0D0, 0xE5E9F0,
                0x4C566A, 0xBF616A, 0xA3BE8C, 0xEBCB8B, 0x81A1C1, 0xB48EAD, 0x8FBCBB, 0xECEFF4,
            ),
        ),
        ContentColorScheme(
            "gruvbox-dark", "Gruvbox Dark", c(0xEBDBB2), c(0x282828), c(0xEBDBB2),
            ansi(
                0x282828, 0xCC241D, 0x98971A, 0xD79921, 0x458588, 0xB16286, 0x689D6A, 0xA89984,
                0x928374, 0xFB4934, 0xB8BB26, 0xFABD2F, 0x83A598, 0xD3869B, 0x8EC07C, 0xEBDBB2,
            ),
        ),
        ContentColorScheme(
            "one-dark", "One Dark", c(0xABB2BF), c(0x282C34), c(0x528BFF),
            ansi(
                0x282C34, 0xE06C75, 0x98C379, 0xE5C07B, 0x61AFEF, 0xC678DD, 0x56B6C2, 0xABB2BF,
                0x5C6370, 0xE06C75, 0x98C379, 0xE5C07B, 0x61AFEF, 0xC678DD, 0x56B6C2, 0xFFFFFF,
            ),
        ),
        ContentColorScheme(
            "solarized-dark", "Solarized Dark", c(0x839496), c(0x002B36), c(0x93A1A1),
            ansi(
                0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
                0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
            ),
        ),
        ContentColorScheme(
            "monokai", "Monokai", c(0xF8F8F2), c(0x272822), c(0xF8F8F0),
            ansi(
                0x272822, 0xF92672, 0xA6E22E, 0xF4BF75, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF8F8F2,
                0x75715E, 0xF92672, 0xA6E22E, 0xF4BF75, 0x66D9EF, 0xAE81FF, 0xA1EFE4, 0xF9F8F5,
            ),
        ),
        ContentColorScheme(
            "classic", "Classic Black", c(0xD0D0D0), c(0x000000), c(0xD0D0D0),
            ansi(
                0x000000, 0xCD0000, 0x00CD00, 0xCDCD00, 0x0000EE, 0xCD00CD, 0x00CDCD, 0xE5E5E5,
                0x7F7F7F, 0xFF0000, 0x00FF00, 0xFFFF00, 0x5C5CFF, 0xFF00FF, 0x00FFFF, 0xFFFFFF,
            ),
        ),
        ContentColorScheme(
            "solarized-light", "Solarized Light", c(0x657B83), c(0xFDF6E3), c(0x586E75),
            ansi(
                0x073642, 0xDC322F, 0x859900, 0xB58900, 0x268BD2, 0xD33682, 0x2AA198, 0xEEE8D5,
                0x002B36, 0xCB4B16, 0x586E75, 0x657B83, 0x839496, 0x6C71C4, 0x93A1A1, 0xFDF6E3,
            ),
        ),
        ContentColorScheme(
            "paper", "Paper", c(0x24292F), c(0xFAFAF7), c(0x0969DA),
            ansi(
                0x24292F, 0xCF222E, 0x116329, 0x7D4E00, 0x0969DA, 0x8250DF, 0x1B7C83, 0x6E7781,
                0x57606A, 0xA40E26, 0x1A7F37, 0x953800, 0x218BFF, 0xA475F9, 0x3192AA, 0x8C959F,
            ),
        ),
    )

    fun byId(id: String?): ContentColorScheme = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
