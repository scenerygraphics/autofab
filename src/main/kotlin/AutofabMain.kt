package graphics.scenery

import org.slf4j.LoggerFactory
import java.net.InetAddress
import java.nio.file.Paths
import javax.swing.JCheckBoxMenuItem
import kotlin.io.path.createDirectory
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

class AutofabMain {
    private val logger = LoggerFactory.getLogger("AutofabMain")
    val sh = SystrayHandler()
    val bonjour = BonjourService()
    var zmq: ZeroMQService? = null

    val configDirectory = Paths.get(System.getProperty("user.home")).resolve(".autofab")

    init {
        if(!configDirectory.exists() && !configDirectory.isDirectory()) {
            logger.warn("Config directory at $configDirectory does not exist, creating...")
            configDirectory.createDirectory()
        }
        logger.info("Using config directory at $configDirectory")

        val listenPort = 4223
        val host = InetAddress.getLocalHost()

        logger.info("Hello from the scenery autofab, listening on ${host.hostAddress}:$listenPort")

        bonjour.register(InetAddress.getLocalHost().hostName, listenPort, "")

        val km = KeyManager()
        val keyPair = km.getOwnKeyPair(configDirectory)

        sh.addMenuItem("Enable registration", icon = null, isToggle = true) { menuItem ->
            if(menuItem is JCheckBoxMenuItem) {
                val state = menuItem.isSelected
                menuItem.isSelected = state

                enableHostRegistration(state)
            }
        }

        zmq = ZeroMQService("tcp://${host.hostAddress}:$listenPort", keyPair, km, configDirectory)
    }

    fun enableHostRegistration(enabled: Boolean) {
        logger.info("Enabling host registration: $enabled")
        zmq?.registerEnabled = enabled
    }

    fun close() {
        bonjour.close()
        zmq?.close()
    }
}

fun main() {
    val app = AutofabMain()
}