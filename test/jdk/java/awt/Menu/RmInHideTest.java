/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4039387
 * @summary Checks that calling Frame.remove() within hide() doesn't
 *          cause SEGV
 * @key headful
 * @run main RmInHideTest
 */

import java.awt.Button;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

public class RmInHideTest {
    static volatile Point point;
    static RmInHideTestFrame frame;
    static volatile Dimension dimension;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                frame = new RmInHideTestFrame();
                frame.setSize(200, 200);
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                point = frame.getButtonLocation();
                dimension = frame.getButtonDimension();
            });
            robot.mouseMove(point.x + dimension.width / 2, point.y + dimension.height / 2);
            robot.mousePress(MouseEvent.BUTTON2_DOWN_MASK);
            robot.mouseRelease(MouseEvent.BUTTON2_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(100);
            System.out.println("Test pass");
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    static class RmInHideTestFrame extends Frame implements ActionListener {
        MenuBar menubar = null;
        Button b;

        public RmInHideTestFrame() {
            super("RmInHideTest");
            b = new Button("Hide");
            b.setActionCommand("hide");
            b.addActionListener(this);
            add("Center", b);

            MenuBar bar = new MenuBar();

            Menu menu = new Menu("Test1", true);
            menu.add(new MenuItem("Test1A"));
            menu.add(new MenuItem("Test1B"));
            menu.add(new MenuItem("Test1C"));
            bar.add(menu);

            menu = new Menu("Test2", true);
            menu.add(new MenuItem("Test2A"));
            menu.add(new MenuItem("Test2B"));
            menu.add(new MenuItem("Test2C"));
            bar.add(menu);
            setMenuBar(bar);
        }

        @Override
        public Dimension minimumSize() {
            return new Dimension(200, 200);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            String cmd = e.getActionCommand();
            if (cmd.equals("hide")) {
                hide();
                try {
                    Thread.currentThread().sleep(2000);
                } catch (InterruptedException ex) {
                    // do nothing
                }
                show();
            }
        }

        @Override
        public void hide() {
            menubar = getMenuBar();
            if (menubar != null) {
                remove(menubar);
            }
            super.hide();
        }


        @Override
        public void show() {
            if (menubar != null) {
                setMenuBar(menubar);
            }
            super.show();
        }

        public Point getButtonLocation() {
            return b.getLocationOnScreen();
        }

        public Dimension getButtonDimension() {
            return b.getSize();
        }
    }
}
