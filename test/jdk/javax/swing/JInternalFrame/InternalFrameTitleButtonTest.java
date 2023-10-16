/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8140527
 * @key headful
 * @requires (os.family == "windows")
 * @summary InternalFrame has incorrect title button width
 * @run main InternalFrameTitleButtonTest
 */

import java.awt.Component;
import java.awt.Robot;

import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicInternalFrameUI;

public class InternalFrameTitleButtonTest {

    private static JFrame frame;
    private static JInternalFrame iframe;

    public static void main(String[] args) throws Exception {
        String osName = System.getProperty("os.name");
        if (!osName.toLowerCase().contains("win")) {
            System.out.println("The test is applicable only for Windows.");
            return;
        }

        UIManager.setLookAndFeel(
                   "com.sun.java.swing.plaf.windows.WindowsClassicLookAndFeel");
        try {
            test(2);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        UIManager.setLookAndFeel(
              "com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        try {
            test(14);
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }

        System.out.println("ok");
    }

    private static void test(final int widthAdd) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame();

            JDesktopPane pane = new JDesktopPane();
            frame.setContentPane(pane);
            frame.setSize(400, 400);
            frame.setVisible(true);

            iframe = new JInternalFrame("Mail Reader", true,
                    true, true, true);
            iframe.setSize(200, 200);
            pane.add(iframe);
            iframe.setVisible(true);
        });

        Robot robot = new Robot();
        robot.waitForIdle();
        robot.delay(1000);

        SwingUtilities.invokeAndWait(() -> {
            JComponent title = ((BasicInternalFrameUI) iframe.getUI()).getNorthPane();
            for (int i = 0; i < title.getComponentCount(); i++) {
                Component c = title.getComponent(i);
                if (c instanceof JButton button
                    && !testButtonSize(button, widthAdd)) {
                    throw new RuntimeException("Wrong title icon size");
                }
            }
        });
    }

    private static boolean testButtonSize(final JButton button,
                                          final int widthAdd) {
        int height = UIManager.getInt("InternalFrame.titleButtonHeight") - 4;
        Icon icon = button.getIcon();
        return height == button.getHeight()
               && (height + widthAdd) == button.getWidth()
               && height == icon.getIconHeight()
               && (height + widthAdd) == icon.getIconWidth();
    }
}

