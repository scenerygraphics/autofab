import graphics.scenery.AutofabClient
import graphics.scenery.AutofabMain
import org.junit.jupiter.api.assertDoesNotThrow
import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.concurrent.thread
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals


class RequestReplyTests {
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    @Test
    fun testDiscovery() {
        var app: AutofabMain? = null

        thread(isDaemon = true) {
            app = AutofabMain()
        }

        val localhost = InetAddress.getLocalHost()
        var client: AutofabClient? = null

        try {
            client = AutofabClient()
            // Wait a bit
            Thread.sleep(5000)
        } catch(e: UnknownHostException) {
            logger.error(e.message)
        } catch(e: IOException) {
            logger.error(e.message)
        }

        logger.info("Found hosts: " + client?.listAvailableHosts()?.joinToString(", ") { it.toString() })
        assert(client!!.listAvailableHosts().contains(localhost))
        app?.close()
        client.close()
    }

    @Test
    fun testHello() {
        var app: AutofabMain? = null

        thread(isDaemon = true) {
            app = AutofabMain()
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
         app?.close()
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun testLaunch() {
        var app: AutofabMain? = null
        var client: AutofabClient? = null

        thread(isDaemon = true) {
            app = AutofabMain()
            // host registration needs to be enabled
            app.enableHostRegistration(true)
        }

        client = AutofabClient()
        Thread.sleep(5000)

        assertDoesNotThrow {
            client.launchOnAvailableHosts("open /Applications/iTerm.app", register = true)
        }

        app?.close()
    }

}