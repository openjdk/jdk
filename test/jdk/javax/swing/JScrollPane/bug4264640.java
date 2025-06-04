/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Robot;

/*
 * @test
 * @bug 4264640
 * @summary Tests that JScrollPane sets correct position of its column header view
 * @key headful
 * @run main bug4264640
 */

public class bug4264640 {
    public static JFrame frame;
    public static JButton b;
    public static Robot robot;
    public static volatile int yPos;

    public static void main(String[] args) throws Exception {
        try {
            robot = new Robot();
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame("Scroll Pane test");
                JScrollPane scroller = new JScrollPane();
                b = new JButton("This is BUG !");
                b.setBounds(12, 12, 169, 133);
                scroller.setColumnHeaderView(b);

                Container pane = frame.getContentPane();
                pane.setLayout(new BorderLayout());
                pane.add(scroller);
                frame.setSize(200,200);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            SwingUtilities.invokeAndWait(() -> {
               yPos = b.getY();
            });
            if (yPos != 0) {
                throw new RuntimeException("Failed: Y = " + yPos + " (should be 0)");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
        System.out.println("Test Passed!");
    }
}
