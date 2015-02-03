/*
 * Copyright (c) 2006, 2014, Oracle and/or its affiliates. All rights reserved.
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
/* @test
   @bug 6249972
   @summary Tests that JMenuItem(String,int) handles lower-case mnemonics properly.
   @library ../../../../lib/testlibrary
   @build ExtendedRobot
   @author Mikhail Lapshin
   @run main bug6249972
 */

import javax.swing.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

public class bug6249972 implements ActionListener {


    private JFrame frame;
    private JMenu menu;
    private volatile boolean testPassed = false;

    public static void main(String[] args) throws Exception {
        bug6249972 bugTest = new bug6249972();
        bugTest.test();
    }

    public bug6249972() throws Exception {
        SwingUtilities.invokeAndWait(
                new Runnable() {
                    public void run() {
                        frame = new JFrame("bug6249972");
                        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                        JMenuBar bar = new JMenuBar();
                        frame.setJMenuBar(bar);

                        menu = new JMenu("Problem");
                        bar.add(menu);

                        JMenuItem item = new JMenuItem("JMenuItem(String,'z')", 'z');
                        item.addActionListener(bug6249972.this);
                        menu.add(item);

                        frame.setLocationRelativeTo(null);
                        frame.pack();
                        frame.setVisible(true);
                    }
                }
        );
    }


    private void test() throws Exception {
        ExtendedRobot robot = new ExtendedRobot();
        robot.waitForIdle();
        java.awt.Point p = menu.getLocationOnScreen();
        java.awt.Dimension size = menu.getSize();
        p.x += size.width / 2;
        p.y += size.height / 2;
        robot.mouseMove(p.x, p.y);
        robot.click();
        robot.delay(100);

        robot.waitForIdle();
        robot.type(KeyEvent.VK_Z);

        robot.waitForIdle();
        frame.dispose(); // Try to stop the event dispatch thread

        if (!testPassed) {
            throw new RuntimeException("JMenuItem(String,int) does not handle " +
                    "lower-case mnemonics properly.");
        }

        System.out.println("Test passed");
    }

    public void actionPerformed(ActionEvent e) {
        // We are in the actionPerformed() method -
        // JMenuItem(String,int) handles lower-case mnemonics properly
        testPassed = true;
    }
}
