package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.IdentityRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import wales.tucker.seren.ssh.data.KeyType

class InAppAgentTest {

    @Test
    fun unlockHoldsKeysInMemoryAndWipeClearsThem() {
        val material = SshKeys.generate(KeyType.ED25519, 256, "test")
        val agent = InAppAgent()
        assertFalse(agent.isUnlocked)
        assertEquals(IdentityRepository.NOTRUNNING, agent.status)

        agent.unlock(listOf(InAppAgent.LoadedKey("test", material.privateKey.toByteArray(Charsets.UTF_8))))
        assertTrue(agent.isUnlocked)
        assertEquals(1, agent.keyCount)
        assertEquals(IdentityRepository.RUNNING, agent.status)
        val identities = agent.identities
        assertEquals(1, identities.size)
        val id = identities[0]
        assertNotNull(id.publicKeyBlob)
        assertNotNull(id.getSignature("challenge".toByteArray()))

        agent.wipe()
        assertFalse(agent.isUnlocked)
        assertEquals(0, agent.keyCount)
        assertTrue(agent.identities.isEmpty())
        assertNull(id.publicKeyBlob)
    }

    @Test
    fun wipeIsIdempotent() {
        val agent = InAppAgent()
        agent.wipe()
        agent.wipe()
        assertFalse(agent.isUnlocked)
    }

    @Test
    fun unlockReplacesPreviousKeys() {
        val a = SshKeys.generate(KeyType.ED25519, 256, "a")
        val b = SshKeys.generate(KeyType.ED25519, 256, "b")
        val agent = InAppAgent()
        agent.unlock(listOf(InAppAgent.LoadedKey("a", a.privateKey.toByteArray(Charsets.UTF_8))))
        assertEquals(1, agent.keyCount)
        agent.unlock(listOf(InAppAgent.LoadedKey("b", b.privateKey.toByteArray(Charsets.UTF_8))))
        assertEquals(1, agent.keyCount)
        assertEquals("b", agent.identities[0].name)
    }

    @Test
    fun unlockWithEmptyListStillMarksUnlocked() {
        val agent = InAppAgent()
        agent.unlock(emptyList())
        assertTrue(agent.isUnlocked)
        assertEquals(0, agent.keyCount)
    }
}
