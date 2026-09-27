package wales.tucker.seren.auth.otp

/**
 * Something in copied text that can add accounts: what Seren Auth looks for on the clipboard when
 * it comes back to the front, to offer adding it in one tap.
 */
sealed interface CopiedSetup {
    /** The setup keys this would add, to leave out ones already in Seren Auth. */
    val secrets: List<String>

    /** An otpauth link, already checked to be one Seren Auth can add. */
    data class Link(val link: String, val token: OtpToken) : CopiedSetup {
        override val secrets get() = listOf(token.secret)
    }

    /** A Google Authenticator transfer link with at least one account Seren Auth can add. */
    data class Transfer(val link: String, val tokens: List<OtpToken>) : CopiedSetup {
        override val secrets get() = tokens.map { it.secret }
    }

    /** A setup key on its own, normalized, still needing a service or account name. */
    data class Key(val secret: String) : CopiedSetup {
        override val secrets get() = listOf(secret)
    }

    companion object {
        /** Longer than any transfer link Google Authenticator makes; skip reading big clips at all. */
        const val MAX_LENGTH = 16_384

        private val LINK = Regex("""(?i)otpauth(-migration)?://[^\s"'<>]+""")
        private const val TRAILING_PUNCTUATION = ".,;:!?)]}"
        private val KEY_CHARS = Regex("""[A-Za-z2-7=\- ]+""")

        /**
         * The first link in [text] Seren Auth can use, or [text] itself if it's a setup key.
         * Links can sit inside a sentence or an email; a bare key has to be the whole clip, since
         * plenty of ordinary words and IDs are valid Base32.
         */
        fun find(text: String): CopiedSetup? {
            if (text.length > MAX_LENGTH) return null
            LINK.findAll(text).forEach { match ->
                val link = match.value.trimEnd { it in TRAILING_PUNCTUATION }
                when {
                    GoogleMigration.isMigration(link) -> runCatching { GoogleMigration.parse(link) }.getOrNull()
                        ?.takeIf { it.tokens.isNotEmpty() }
                        ?.let { return Transfer(link, it.tokens) }
                    else -> runCatching { OtpAuthUri.parse(link) }.getOrNull()?.let { return Link(link, it) }
                }
            }
            return key(text.trim())
        }

        /**
         * [text] as a setup key if it looks like one: Base32 letters and digits, at least 16 of
         * them (80 bits, the shortest keys sites give out), with both a digit and a letter, so
         * words and numbers alone aren't mistaken for keys.
         */
        private fun key(text: String): Key? {
            if (!KEY_CHARS.matches(text)) return null
            val secret = Base32.normalize(text)
            if (secret.length !in 16..128 || !Base32.isValid(secret)) return null
            if (secret.none { it in '2'..'7' } || secret.none { it in 'A'..'Z' }) return null
            return Key(secret)
        }
    }
}
