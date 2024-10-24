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
 * @bug 4034665
 * @key headful
 * @summary Function keys should work correctly as shortcuts
 */

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Menu;
import java.awt.MenuBar;
import java.awt.MenuItem;
import java.awt.MenuShortcut;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.VK_CONTROL;
import static java.awt.event.KeyEvent.VK_META;

public class FunctionKeyShortcut implements ActionListener {

    static volatile Frame frame;
    static volatile boolean event = false;
    static volatile boolean failed = false;

    static final boolean isMac = System.getProperty("os.name").contains("OS X");

    static void createUI() {
        frame = new Frame("Function Key Menu Shortcut Test");
        MenuBar mb = new MenuBar();
        Menu m = new Menu("Test");
        MenuItem mi1 = new MenuItem("Function key 1", new MenuShortcut(KeyEvent.VK_F1));
        MenuItem mi2 = new MenuItem("Function key 2", new MenuShortcut(KeyEvent.VK_F2));
        MenuItem mi3 = new MenuItem("Function key 3", new MenuShortcut(KeyEvent.VK_F3));
        MenuItem mi4 = new MenuItem("Function key 4", new MenuShortcut(KeyEvent.VK_F4));
        MenuItem mi5 = new MenuItem("Function key 5", new MenuShortcut(KeyEvent.VK_F5));
        MenuItem mi6 = new MenuItem("Function key 6", new MenuShortcut(KeyEvent.VK_F6));
        MenuItem mi7 = new MenuItem("Function key 7", new MenuShortcut(KeyEvent.VK_F7));
        MenuItem mi8 = new MenuItem("Function key 8", new MenuShortcut(KeyEvent.VK_F8));
        MenuItem mi9 = new MenuItem("Function key 8", new MenuShortcut(KeyEvent.VK_F9));

        FunctionKeyShortcut fks = new FunctionKeyShortcut();
        mi1.addActionListener(fks);
        mi2.addActionListener(fks);
        mi3.addActionListener(fks);
        mi4.addActionListener(fks);
        mi5.addActionListener(fks);
        mi6.addActionListener(fks);
        mi7.addActionListener(fks);
        mi8.addActionListener(fks);
        mi9.addActionListener(fks);

        m.add(mi1);
        m.add(mi2);
        m.add(mi3);
        m.add(mi4);
        m.add(mi5);
        m.add(mi6);
        m.add(mi7);
        m.add(mi8);
        m.add(mi9);

        mb.add(m);
        frame.setMenuBar(mb);
        frame.setBounds(50,400,200,200);
        frame.setVisible(true);
    }

    public static void main(String[] args ) throws Exception {

        EventQueue.invokeAndWait(FunctionKeyShortcut::createUI);
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

            int mod = (isMac) ? KeyEvent.VK_META : KeyEvent.VK_CONTROL;
            robot.keyPress(mod);
            robot.keyPress(KeyEvent.VK_F1);
            robot.delay(50);
            robot.keyRelease(KeyEvent.VK_F1);
            robot.keyRelease(mod);
            robot.waitForIdle();
            robot.delay(2000);
        } finally  {
            if (frame != null) {
                EventQueue.invokeAndWait(frame::dispose);
            }
        }
        if (!event || failed) {
           throw new RuntimeException("No actioncommand");
        }
    }

    public void actionPerformed(ActionEvent e) {
        System.out.println("Got " + e);
        String s = e.getActionCommand();
        event = true;
        if (s == null || !s.equals("Function key 1")) {
            failed = true;
        }
    }

}
