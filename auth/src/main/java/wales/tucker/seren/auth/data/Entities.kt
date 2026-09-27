package wales.tucker.seren.auth.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import wales.tucker.seren.auth.otp.OtpAlgorithm
import wales.tucker.seren.auth.otp.OtpToken
import wales.tucker.seren.auth.otp.OtpType

/** An account as stored: everything in the clear except the setup key. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val issuer: String,
    val name: String,
    /** The Base32 setup key, encrypted with the app's Keystore key (see SecretBox). */
    val secretEnc: String,
    val type: OtpType,
    val algorithm: OtpAlgorithm,
    val digits: Int,
    val period: Int,
    val counter: Long,
    /** Index into the accent palette. */
    val color: Int,
    val created: Long,
)

/** An account with its setup key decrypted, ready to make codes. */
data class Account(
    val id: Long,
    val token: OtpToken,
    val color: Int,
    val created: Long,
)
