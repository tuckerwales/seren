package wales.tucker.seren.ssh

import wales.tucker.seren.ssh.security.SecretBox
import java.util.Base64

/** Robolectric has no AndroidKeyStore, so tests use a reversible stand-in. */
class FakeSecretBox : SecretBox() {
    override fun encrypt(plain: ByteArray): String = Base64.getEncoder().encodeToString(plain)
    override fun decrypt(encoded: String): ByteArray = Base64.getDecoder().decode(encoded)
}

class TestApp : SerenApp() {
    override fun createContainer(): AppContainer = AppContainer(this, FakeSecretBox())
}
