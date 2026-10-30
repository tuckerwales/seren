package wales.tucker.seren.ssh.voice

import android.Manifest
import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Short foreground-only PCM recording, entirely in memory. No system speech/cloud service. */
internal class VoiceRecorder {
    @Volatile private var stopped = false
    fun stop() { stopped = true }

    @SuppressLint("MissingPermission") // Caller requests Manifest.permission.RECORD_AUDIO first.
    fun record(): ByteArray {
        val rate = 16000
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Microphone does not support 16 kHz PCM recording" }
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, rate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 4096),
        )
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Cannot open microphone" }
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Cannot start microphone" }
            val pcm = ByteArrayOutputStream()
            val buffer = ByteArray(2048)
            val limit = rate * 2 * 8 // Eight seconds, at most 256 KiB PCM.
            while (!stopped && pcm.size() < limit) {
                val count = recorder.read(buffer, 0, minOf(buffer.size, limit - pcm.size()))
                check(count > 0) { "Microphone read failed" }
                pcm.write(buffer, 0, count)
            }
            check(pcm.size() > 0) { "No audio recorded" }
            return wav(pcm.toByteArray(), rate)
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
    }
}

internal fun wav(pcm: ByteArray, rate: Int = 16000): ByteArray {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray()).putInt(pcm.size + 36).put("WAVEfmt ".toByteArray())
    header.putInt(16).putShort(1.toShort()).putShort(1.toShort()).putInt(rate).putInt(rate * 2)
    header.putShort(2.toShort()).putShort(16.toShort()).put("data".toByteArray()).putInt(pcm.size)
    return header.array() + pcm
}
