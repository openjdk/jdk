/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
import jdk.test.lib.Platform;
import jtreg.SkippedException;

import java.awt.TrayIcon;
import java.awt.SystemTray;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.AWTException;
import java.awt.event.MouseEvent;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.image.BufferedImage;

/*
 * @test
 * @key headful
 * @summary Check if a action performed event is received when TrayIcon display
 *          message is clicked on.
 * @modules java.desktop/java.awt:open
 * @library
 *          /java/awt/patchlib
 *          /java/awt/TrayIcon
 *          /lib/client
 *          /test/lib
 * @build
 *          java.desktop/java.awt.Helper
 *          jdk.test.lib.Platform
 *          jtreg.SkippedException
 *          ExtendedRobot
 *          SystemTrayIconHelper
 * @run main TrayIconPopupClickTest
 */

public class TrayIconPopupClickTest {

    TrayIcon icon;
    ExtendedRobot robot;
    volatile boolean actionPerformed = false;

    public static void main(String[] args) throws Exception {
        if (Platform.isOnWayland()) {
            // The current robot implementation does not support
            // clicking in the system tray area.
            throw new SkippedException("Skipped on Wayland");
        }

        if (!SystemTray.isSupported()) {
            throw new SkippedException("SystemTray is not supported on this platform.");
        }

        if (Platform.isWindows()) {
            System.err.println("Test can fail if the icon hides to a tray icons pool " +
                    "in Windows 7/10, which is behavior by default.\n" +
                    "Set \"Right mouse click\" -> \"Customize notification icons\" -> " +
                    "\"Always show all icons and notifications on the taskbar\" true " +
                    "to avoid this problem. Or change behavior only for Java SE " +
                    "tray icon.");
        }

        new TrayIconPopupClickTest().doTest();
    }

    TrayIconPopupClickTest() throws Exception {
        robot = new ExtendedRobot();
        EventQueue.invokeAndWait(this::initializeGUI);
        robot.waitForIdle(1000);
    }

    private void initializeGUI() {
        SystemTray tray = SystemTray.getSystemTray();
        icon = new TrayIcon(new BufferedImage(20, 20, BufferedImage.TYPE_INT_RGB), "Sample Icon");
        icon.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent event) {
                icon.displayMessage("Sample Icon", "This is a test message for the tray icon", TrayIcon.MessageType.INFO);
            }
        });

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException(e);
        }

        icon.addActionListener(e -> actionPerformed = true);
    }

    void doTest() throws Exception {
        Point iconPosition = SystemTrayIconHelper.getTrayIconLocation(icon);
        if (iconPosition == null)
            throw new RuntimeException("Unable to find the icon location!");

        robot.mouseMove(iconPosition.x, iconPosition.y);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);

        robot.mouseMove(iconPosition.x, iconPosition.y + 10);
        robot.waitForIdle();
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(50);

        if (!actionPerformed)
            throw new RuntimeException("FAIL: ActionEvent not triggered when " +
                    "tray icon message was clicked on");
    }
}
