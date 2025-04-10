/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4215380
 * @summary Internal Frame should get focus
 * @key headful
 * @run main bug4215380
 */

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.Point;
import java.awt.Robot;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class bug4215380 {

    private static String button;
    private static JButton b;
    private static JFrame frame;
    private static JInternalFrame jif;
    private static volatile Point loc;
    private static volatile Dimension size;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("bug4215380");
                JDesktopPane desktop = new JDesktopPane();
                frame.add(desktop, BorderLayout.CENTER);

                jif = iFrame(1);
                desktop.add(jif, JLayeredPane.DEFAULT_LAYER);
                desktop.add(iFrame(2), JLayeredPane.DEFAULT_LAYER);
                frame.setSize(200, 200);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                loc = b.getLocationOnScreen();
                size = b.getSize();
            });
            robot.mouseMove(loc.x + size.width / 2, loc.y + size.height / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(500);
            if (!(jif.isSelected()) && !button.equals("Frame 1")) {
                throw new RuntimeException("Internal frame \"Frame 1\" should be selected...");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static JInternalFrame iFrame(int i) {
        JInternalFrame frame = new JInternalFrame("Frame " + i);
        JPanel panel = new JPanel();
        JButton bt = new JButton("Button " + i);
        if (i == 1) {
            b = bt;
        }
        bt.addActionListener(e -> button = ((JButton)e.getSource()).getText());

        panel.add(bt);

        frame.getContentPane().add(panel);
        frame.setBounds(10, i * 80 - 70, 120, 90);
        frame.setVisible(true);
        return frame;
    }
}
