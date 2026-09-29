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

/*
 * @test
 * @bug 4079449
 * @key headful
 * @summary MenuItem objects return null if they are activated by shortcut
 */

import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Point;
import java.awt.Robot;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_META;

public class ActionCommandTest implements ActionListener {

    static volatile Frame frame;
    static volatile boolean event = false;
    static volatile boolean failed = false;
    static final String ITEMTEXT = "Testitem";

    static void createUI() {
        frame = new Frame("ActionCommand Menu Shortcut Test");
        MenuBar mb = new MenuBar();
        Menu m = new Menu("Test");
        MenuItem mi = new MenuItem(ITEMTEXT, new MenuShortcut(KeyEvent.VK_T));
        mi.addActionListener(new ActionCommandTest());
        m.add(mi);
        mb.add(m);
        frame.setMenuBar(mb);
        frame.setBounds(50, 400, 200, 200);
        frame.setVisible(true);
    }

    public static void main(String[] args ) throws Exception {

        EventQueue.invokeAndWait(ActionCommandTest::createUI);
        try {
            Robot robot = new Robot();

            robot.waitForIdle();
            robot.delay(2000);

            // Ensure window has focus
            Point p = frame.getLocationOnScreen();
            robot.mouseMove(p.x + frame.getWidth() / 2, p.y + frame.getHeight() / 2);
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
            robot.waitForIdle();
            robot.delay(2000);

            // invoke short cut.
            robot.keyPress(KeyEvent.VK_T);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_T);
            robot.waitForIdle();
            robot.delay(2000);
        } finally  {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
        if (failed) {
           throw new RuntimeException("No actioncommand");
        }
    }

    // Since no ActionCommand is set, this should be the menuitem's label.
    public void actionPerformed(ActionEvent e) {
        event = true;
        String s = e.getActionCommand();
        if (s == null || !s.equals(ITEMTEXT)) {
            failed = true;
        }
    }

}
