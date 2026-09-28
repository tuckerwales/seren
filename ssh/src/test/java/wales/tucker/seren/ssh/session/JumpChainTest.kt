package wales.tucker.seren.ssh.session

import org.junit.Assert.assertEquals
import org.junit.Test

class JumpChainTest {
    @Test
    fun ordersOutermostFirst() {
        // A → B → C  (A.jump=B, B.jump=C)
        val jumps = mapOf(1L to 2L, 2L to 3L, 3L to null)
        assertEquals(listOf(3L, 2L), resolveJumpHosts(1L) { jumps[it] })
    }

    @Test
    fun stopsOnCycle() {
        val jumps = mapOf(1L to 2L, 2L to 1L)
        assertEquals(listOf(2L), resolveJumpHosts(1L) { jumps[it] })
    }

    @Test
    fun emptyWhenNoJump() {
        assertEquals(emptyList<Long>(), resolveJumpHosts(1L) { null })
    }
}
