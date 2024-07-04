package graphics.scenery

import graphics.scenery.KeyManager.Companion.storeAsBase64
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import java.io.File
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths
import java.security.KeyPair
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText


class ZeroMQService(url: String, keyPair: KeyPair, keyManager: KeyManager, configDirectory: Path) {
    val logger = LoggerFactory.getLogger(javaClass.simpleName)

    @Volatile
    var registerEnabled = false

    val launchedThreads = mutableListOf<Thread>()

    val logDirectory = configDirectory.resolve("logs").createDirectories()

    private val logDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd_HH:mm:ss")

    val workerThread: Thread = thread(isDaemon = true) {
        ZContext().use { context ->
            val sub = context.createSocket(SocketType.REP)
            sub.bind(url)

            while(!Thread.currentThread().isInterrupted) {
                // Block until a message is received
                val request: ByteArray = sub.recv(0)
                val requestString = String(request)

                logger.info("Received $requestString")
                when {
                    // register new host, will send public key
                    requestString.startsWith("REGISTER") -> {
                        if(!registerEnabled) {
                            logger.warn("REGISTER received, but registration is disabled. Ignored.")
                            sub.recvStr()
                            sub.recvStr()
                            sub.send("REGISTER IGNORE")
                            continue
                        }

                        // will send hostname, then public key
                        val host = String(sub.recv())
                        val publicKey = String(sub.recv())

                        try {
                            val publicKeyFile = configDirectory.resolve("$host.pub")
                            KeyManager.getPublicKey(publicKey).storeAsBase64(publicKeyFile)
                            logger.info("Registered new host $host, public key stored at $publicKeyFile")
                        } catch(e: Exception) {
                            logger.error("Failed to register host $host: ${e.message}")
                            continue
                        }

                        sub.send("REGISTER OK")
                    }

                    // LAUNCH format is similar to what jaunch does
                    // String 1: ECDSA Signature over lines 2-n
                    // String 2: Hostname
                    // String 3: n lines following
                    // String 4-n: contents
                    requestString.startsWith("LAUNCH") -> {
                        // TODO determine host(name) from connection
                        val signature = sub.recvStr()
                        val host = sub.recvStr()
                        val lineCount = sub.recvStr().toInt(10)
                        val lines = (0 until lineCount).map {
                            sub.recvStr()
                        }

                        val payload = (listOf(host, lineCount.toString()) + lines).joinToString("\n")
                        logger.debug("Payload string is {}", payload)

                        val publicKey = try {
                            KeyManager.getPublicKey(configDirectory.resolve("$host.pub"))
                        } catch(e: Exception) {
                            logger.error("Error getting public key for host $host: ${e.message}")
                            continue
                        }

                        val result = KeyManager.verifySignature(publicKey, payload.toByteArray(), signature)
                        if(!result) {
                            logger.error("Failed to verify signature of LAUNCH payload from host $host, will ignore LAUNCH command.")
                            sub.send("LAUNCH EPIC FAIL")
                            continue
                        }

                        logger.info("Will launch this command: ${lines.joinToString(" ")}")

                        launchedThreads += thread(isDaemon = true) {
                            val output = lines.runCommand(Paths.get(".").toFile())
                            val timestamp = Timestamp(System.currentTimeMillis())
                            val logFile = logDirectory.resolve("$host-${logDateFormat.format(timestamp)}.log")
                            output?.let { logFile.writeText(it) }
                        }

                        sub.send("LAUNCH EPIC OK")
                    }

                    requestString.startsWith("HELLO") -> {
                        sub.send("WORLD")
                    }
                }
            }
        }
    }

    fun close() {
        workerThread.interrupt()
    }

    companion object {
        fun List<String>.runCommand(workingDir: File): String? {
            try {
                val proc = ProcessBuilder(this)
                    .directory(workingDir)
                    .redirectOutput(ProcessBuilder.Redirect.PIPE)
                    .redirectError(ProcessBuilder.Redirect.PIPE)
                    .start()

                //proc.waitFor(60, TimeUnit())
                Thread.sleep(60)

                return proc.inputStream.bufferedReader().readText()
            } catch(e: IOException) {
                e.printStackTrace()
                return null
            }
        }
    }
}