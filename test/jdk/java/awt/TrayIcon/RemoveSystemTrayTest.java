/*
 * Copyright (c) 2006, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.AWTException;
import java.awt.Button;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import jtreg.SkippedException;
import sun.awt.AppContext;
import sun.awt.SunToolkit;

/*
 * @test
 * @bug 6390934
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @modules java.desktop/sun.awt
 * @summary To check if TrayIcon msg is displayed when there is no system tray.
 * @run main/manual RemoveSystemTrayTest
 */

public class RemoveSystemTrayTest {
    private static SystemTray tray;
    private static TrayIcon icon;
    private static final ThreadGroup threadGroup
            = new ThreadGroup("Test thread group");
    private static AppContext appContext = null;
    private static final Object lock = new Object();
    // stores information whether AppContext was created to prevent spurious wakeups
    private static volatile boolean isCreated;

    private static final String INSTRUCTIONS = """
                1) When the test starts an icon is added to the SystemTray area.
                2) Remove the SystemTray manually (if it's impossible just press PASS).
                3) If you still see the tray icon in the tray area region
                   then press FAIL else goto next step.
                4) Press button 'Show message'.
                5) If you see the tray icon message then press FAIL else press PASS.
                """;

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new SkippedException("Test not applicable as"
                                       + " System Tray not supported");
        }
        try {
            PassFailJFrame.builder()
                          .title("FocusLostAfterTrayTest Instructions")
                          .instructions(INSTRUCTIONS)
                          .columns(40)
                          .testUI(RemoveSystemTrayTest::createAndShowUI)
                          .logArea(4)
                          .build()
                          .awaitAndCheck();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (tray != null) {
                    tray.remove(icon);
                }
            });
        }
    }

    private static Frame createAndShowUI() {
        Frame frame = new Frame("RemoveSystemTrayTest");
        Button button = new Button("Show message");
        button.addActionListener(e -> displayMessage());
        frame.add(button);
        frame.setSize(150, 100);

        createAppContext();
        final Runnable runnable = RemoveSystemTrayTest::addTrayIcon;
        final Thread thread = new Thread(threadGroup, runnable,
                                         "Thread to add icon");
        thread.start();
        return frame;
    }


    private static void addTrayIcon() {
        BufferedImage img = new BufferedImage(32, 32,
                                              BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 32);
        g.setColor(Color.RED);
        g.fillRect(6, 6, 20, 20);
        g.dispose();

        tray = SystemTray.getSystemTray();
        icon = new TrayIcon(img);
        icon.setImageAutoSize(true);

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException("Error while adding"
                                       + " icon to system tray", e);
        }
    }

    private static void createAppContext() {
        final Runnable runnable = () -> {
            appContext = SunToolkit.createNewAppContext();
            synchronized (lock) {
                isCreated = true;
                lock.notifyAll();
            }
        };

        final Thread thread = new Thread(threadGroup, runnable, "Test thread");
        synchronized (lock) {
            thread.start();
            while (!isCreated) {
                try {
                    lock.wait();
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }

        if (appContext == null) {
            PassFailJFrame.log("WARNING: Failed to create AppContext.");
        } else {
            PassFailJFrame.log("AppContext was created.");
        }
    }

    private static void displayMessage() {
        final Runnable runnable = () -> icon.displayMessage("caption",
                                                "TrayIcon Msg", TrayIcon.MessageType.INFO);
        final Thread thread = new Thread(threadGroup, runnable,
                                         "Thread to display message");
        thread.start();
    }
}
