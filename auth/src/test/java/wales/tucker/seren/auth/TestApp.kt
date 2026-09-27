package wales.tucker.seren.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import wales.tucker.seren.core.security.SecretBox
import java.util.Base64

/** Robolectric has no AndroidKeyStore, so tests use a reversible stand-in that still changes the text. */
class FakeSecretBox : SecretBox("test") {
    override fun encrypt(plain: ByteArray): String = "enc:" + Base64.getEncoder().encodeToString(plain)
    override fun decrypt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded.removePrefix("enc:"))
}

/** A clock that stands still until a test moves it, so codes are predictable. */
class FakeClock(start: Long = 1_700_000_010_000L) : Clock {
    val time = MutableStateFlow(start)
    override fun now(): Long = time.value
    override val ticks: Flow<Long> = time
}

class TestApp : SerenApp() {
    val clock = FakeClock()
    override fun createContainer(): AppContainer = AppContainer(this, FakeSecretBox(), clock)
}
