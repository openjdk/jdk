/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
   @bug 4187004
   @summary test that title/label is show on menu for Motif L&F
   @key headful
*/

import java.awt.Dimension;
import java.awt.Robot;
import javax.swing.JFrame;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4187004 {

    static volatile JPopupMenu m;
    static volatile Dimension d1, d2;

    public static void main(String[] args) throws Exception {
        try {
            SwingUtilities.invokeAndWait(bug4187004::createUI);

            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            d1 = m.getSize();
            SwingUtilities.invokeAndWait(bug4187004::hideMenu);
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(bug4187004::updateUI);
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(bug4187004::showMenu);
            robot.waitForIdle();
            robot.delay(1000);
            d2 = m.getSize();
        } finally {
            SwingUtilities.invokeAndWait(bug4187004::hideMenu);
        }
        System.out.println(d1);
        System.out.println(d2);
        if (d1.width <= d2.width) {
            throw new RuntimeException("Menu not updated");
        }
    }

   static void createUI() {
        try {
            UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        m = new JPopupMenu("One really long menu title");
        m.add("Item 1");
        m.add("Item 2");
        m.add("Item 3");
        m.add("Item 4");
        m.pack();
        m.setVisible(true);
    }

    static void hideMenu() {
        m.setVisible(false);
    }

    static void showMenu() {
        m.setVisible(true);
    }

    static void updateUI() {
        m.setLabel("short");
        m.pack();
    }
}
