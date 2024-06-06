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
 * @requires (os.family == "mac")
 * @key headful
 * @run main MouseDragPopupTest
 */
public class MouseDragPopupTest {
    static boolean failed;
    static JFrame frame;
    static JPanel panel;
    static JPanel innerPanel;
    static JPopupMenu menu;
    static volatile Point srcPoint;
    static volatile Dimension d;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            createAndShowGUI();
            srcPoint = frame.getLocationOnScreen();
            d = frame.getSize();
        });
        srcPoint.translate(2*d.width/3, 3*d.height/4);

        final Point dstPoint = new Point(srcPoint);
        dstPoint.translate(4*d.width/15, 0);

        failed = false;
        Robot robot = new Robot();
        robot.setAutoDelay(100);

        robot.mouseMove(srcPoint.x, srcPoint.y);
        robot.waitForIdle();
        robot.delay(500);

        robot.mousePress(InputEvent.BUTTON2_DOWN_MASK);

        while (!srcPoint.equals(dstPoint)) {
            srcPoint.translate(sign(dstPoint.x - srcPoint.x), 0);
            robot.mouseMove(srcPoint.x, srcPoint.y);
        }
        robot.waitForIdle();

        robot.mouseRelease(InputEvent.BUTTON2_DOWN_MASK);
        robot.waitForIdle();

        if (failed) {
            throw new RuntimeException("Popup was shown, Test Failed.");
        }
    }

    public static int sign(int n) {
        return n < 0 ? -1 : n == 0 ? 0 : 1;
    }

    static void createAndShowGUI() {
        frame = new JFrame("MouseDragPopupTest");
        panel = new JPanel();
        innerPanel = new JPanel();
        menu = new JPopupMenu();

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

        menu.add("This should not appear (and does not under Linux/Windows)");
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
