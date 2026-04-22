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
import java.awt.Graphics;
import java.awt.SystemTray;
import java.awt.TrayIcon;
import java.awt.image.BufferedImage;

import jtreg.SkippedException;

/*
 * @test
 * @bug 6267943
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers /test/lib
 * @build PassFailJFrame jtreg.SkippedException
 * @summary Tests the possibility of selecting a tray icon with the keyboard.
 * @run main/manual TrayIconKeySelectTest
 */

public class TrayIconKeySelectTest {
    private static SystemTray tray;
    private static TrayIcon icon;
    private static final String INSTRUCTIONS = """
            Tests that TrayIcon is selectable with the keyboard
            When the test is started you will see three-color icon
            in the system tray.

            1. Bring the focus to the icon with TAB. Press ENTER key.
            - One or more ActionEvent should be generated
               (see the output area of the test)

            2. Bring the focus again to the icon. Press SPACE key twice.
            - One or more ActionEvent should be generated.

            3. Bring the focus again to the icon. Click on the icon with
            the LEFT mouse button twice.
            - One or more ActionEvent should be generated.

            4. Again bring the focus to the icon. Click on the icon with
            the LEFT mouse button just once.
            - NO ActionEvent should be generated.

            5. Repeat the 4th step with other mouse buttons.

            If all the above are true press PASS, else FAIL
            """;

    public static void main(String[] args) throws Exception {
        if (!SystemTray.isSupported()) {
            throw new SkippedException("Test not applicable as"
                                       + " System Tray not supported");
        }
        PassFailJFrame passFailJFrame;
        try {
            passFailJFrame
                    = PassFailJFrame.builder()
                                    .title("TrayIconKeySelectTest Instructions")
                                    .instructions(INSTRUCTIONS)
                                    .columns(40)
                                    .logArea()
                                    .build();

            EventQueue.invokeAndWait(TrayIconKeySelectTest::createAndShowTrayIcon);
            passFailJFrame.awaitAndCheck();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (tray != null) {
                    tray.remove(icon);
                }
            });
        }
    }

    private static void createAndShowTrayIcon() {
        BufferedImage im = new BufferedImage(16, 16,
                                             BufferedImage.TYPE_INT_ARGB);
        Graphics gr = im.createGraphics();
        gr.setColor(Color.white);
        gr.fillRect(0, 0, 16, 5);
        gr.setColor(Color.blue);
        gr.fillRect(0, 5, 16, 10);
        gr.setColor(Color.red);
        gr.fillRect(0, 10, 16, 16);
        gr.dispose();

        tray = SystemTray.getSystemTray();
        icon = new TrayIcon(im);
        icon.setImageAutoSize(true);
        icon.addActionListener(e -> PassFailJFrame.log(e.toString()));

        try {
            tray.add(icon);
        } catch (AWTException e) {
            throw new RuntimeException("Error while adding"
                                       + " icon to system tray", e);
        }
    }
}
