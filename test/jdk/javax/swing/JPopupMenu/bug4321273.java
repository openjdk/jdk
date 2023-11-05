/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.awt.Robot;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;


/*
 * @test
 * @bug 4321273
 * @summary NotSerializableException during the menu serialization
 * @key headful
 * @run main bug4321273
*/

public class bug4321273 {
    public static JFrame frame;
    public static JMenu menu;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                JMenuBar menuBar = new JMenuBar();
                frame = new JFrame();
                frame.setJMenuBar(menuBar);
                menu = new JMenu("Menu");
                menuBar.add(menu);
                menu.add(new JMenuItem("item 1"));
                menu.add(new JMenuItem("item 2"));
                menu.add(new JMenuItem("item 3"));
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                menu.doClick();
                try {
                    ByteArrayOutputStream byteArrayOutputStream =
                            new ByteArrayOutputStream();
                    ObjectOutputStream oos =
                            new ObjectOutputStream(byteArrayOutputStream);
                    oos.writeObject(menu);
                } catch (Exception se) {
                    throw new RuntimeException("NotSerializableException " +
                            "during the menu serialization", se);
                }
            });

            robot.waitForIdle();
            robot.delay(100);
            System.out.println("Test Passed!");
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
