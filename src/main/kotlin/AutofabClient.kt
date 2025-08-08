package graphics.scenery

import org.slf4j.LoggerFactory
import org.zeromq.SocketType
import org.zeromq.ZContext
import org.zeromq.ZMQ
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.file.Path
import java.nio.file.Paths
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceInfo
import javax.jmdns.ServiceListener
import kotlin.concurrent.thread
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

class AutofabClient(
    val serviceType: String = "_distributedscenery._tcp.local.",
    val configDirectory: Path = Paths.get(System.getProperty("user.home")).resolve(".autofab")
): AutoCloseable {
    private val logger = LoggerFactory.getLogger("AutofabClient")
    private val jmdns = JmDNS.create(InetAddress.getLocalHost())

    private val keyManager = KeyManager()
    private val keyPair = keyManager.getOwnKeyPair(configDirectory)

    private val zmqCtx = ZContext()

    val availableServices = HashMap<String, ServiceInfo>()

    init {
        // Add a service listener
        jmdns.addServiceListener(serviceType, object: ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                logger.info("Service added: " + event.info)
            }

            override fun serviceRemoved(event: ServiceEvent) {
                logger.info("Service removed: " + event.info)
                availableServices.remove(event.info.name)
            }

            override fun serviceResolved(event: ServiceEvent) {
                logger.info("Service resolved: " + event.info)
                availableServices[event.info.name] = event.info
            }
        })
    }

    fun listAvailableHosts(): List<InetAddress> {
        return availableServices.values.flatMap { it.inetAddresses.toList() }
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun registerWith(host: InetAddress, withSocket: ZMQ.Socket? = null) {
        ZContext().use { context ->
            var localAddress = "127.0.0.1"
            DatagramSocket().use { socket ->
                socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
                localAddress = socket.localAddress.hostAddress
            }

            val socket: ZMQ.Socket = withSocket ?: context.createSocket(SocketType.REQ)
            socket.connect("tcp://${host.hostAddress}:4223")

            socket.sendMore("REGISTER")
            socket.sendMore(localAddress)
            socket.send(Base64.encode(keyPair.public.encoded))

            val registerResponse = socket.recvStr()
            if(registerResponse != "REGISTER OK") {
                throw IllegalStateException("Registration with ${host.hostAddress} failed")
            }
        }
    }

    /**
     * @param catchErrors if false it will fail if any client causes an error.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun shutdownLaunchedProcesses(force: Boolean = false, catchErrors: Boolean = true) {
        logger.info("Local address is ${InetAddress.getLocalHost().hostAddress}")

        var localAddress = "127.0.0.1"
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            localAddress = socket.localAddress.hostAddress
        }

        val context = zmqCtx
        availableServices.forEach { id, info ->
            thread(isDaemon = true) {
                try {
                    // Socket to talk to clients
                    val socket: ZMQ.Socket = context.createSocket(SocketType.REQ)
                    socket.connect("tcp://${info.inetAddresses.first().hostAddress}:4223")

                    logger.info("Shutting down processes on ${info.name}")

                    if(!force) {
                        socket.send("SHUTDOWN")
                        val response = socket.recvStr()
                    } else {
                        socket.send("KILL")
                    }
                } catch (e: IllegalStateException) {
                    if (catchErrors) {
                        logger.error("lauch failed:", e)
                    } else {
                        throw e
                    }
                }
            }
        }

    }

    /**
     * @param catchErrors if false it will fail if any client causes an error.
     */
    @OptIn(ExperimentalEncodingApi::class)
    fun launchOnAvailableHosts(command: String, register: Boolean = false, catchErrors: Boolean = true) {
        logger.info("Local address is ${InetAddress.getLocalHost().hostAddress}")

        var localAddress = "127.0.0.1"
        DatagramSocket().use { socket ->
            socket.connect(InetAddress.getByName("8.8.8.8"), 10002)
            localAddress = socket.localAddress.hostAddress
        }

        val context = zmqCtx
        availableServices.forEach { id, info ->
            try {
                // Socket to talk to clients
                val socket: ZMQ.Socket = context.createSocket(SocketType.REQ)
                socket.connect("tcp://${info.inetAddresses.first().hostAddress}:4223")

                if (register) {
                    registerWith(info.inetAddresses.first(), socket)
                }

                val commandArray = command.split(" ")
//                    """S:\\jdk\\temurin-21.0.3.9\\bin\\java.exe -cp "S:/scenery/build/libs/*;S:/scenery/build/dependencies/*" -ea -Xmx16g -Dscenery.VulkanRenderer.UseOpenGLSwapchain=false -Dscenery.Renderer.Framelock=true -Dscenery.RunFullscreen=false-Dscenery.Renderer.Config=DeferredShadingStereo.yml -Dscenery.vr.Active=true -Dscenery.ScreenConfig=CAVEExample.yml -Dscenery.TrackerAddress=DTrack:body-0@224.0.1.1:5001 -Dscenery.ScreenName=front graphics.scenery.tests.examples.basic.TexturedCubeExample"""

                val payload = listOf(localAddress, "${commandArray.size}") + commandArray
                logger.debug("Payload string is ${payload.joinToString("\n")}")
                val signature = KeyManager.sign(keyPair.private, payload.joinToString("\n").toByteArray())
                socket.sendMore("LAUNCH")
                socket.sendMore(signature)
                payload.forEachIndexed { i, p ->
                    if (i == payload.size - 1) {
                        socket.send(p)
                    } else {
                        socket.sendMore(p)
                    }
                }

                val response = socket.recvStr()
                if (response != "LAUNCH EPIC OK") {
                    throw IllegalStateException("Could not execute launch of $command on ${info.inetAddresses.first().hostAddress}")
                }
                logger.info("Launched $command on ${info.hostAddress}")
            } catch (e: IllegalStateException){
                if (catchErrors) {
                    logger.error("lauch failed:", e)
                } else {
                    throw e
                }
            }
        }

    }

    override fun close() {
        availableServices.clear()
        jmdns.close()
        zmqCtx.close()
    }
}