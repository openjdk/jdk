/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
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
  test
  @bug       6380743 8158380
  @summary   Submenu should be shown by mnemonic key press.
  @author    anton.tarasov@...: area=awt.focus
  @run       applet SubMenuShowTest.html
*/

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import java.applet.Applet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.InvocationTargetException;
import test.java.awt.regtesthelpers.Util;
import jdk.testlibrary.OSInfo;

public class SubMenuShowTest extends Applet {
    Robot robot;
    JFrame frame = new JFrame("Test Frame");
    JMenuBar bar = new JMenuBar();
    JMenu menu = new JMenu("Menu");
    JMenu submenu = new JMenu("More");
    JMenuItem item = new JMenuItem("item");
    AtomicBoolean activated = new AtomicBoolean(false);

    public static void main(String[] args) {
        SubMenuShowTest app = new SubMenuShowTest();
        app.init();
        app.start();
    }

    public void init() {
        robot = Util.createRobot();
        robot.setAutoDelay(200);
        robot.setAutoWaitForIdle(true);

        // Create instructions for the user here, as well as set up
        // the environment -- set the layout manager, add buttons,
        // etc.
        this.setLayout (new BorderLayout ());
    }

    public void start() {
        menu.setMnemonic('f');
        submenu.setMnemonic('m');
        menu.add(submenu);
        submenu.add(item);
        bar.add(menu);
        frame.setJMenuBar(bar);
        frame.pack();

        item.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    System.out.println(e.toString());
                    synchronized (activated) {
                        activated.set(true);
                        activated.notifyAll();
                    }
                }
            });

        frame.setVisible(true);

        boolean isMacOSX = (OSInfo.getOSType() == OSInfo.OSType.MACOSX);
        if (isMacOSX) {
            robot.keyPress(KeyEvent.VK_CONTROL);
        }
        robot.keyPress(KeyEvent.VK_ALT);
        robot.keyPress(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_F);
        robot.keyRelease(KeyEvent.VK_ALT);

        if (isMacOSX) {
            robot.keyRelease(KeyEvent.VK_CONTROL);
        }

        robot.keyPress(KeyEvent.VK_M);
        robot.keyRelease(KeyEvent.VK_M);
        robot.keyPress(KeyEvent.VK_SPACE);
        robot.keyRelease(KeyEvent.VK_SPACE);

        if (!Util.waitForCondition(activated, 2000)) {
            throw new TestFailedException("a submenu wasn't activated by mnemonic key press");
        }

        System.out.println("Test passed.");
    }
}

class TestFailedException extends RuntimeException {
    TestFailedException(String msg) {
        super("Test failed: " + msg);
    }
}

