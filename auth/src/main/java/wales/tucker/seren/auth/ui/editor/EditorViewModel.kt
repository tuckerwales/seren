package wales.tucker.seren.auth.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import wales.tucker.seren.auth.AppContainer
import wales.tucker.seren.auth.data.Account
import wales.tucker.seren.auth.data.AccountRepository
import wales.tucker.seren.auth.otp.Base32
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpAuthUri
import wales.tucker.seren.auth.otp.OtpFormatException
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType

/** The editor's fields, as typed. */
data class AccountForm(
    val issuer: String = "",
    val name: String = "",
    val secret: String = "",
    val type: OtpType = OtpType.TOTP,
    val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    val digits: Int = OtpToken.DEFAULT_DIGITS,
    val period: String = OtpToken.DEFAULT_PERIOD.toString(),
    val counter: String = "0",
    /** An accent index, or null to follow the service name until the user picks one. */
    val color: Int? = null,
) {
    val effectiveColor: Int
        get() = color ?: AccountRepository.defaultColor(OtpToken(issuer, name, ""))

    val secretError: String?
        get() = when {
            secret.isBlank() -> "Enter the setup key"
            !Base32.isValid(secret) -> "Setup keys use only the letters A to Z and the digits 2 to 7"
            else -> null
        }

    val nameError: String?
        get() = if (issuer.isBlank() && name.isBlank()) "Enter a service or an account name" else null

    val periodError: String?
        get() = if (type == OtpType.TOTP && period.toIntOrNull() !in OtpToken.PERIOD_RANGE) "Enter a number of seconds from 1 to 3600" else null

    val counterError: String?
        get() = if (type == OtpType.HOTP && (counter.toLongOrNull() ?: -1) < 0) "Enter a whole number, 0 or more" else null

    /** Whether the advanced settings differ from what almost every site uses. */
    val hasAdvanced: Boolean
        get() = type != OtpType.TOTP || algorithm != OtpAlgorithm.SHA1 || digits != OtpToken.DEFAULT_DIGITS ||
            period != OtpToken.DEFAULT_PERIOD.toString()

    /** The token these fields describe, or null while any of them is invalid. */
    fun token(): OtpToken? {
        if (secretError != null || periodError != null || counterError != null) return null
        return OtpToken(
            issuer = issuer.trim(),
            name = name.trim(),
            secret = Base32.normalize(secret),
            type = type,
            algorithm = algorithm,
            digits = digits,
            period = period.toIntOrNull() ?: OtpToken.DEFAULT_PERIOD,
            counter = counter.toLongOrNull() ?: 0,
        )
    }

    companion object {
        fun of(token: OtpToken, color: Int?) = AccountForm(
            issuer = token.issuer,
            name = token.name,
            secret = token.secret,
            type = token.type,
            algorithm = token.algorithm,
            digits = token.digits,
            period = token.period.toString(),
            counter = token.counter.toString(),
            color = color,
        )
    }
}

/** Adds a new account (optionally filled in from an otpauth [link]) or edits the one with [id]. */
class EditorViewModel(private val container: AppContainer, val id: Long?, link: String?) : ViewModel() {
    var form by mutableStateOf(AccountForm())
        private set

    /** The form as it was loaded, to tell whether there are unsaved changes. */
    private var original = AccountForm()

    var account by mutableStateOf<Account?>(null)
        private set

    var loaded by mutableStateOf(id == null)
        private set

    /** Errors show only after the first save attempt, so an empty new form isn't all red. */
    var showErrors by mutableStateOf(false)
        private set

    /** Set when the setup key is already in Seren Auth. */
    var duplicateOf by mutableStateOf<String?>(null)
        private set

    var saving by mutableStateOf(false)
        private set

    val isNew get() = id == null
    val dirty get() = form != original

    init {
        if (id != null) {
            viewModelScope.launch {
                val a = container.accounts.get(id)
                account = a
                if (a != null) {
                    form = AccountForm.of(a.token, a.color)
                    original = form
                }
                loaded = true
            }
        } else if (link != null) {
            val token = try {
                OtpAuthUri.parse(link)
            } catch (e: OtpFormatException) {
                null
            }
            if (token != null) {
                // A scanned code counts as a change, so leaving asks before dropping it.
                form = AccountForm.of(token, null)
            }
        }
    }

    fun update(transform: (AccountForm) -> AccountForm) {
        form = transform(form)
        duplicateOf = null
    }

    /** Saves and calls [onSaved] with a confirmation, or shows the errors. */
    fun save(onSaved: (String) -> Unit) {
        showErrors = true
        val token = form.token()
        if (token == null || form.nameError != null || saving) return
        saving = true
        viewModelScope.launch {
            val repo = container.accounts
            if (id == null) {
                when (val result = repo.add(token, form.effectiveColor)) {
                    is AccountRepository.AddResult.Added -> {
                        original = form
                        onSaved("Added ${token.title}")
                    }
                    is AccountRepository.AddResult.Duplicate -> duplicateOf = result.existing.token.title.ifBlank { "another account" }
                }
            } else {
                repo.update(id, token, form.effectiveColor)
                original = form
                onSaved("Saved ${token.title}")
            }
            saving = false
        }
    }

    fun delete(onDeleted: (Account) -> Unit) {
        val a = account ?: return
        viewModelScope.launch {
            container.accounts.delete(a.id)
            original = form
            onDeleted(a)
        }
    }
}
