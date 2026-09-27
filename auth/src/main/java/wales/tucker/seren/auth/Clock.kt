package wales.tucker.seren.auth

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Where Seren Auth gets the time for codes, so tests can fix it. */
interface Clock {
    fun now(): Long

    /** The time, emitted just after every whole second, for screens that count down. */
    val ticks: Flow<Long>
}

object SystemClock : Clock {
    override fun now(): Long = System.currentTimeMillis()

    override val ticks: Flow<Long> = flow {
        while (true) {
            val t = now()
            emit(t)
            delay(1000 - t % 1000 + 5)
        }
    }
}
