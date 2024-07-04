package graphics.scenery

import org.slf4j.LoggerFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.JCheckBoxMenuItem
import javax.swing.JMenuItem
import javax.swing.JPopupMenu
import kotlin.system.exitProcess


class SystrayHandler {
    val enabled: Boolean
    private val logger = LoggerFactory.getLogger(SystrayHandler::class.java)
    val popup = JPopupMenu()

    init {
        //Check the SystemTray is supported
        if(!SystemTray.isSupported()) {
            logger.error("SystemTray is not supported")
            enabled = false
        } else {
            enabled = true
            val trayIcon = TrayIcon(ImageIO.read(javaClass.getResource("/systray-icon.png")))
            val tray = SystemTray.getSystemTray()

            // Create a pop-up menu components
            val aboutItem = JMenuItem("About")
            val exitItem = JMenuItem("Exit")
            exitItem.addActionListener { exitProcess(0) }

            //Add components to pop-up menu
            popup.add(aboutItem)
            popup.addSeparator()
            popup.add(exitItem)

            trayIcon.addMouseListener(object : MouseAdapter() {
                override fun mouseReleased(e: MouseEvent) {
                    maybeShowPopup(e)
                }

                override fun mousePressed(e: MouseEvent) {
                    maybeShowPopup(e)
                }

                private fun maybeShowPopup(e: MouseEvent) {
                    if(e.isPopupTrigger) {
                        popup.setLocation(e.x, e.y)
                        popup.setInvoker(popup)
                        popup.setVisible(true)
                    }
                }
            })

            try {
                tray.add(trayIcon)
            } catch(e: AWTException) {
                println("TrayIcon could not be added.")
            }
        }
    }

    fun addMenuItem(description: String, icon: Icon? = null, isToggle: Boolean = false, action: (JMenuItem) -> Any) {
        val menuItem = if(isToggle) {
            JCheckBoxMenuItem(description)
        } else {
            JMenuItem(description)
        }
        if(icon != null) {
            menuItem.icon = icon
        }

        menuItem.addActionListener { action(menuItem) }
        popup.add(menuItem)
    }
}