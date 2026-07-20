/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import jtreg.SkippedException;

/*
 * @test
 * @bug 6267936
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @summary Tests that the previous image in TrayIcon is cleared
 *          when a new image is set
 * @run main/manual ClearPrevImageTest
 */

public class ClearPrevImageTest {
    private static SystemTray tray;
    private static TrayIcon icon;
    private static final String INSTRUCTIONS = """
            This test checks that the previous image in TrayIcon is cleared
            when a new image is set.

            When the test starts, a RED square TrayIcon is added
            to the SystemTray (also, called Taskbar Status Area in MS Windows,
            Notification Area in, GNOME and System Tray in KDE).

            You should see it change into YELLOW after 5 seconds.
            If you still see RED TrayIcon after 5 seconds,
            press FAIL, otherwise press PASS
            """;


    public static void main(String[] args) throws Exception {
         if (!SystemTray.isSupported()) {
             throw new SkippedException("Test not applicable as"
                                        + " System Tray not supported");
         }

        PassFailJFrame passFailJFrame
                = PassFailJFrame.builder()
                                .title("TrayIcon Change Test Instructions")
                                .instructions(INSTRUCTIONS)
                                .columns(40)
                                .build();

        EventQueue.invokeAndWait(ClearPrevImageTest::createAndShowTrayIcon);
        try {
            changeTrayIcon();
            passFailJFrame.awaitAndCheck();
        } catch (Exception e) {
            throw new RuntimeException("Test failed: ", e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (tray != null) {
                    tray.remove(icon);
                }
            });
        }
    }

    private static void createAndShowTrayIcon() {
        Image img1 = createIcon(Color.RED);
        tray = SystemTray.getSystemTray();
        icon = new TrayIcon(img1);
        icon.setImageAutoSize(true);

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException("Error while adding"
                                       + " icon to system tray", e);
        }
    }

    private static void changeTrayIcon() {
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Image img2 = createIcon(Color.YELLOW);
        icon.setImage(img2);
    }

    private static Image createIcon(Color color) {
        BufferedImage image = new BufferedImage(16, 16,
                                                BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                           RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(color);
        g.fillRect(0, 0, 16, 16);
        g.dispose();
        return image;
    }
}
