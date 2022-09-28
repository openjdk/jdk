/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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

/*
  @test
  @bug 6384984 8004032
  @library /java/awt/regtesthelpers
  @build PassFailJFrame
  @summary TrayIcon try to display a tooltip when is not visible
  @run main/manual ShowAfterDisposeTest
*/

import java.awt.AWTException;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

public class ShowAfterDisposeTest {

    public static void createTestUI() {
        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 32);
        g.setColor(Color.RED);
        g.fillRect(6, 6, 20, 20);
        g.dispose();

        SystemTray tray = SystemTray.getSystemTray();
        TrayIcon icon = new TrayIcon(img);
        icon.setImageAutoSize(true);
        icon.addActionListener( (e) -> tray.remove(icon));
        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException(e.getMessage());
        }
        icon.setToolTip("tooltip");
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        if (!SystemTray.isSupported()) {
            System.out.println("SystemTrayIcon is not supported in "
                    + System.getProperty("os.name"));
            return;
        }

        String testInstruction = """
                1) When the test starts an icon is added to the SystemTray area.
                2a) If you use Apple OS X,
                    right click on this icon (it's important to click before the tooltip is shown).
                    The icon should disappear.
                2b) If you use other os (Windows, Linux, Solaris),
                    double click on this icon (it's important to click before the tooltip is shown).
                    The icon should disappear.
                3) If the bug is reproducible then the test will fail without assistance.
                4) Just press the 'pass' button.
                """;

        PassFailJFrame passFailJFrame = new PassFailJFrame("ShownOnPack " +
                "Test Instructions", testInstruction, 5);
        EventQueue.invokeAndWait(ShowAfterDisposeTest::createTestUI);
        passFailJFrame.awaitAndCheck();
    }
}

