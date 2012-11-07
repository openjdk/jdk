/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7160951
 * @summary [macosx] ActionListener called twice for JMenuItem using ScreenMenuBar
 * @author vera.akulova@oracle.com
 * @run main ActionListenerCalledTwiceTest
 */

import sun.awt.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ActionListenerCalledTwiceTest {
    static volatile int listenerCallCounter = 0;
    public static void main(String[] args) throws Exception {
        if (sun.awt.OSInfo.getOSType() != sun.awt.OSInfo.OSType.MACOSX) {
            System.out.println("This test is for MacOS only. Automatically passed on other platforms.");
            return;
        }
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                createAndShowGUI();
            }
        });
        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        robot.keyPress(KeyEvent.VK_META);
        robot.keyPress(KeyEvent.VK_E);
        robot.keyRelease(KeyEvent.VK_E);
        robot.keyRelease(KeyEvent.VK_META);
        toolkit.realSync();
        if (listenerCallCounter != 1) {
            throw new Exception("Test failed: ActionListener called " + listenerCallCounter + " times instead of 1!");
        }
    }

    private static void createAndShowGUI() {
        JMenuItem newItem = new JMenuItem("Exit");
        newItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.META_MASK));
        newItem.addActionListener(
            new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    listenerCallCounter++;
                }
            }
        );
        JMenu menu = new JMenu("Menu");
        menu.add(newItem);
        JMenuBar bar = new JMenuBar();
        bar.add(menu);
        JFrame frame = new JFrame("Test");
        frame.setJMenuBar(bar);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }
}
