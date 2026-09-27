package wales.tucker.seren.auth.otp

import java.net.URLDecoder
import java.net.URLEncoder

/** Thrown when a link, QR code or file can't be read, with a [message] fit to show the user. */
class OtpFormatException(message: String) : Exception(message)

/**
 * `otpauth://` links, the format sites put in their setup QR codes:
 * `otpauth://totp/GitHub:you@example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub`.
 * See https://github.com/google/google-authenticator/wiki/Key-Uri-Format.
 */
object OtpAuthUri {
    const val SCHEME = "otpauth"

    fun isOtpAuth(text: String): Boolean = text.trim().startsWith("$SCHEME://", ignoreCase = true)

    /** @throws OtpFormatException if [text] is not a usable otpauth link. */
    fun parse(text: String): OtpToken {
        val link = text.trim()
        if (!isOtpAuth(link)) throw OtpFormatException("This isn't an otpauth link")
        val rest = link.substring(SCHEME.length + 3)
        val slash = rest.indexOf('/')
        val typeName = (if (slash >= 0) rest.substring(0, slash) else rest.substringBefore('?')).lowercase()
        val type = when (typeName) {
            "totp" -> OtpType.TOTP
            "hotp" -> OtpType.HOTP
            else -> throw OtpFormatException("Seren Auth doesn't support \"$typeName\" codes")
        }
        val afterType = if (slash >= 0) rest.substring(slash + 1) else ""
        val label = decodePath(afterType.substringBefore('?'))
        val query = parseQuery(afterType.substringAfter('?', ""))

        val secret = query["secret"].orEmpty()
        if (secret.isBlank()) throw OtpFormatException("The link has no setup key")
        if (!Base32.isValid(secret)) throw OtpFormatException("The setup key in the link isn't valid")

        val issuerParam = query["issuer"]?.trim()?.takeIf { it.isNotEmpty() }
        val (labelIssuer, name) = when {
            // The issuer itself may hold a colon, so match it whole before splitting.
            issuerParam != null && label.startsWith("$issuerParam:") -> issuerParam to label.substring(issuerParam.length + 1).trim()
            label.contains(':') -> label.substringBefore(':').trim() to label.substringAfter(':').trim()
            else -> "" to label.trim()
        }
        val issuer = issuerParam ?: labelIssuer

        val algorithm = when (query["algorithm"]?.uppercase()?.replace("-", "")) {
            null, "", "SHA1" -> OtpAlgorithm.SHA1
            "SHA256" -> OtpAlgorithm.SHA256
            "SHA512" -> OtpAlgorithm.SHA512
            else -> throw OtpFormatException("Seren Auth doesn't support the ${query["algorithm"]} algorithm")
        }
        val digits = query["digits"]?.takeIf { it.isNotBlank() }?.let {
            it.toIntOrNull()?.takeIf { d -> d in OtpToken.DIGIT_CHOICES } ?: throw OtpFormatException("Seren Auth doesn't support $it digit codes")
        } ?: OtpToken.DEFAULT_DIGITS
        val period = query["period"]?.takeIf { it.isNotBlank() }?.let {
            it.toIntOrNull()?.takeIf { p -> p in OtpToken.PERIOD_RANGE } ?: throw OtpFormatException("The link's period isn't valid")
        } ?: OtpToken.DEFAULT_PERIOD
        val counter = query["counter"]?.takeIf { it.isNotBlank() }?.let {
            it.toLongOrNull()?.takeIf { c -> c >= 0 } ?: throw OtpFormatException("The link's counter isn't valid")
        } ?: 0L

        return OtpToken(
            issuer = issuer,
            name = name,
            secret = Base32.normalize(secret),
            type = type,
            algorithm = algorithm,
            digits = digits,
            period = period,
            counter = counter,
        )
    }

    fun format(token: OtpToken): String {
        val type = token.type.name.lowercase()
        val label = if (token.issuer.isNotEmpty()) "${encode(token.issuer)}:${encode(token.name)}" else encode(token.name)
        val params = buildList {
            add("secret=${token.secret}")
            if (token.issuer.isNotEmpty()) add("issuer=${encode(token.issuer)}")
            add("algorithm=${token.algorithm.name}")
            add("digits=${token.digits}")
            when (token.type) {
                OtpType.TOTP -> add("period=${token.period}")
                OtpType.HOTP -> add("counter=${token.counter}")
            }
        }
        return "$SCHEME://$type/$label?${params.joinToString("&")}"
    }

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // In the path a "+" is a literal plus; only the query uses "+" for a space.
    private fun decodePath(s: String): String = decode(s.replace("+", "%2B"))

    private fun decode(s: String): String = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

    private fun parseQuery(query: String): Map<String, String> =
        query.split('&').filter { it.isNotEmpty() }.associate { pair ->
            decode(pair.substringBefore('=')).lowercase() to decode(pair.substringAfter('=', ""))
        }
}
