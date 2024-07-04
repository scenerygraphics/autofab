import graphics.scenery.AutofabMain
import graphics.scenery.KeyManager
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.file.Files
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals


class RequestReplyTests {
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    @Test
    fun testDiscovery() {
        thread(isDaemon = true) {
            val app = AutofabMain()
        }

        val localhost = InetAddress.getLocalHost()
        val hostsFound = mutableListOf<InetAddress>()

        try {
            // Create a JmDNS instance
            val jmdns = JmDNS.create(InetAddress.getLocalHost())

            // Add a service listener
            jmdns.addServiceListener("_distributedscenery._tcp.local.", object: ServiceListener {
                override fun serviceAdded(event: ServiceEvent) {
                    logger.info("Service added: " + event.info)
                }

                override fun serviceRemoved(event: ServiceEvent) {
                    logger.info("Service removed: " + event.info)
                }

                override fun serviceResolved(event: ServiceEvent) {
                    logger.info("Service resolved: " + event.info)
                    hostsFound += event.info.inetAddresses
                }
            })

            // Wait a bit
            Thread.sleep(5000)
        } catch(e: UnknownHostException) {
            logger.error(e.message)
        } catch(e: IOException) {
            logger.error(e.message)
        }

        logger.info("Found hosts: " + hostsFound.joinToString(", ") { it.toString() })
        assert(hostsFound.contains(localhost))
    }

    @Test
    fun testHello() {
        thread(isDaemon = true) {
            val app = AutofabMain()
        }

        val localhost = InetAddress.getLocalHost()

        ZContext().use { context ->
            // Socket to talk to clients
            val socket: ZMQ.Socket = context.createSocket(SocketType.REQ)
            socket.connect("tcp://${localhost.hostAddress}:4223")

            socket.send("HELLO")
            val response = socket.recvStr()

            assertEquals(expected = "WORLD", actual = response, "Response to HELLO should be WORLD")
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testLaunch() {
        thread(isDaemon = true) {
            val app = AutofabMain()
            // host registration needs to be enabled
            app.enableHostRegistration(true)
        }

        val localhost = InetAddress.getLocalHost()
        val tmpDirectory = Files.createTempDirectory("AutofabLaunchTest")
        val km = KeyManager()
        val kp = km.getOwnKeyPair(tmpDirectory)

        ZContext().use { context ->
            // Socket to talk to clients
            val socket: ZMQ.Socket = context.createSocket(SocketType.REQ)
            socket.connect("tcp://${localhost.hostAddress}:4223")

            socket.sendMore("REGISTER")
            socket.sendMore("127.0.0.1")
            socket.send(Base64.encode(kp.public.encoded))

            val registerResponse = socket.recvStr()
            assertEquals(expected = "REGISTER OK", actual = registerResponse, "Response to REGISTER should be REGISTER OK")

            val payload = listOf("127.0.0.1", "2", "open", "/Applications/OmniFocus.app")
            logger.debug("Payload string is ${payload.joinToString("\n")}")
            val signature = KeyManager.sign(kp.private, payload.joinToString("\n").toByteArray())
            socket.sendMore("LAUNCH")
            socket.sendMore(signature)
            payload.forEachIndexed { i, p ->
                if(i == payload.size - 1) {
                    socket.send(p)
                } else {
                    socket.sendMore(p)
                }
            }

            val response = socket.recvStr()
            assertEquals(expected = "LAUNCH EPIC OK", actual = response, "Response to LAUNCH should be EPIC OK")
        }
    }

}