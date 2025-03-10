/*
 * Copyright (c) 2004, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Label;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 5021183
 * @summary Tests Key Traversal doesn't crash PopupMenu
 * @key headful
 * @run main KeyTraversalCrash
 */

public class KeyTraversalCrash {
    private static Frame f;
    private static Label label;

    private static volatile Point loc;
    private static volatile Dimension dim;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(100);
            EventQueue.invokeAndWait(KeyTraversalCrash::createAndShowUI);
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                loc = label.getLocationOnScreen();
                dim = label.getSize();
            });

            robot.mouseMove(loc.x + 20, loc.y + 20);
            robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

            robot.mouseMove(loc.x + 25, loc.y + 25);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

            robot.keyPress(KeyEvent.VK_LEFT);
            robot.keyRelease(KeyEvent.VK_LEFT);

            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);

            // To close the popup, otherwise test fails on windows with timeout error
            robot.mouseMove(loc.x + dim.width - 20, loc.y + dim.height - 20);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() {
        f = new Frame("KeyTraversalCrash Test");
        final PopupMenu popup = new PopupMenu();
        for (int i = 0; i < 10; i++) {
            Menu menu = new Menu("Menu " + i);
            for(int j = 0; j < 10; j++) {
                MenuItem menuItem = new MenuItem("MenuItem " + j);
                menu.add(menuItem);
            }
            popup.add(menu);
        }
        label = new Label("Label");
        f.add(label);
        f.add(popup);
        label.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    popup.show(me.getComponent(), me.getX(), me.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent me) {
                if (me.isPopupTrigger()) {
                    popup.show(me.getComponent(), me.getX(), me.getY());
                }
            }
        });
        f.setSize(200, 200);
        f.setLocationRelativeTo(null);
        f.setVisible(true);
    }
}
