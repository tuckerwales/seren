package wales.tucker.seren.ssh.ssh

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.DataInputStream
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * A minimal SOCKS4/4a/5 (no authentication, CONNECT only) proxy that tunnels every connection
 * through an SSH "direct-tcpip" channel, providing dynamic port forwarding (ssh -D).
 */
class SocksProxyServer(
    private val session: Session,
    private val bindAddress: String,
    private val port: Int,
) {
    private var serverSocket: ServerSocket? = null
    private val executor = Executors.newCachedThreadPool { r -> Thread(r, "socks-$port").apply { isDaemon = true } }

    @Volatile
    private var running = false

    val localPort: Int get() = serverSocket?.localPort ?: port

    fun start() {
        val ss = ServerSocket()
        ss.reuseAddress = true
        ss.bind(InetSocketAddress(InetAddress.getByName(bindAddress), port))
        serverSocket = ss
        running = true
        executor.execute {
            while (running) {
                val client = try {
                    ss.accept()
                } catch (_: IOException) {
                    break
                }
                executor.execute { handle(client) }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { serverSocket?.close() }
        executor.shutdownNow()
    }

    private fun handle(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val input = DataInputStream(socket.getInputStream())
            val out = socket.getOutputStream()
            when (input.readUnsignedByte()) {
                5 -> handleSocks5(socket, input, out)
                4 -> handleSocks4(socket, input, out)
                else -> socket.close()
            }
        } catch (_: Exception) {
            runCatching { socket.close() }
        }
    }

    private fun handleSocks5(socket: Socket, input: DataInputStream, out: OutputStream) {
        val methods = input.readUnsignedByte()
        val offered = ByteArray(methods).also { input.readFully(it) }
        if (offered.none { it.toInt() == 0 }) {
            out.write(byteArrayOf(5, 0xFF.toByte()))
            socket.close()
            return
        }
        out.write(byteArrayOf(5, 0))
        if (input.readUnsignedByte() != 5) throw IOException("bad version")
        val cmd = input.readUnsignedByte()
        input.readUnsignedByte() // reserved
        val host = when (input.readUnsignedByte()) {
            1 -> ByteArray(4).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
            3 -> ByteArray(input.readUnsignedByte()).also { input.readFully(it) }.let { String(it, Charsets.US_ASCII) }
            4 -> ByteArray(16).also { input.readFully(it) }.let { InetAddress.getByAddress(it).hostAddress!! }
            else -> throw IOException("bad address type")
        }
        val destPort = input.readUnsignedShort()
        if (cmd != 1) {
            out.write(byteArrayOf(5, 7, 0, 1, 0, 0, 0, 0, 0, 0))
            socket.close()
            return
        }
        connect(socket, host, destPort,
            success = byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0),
            failure = byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0))
    }

    private fun handleSocks4(socket: Socket, input: DataInputStream, out: OutputStream) {
        val cmd = input.readUnsignedByte()
        val destPort = input.readUnsignedShort()
        val ip = ByteArray(4).also { input.readFully(it) }
        readNullTerminated(input) // user id
        val host = if (ip[0].toInt() == 0 && ip[1].toInt() == 0 && ip[2].toInt() == 0 && ip[3].toInt() != 0) {
            readNullTerminated(input) // SOCKS4a host name
        } else {
            InetAddress.getByAddress(ip).hostAddress!!
        }
        if (cmd != 1) {
            out.write(byteArrayOf(0, 0x5B, 0, 0, 0, 0, 0, 0))
            socket.close()
            return
        }
        connect(socket, host, destPort,
            success = byteArrayOf(0, 0x5A, 0, 0, 0, 0, 0, 0),
            failure = byteArrayOf(0, 0x5B, 0, 0, 0, 0, 0, 0))
    }

    private fun readNullTerminated(input: DataInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val b = input.readUnsignedByte()
            if (b == 0) break
            if (sb.length > 255) throw IOException("string too long")
            sb.append(b.toChar())
        }
        return sb.toString()
    }

    private fun connect(socket: Socket, host: String, destPort: Int, success: ByteArray, failure: ByteArray) {
        val sockOut = socket.getOutputStream()
        // Hold remote data until the SOCKS reply has been written.
        val gate = CountDownLatch(1)
        val gatedOut = object : OutputStream() {
            override fun write(b: Int) {
                gate.await(); sockOut.write(b)
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                gate.await(); sockOut.write(b, off, len); sockOut.flush()
            }

            override fun flush() = sockOut.flush()
            override fun close() {
                runCatching { socket.close() }
            }
        }
        val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
        channel.setHost(host)
        channel.setPort(destPort)
        channel.setOrgIPAddress(socket.inetAddress.hostAddress)
        channel.setOrgPort(socket.port)
        channel.setInputStream(socket.getInputStream())
        channel.setOutputStream(gatedOut)
        try {
            channel.connect(15_000)
        } catch (e: Exception) {
            sockOut.write(failure)
            gate.countDown()
            socket.close()
            return
        }
        sockOut.write(success)
        sockOut.flush()
        gate.countDown()
    }
}
