/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8267374
 * @key headful
 * @requires (os.family == "mac")
 * @summary Verifies menu closes when main window is resized
 * @run main TestUngrab
 */

import java.awt.Point;
import java.awt.Dimension;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;

public class TestUngrab {
    /**
     * Menu (JMenuBar and JPopupMenu) not hidden when resizing the window.
     * -> UngrabEvent not sent.
     */
    static JMenu menu;
    static JFrame frame;
    static volatile Point loc;
    static volatile Point point;
    static volatile Dimension dim;
    static volatile int width;
    static volatile int height;
    static volatile boolean popupVisible;

    private static void createAndShowGUI() {
        frame = new JFrame();
        JMenuBar mb = new JMenuBar();
        menu = new JMenu("Menu");
        menu.add(new JMenuItem("Item 1"));
        menu.add(new JMenuItem("Item 2"));
        mb.add(menu);

        frame.setJMenuBar(mb);
        frame.setSize(300, 300);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            SwingUtilities.invokeAndWait(() -> createAndShowGUI());
            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                point = menu.getLocationOnScreen();
                dim = menu.getSize();
            });
            robot.mouseMove(point.x + dim.width / 2, point.y + dim.height / 2);
            robot.waitForIdle();
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();

            SwingUtilities.invokeAndWait(() -> {
                loc = frame.getLocationOnScreen();
                width = frame.getWidth();
                height = frame.getHeight();
            });
            robot.mouseMove(loc.x + width, loc.y + height);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
                popupVisible = menu.isPopupMenuVisible();
            });
            System.out.println("isPopupMenuVisible " + popupVisible);
            if (popupVisible) {
                throw new RuntimeException("popup menu not closed on resize");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

}
