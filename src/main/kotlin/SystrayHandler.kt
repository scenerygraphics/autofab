package graphics.scenery

import org.slf4j.LoggerFactory
import java.awt.*
import javax.imageio.ImageIO
import kotlin.system.exitProcess


class SystrayHandler {
    val enabled: Boolean
    private val logger = LoggerFactory.getLogger(SystrayHandler::class.java)

    init {
        //Check the SystemTray is supported
        if(!SystemTray.isSupported()) {
            logger.error("SystemTray is not supported")
            enabled = false
        } else {
            enabled = true
            val popup = PopupMenu()
            val trayIcon = TrayIcon(ImageIO.read(javaClass.getResource("/systray-icon.png")))
            val tray = SystemTray.getSystemTray()

            // Create a pop-up menu components
            val aboutItem = MenuItem("About")
            val cb1 = CheckboxMenuItem("Set auto size")
            val cb2 = CheckboxMenuItem("Set tooltip")
            val displayMenu = Menu("Display")
            val errorItem = MenuItem("Error")
            val warningItem = MenuItem("Warning")
            val infoItem = MenuItem("Info")
            val noneItem = MenuItem("None")
            val exitItem = MenuItem("Exit")
            exitItem.addActionListener { exitProcess(0) }


            //Add components to pop-up menu
            popup.add(aboutItem)
            popup.addSeparator()
            popup.add(cb1)
            popup.add(cb2)
            popup.addSeparator()
            popup.add(displayMenu)
            displayMenu.add(errorItem)
            displayMenu.add(warningItem)
            displayMenu.add(infoItem)
            displayMenu.add(noneItem)
            popup.add(exitItem)

            trayIcon.popupMenu = popup

            try {
                tray.add(trayIcon)
            } catch(e: AWTException) {
                println("TrayIcon could not be added.")
            }
        }
    }
}