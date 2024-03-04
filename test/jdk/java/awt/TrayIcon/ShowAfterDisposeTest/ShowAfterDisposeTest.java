/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @bug 6384984 8004032
 * @library ../../regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @summary TrayIcon try to dispay a tooltip when is not visible
 * @run main/manual ShowAfterDisposeTest
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class ShowAfterDisposeTest {
    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new jtreg.SkippedException("The test cannot be run because SystemTray is not supported.");
        }

        ShowAfterDisposeTest test = new ShowAfterDisposeTest();
        test.startTest();
    }

    public void startTest() throws Exception {
        String instructions =
            "1) When the test starts an icon is added to the SystemTray area.\n" +
            "2a) If you use Apple OS X,\n" +
            "    right click on this icon (it's important to click before the tooltip is shown).\n" +
            "    The icon should disappear.\n" +
            "2b) If you use other os (Windows, Linux, Solaris),\n" +
            "    double click on this icon (it's important to click before the tooltip is shown).\n" +
            "    The icon should disappear.\n" +
            "3) If the bug is reproducible then the test will fail without assistance.\n" +
            "4) Just press the 'pass' button.";

        PassFailJFrame.builder()
                .title("Test Instructions Frame")
                .instructions(instructions)
                .testTimeOut(10)
                .rows(10)
                .columns(45)
                .testUI(ShowAfterDisposeTest::showFrameAndIcon)
                .build()
                .awaitAndCheck();
    }

    private static JFrame showFrameAndIcon() {
        JFrame frame = new JFrame("ShowAfterDisposeTest");
        frame.setLayout(new BorderLayout());

        BufferedImage img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        Graphics g = img.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 32, 32);
        g.setColor(Color.RED);
        g.fillRect(6, 6, 20, 20);
        g.dispose();

        final SystemTray tray = SystemTray.getSystemTray();
        final TrayIcon icon = new TrayIcon(img);
        icon.setImageAutoSize(true);
        icon.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ev) {
                tray.remove(icon);
            }});

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException("Could not add icon to tray");
        }
        icon.setToolTip("tooltip");

        return frame;
    }
}
