/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

/*
 * @test
 * @bug 8315655
 * @summary Verifies Right click and dragging over a component with a popup menu will not open the popup
 * @key headful
 * @run main MouseDragPopupTest
 */
public class MouseDragPopupTest {
    static JFrame frame;
    static JPanel panel;
    static Robot robot;
    static volatile boolean failed;
    static volatile Point srcPoint;
    static volatile Dimension d;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            robot.setAutoWaitForIdle(true);
            robot.setAutoDelay(100);

            SwingUtilities.invokeAndWait(() -> {
                createAndShowGUI();
            });
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                srcPoint = frame.getLocationOnScreen();
                d = frame.getSize();
            });
            srcPoint.translate(2 * d.width / 3, 3 * d.height / 4);

            final Point dstPoint = new Point(srcPoint);
            dstPoint.translate(4 * d.width / 15, 0);

            robot.mouseMove(srcPoint.x, srcPoint.y);

            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);

            while (!srcPoint.equals(dstPoint)) {
                srcPoint.translate(sign(dstPoint.x - srcPoint.x), 0);
                robot.mouseMove(srcPoint.x, srcPoint.y);
            }

            if (failed) {
                throw new RuntimeException("Popup was shown, Test Failed.");
            }
        } finally {
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    static void createAndShowGUI() {
        frame = new JFrame("MouseDragPopupTest");
        panel = new JPanel();
        JPanel innerPanel = new JPanel();
        JPopupMenu menu = new JPopupMenu();

        menu.addPopupMenuListener(new PopupMenuListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                failed = true;
            }

            @Override
            public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

            @Override
            public void popupMenuCanceled(PopupMenuEvent e) {}
        });

        menu.add("This should not appear");
        innerPanel.setComponentPopupMenu(menu);

        panel.add(new JLabel("Right click and drag from here"));
        panel.add(innerPanel);
        panel.add(new JLabel("to here"));

        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
