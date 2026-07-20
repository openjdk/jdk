/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.MenuItem;
import java.awt.Panel;
import java.awt.Point;
import java.awt.PopupMenu;
import java.awt.Robot;

import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/*
 * @test
 * @bug 4083400
 * @key headful
 * @summary Tests that excessive popping up and down does not crash or
 *          throw an exception.
 * @run main StressTest
 */

public class StressTest {
    private static Frame fr;
    private static PopupTestPanel panel;

    private static volatile Point panelCenter;

    public static void main(String[] args) throws Exception {
        final int REPEAT_COUNT = 5;
        try {
            Robot robot = new Robot();
            robot.setAutoDelay(50);
            EventQueue.invokeAndWait(StressTest::createAndShowUI);
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> {
                Point loc = panel.getLocationOnScreen();
                Dimension dim = panel.getSize();
                panelCenter = new Point(loc.x + dim.width / 2, loc.y + dim.height / 2);
            });

            for (int i = 0; i < REPEAT_COUNT; i++) {
                robot.mouseMove(panelCenter.x + i * 2, panelCenter.y + i * 2);

                robot.mousePress(InputEvent.BUTTON3_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON3_DOWN_MASK);

                robot.mouseMove(panelCenter.x - i * 2, panelCenter.y - i * 2);

                robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    public static void createAndShowUI() {
        fr = new Frame("PopupMenu Test");
        panel = new PopupTestPanel();
        fr.add(panel);
        fr.setSize(300, 200);
        fr.setVisible(true);
    }

    static class PopupTestPanel extends Panel {

        static class Item extends MenuItem {
            public Item(String text) {
                super(text);
            }

            public boolean isEnabled() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }
                return super.isEnabled();
            }
        }

        final PopupMenu popup;

        public PopupTestPanel() {
            popup = new PopupMenu();
            popup.add(new Item("Soap"));
            popup.add(new Item("Sponge"));
            popup.add(new Item("Flannel"));
            popup.add(new Item("Mat"));
            popup.add(new Item("Towel"));
            add(popup);
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (e.isPopupTrigger()) {
                        showPopup(e);
                    }
                }

                private void showPopup(MouseEvent e) {
                    popup.show((Component) e.getSource(), e.getX(), e.getY());
                }
            });
        }
    }
}
