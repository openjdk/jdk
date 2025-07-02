/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @requires (os.family == "mac")
 * @bug 8008222
 * @summary Verifies selectNextChangeLead is implemented in AquaL&F
 * @run main TestTreeLeadSelChange
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

public class TestTreeLeadSelChange {
    static JTree tree;
    static JFrame frame;
    public static void main(String[]  args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                DefaultMutableTreeNode sports = new DefaultMutableTreeNode("sports");
                sports.add(new DefaultMutableTreeNode("basketball"));
                sports.add(new DefaultMutableTreeNode("football"));
                sports.add(new DefaultMutableTreeNode("cricket"));
                sports.add(new DefaultMutableTreeNode("tennis"));

                tree = new JTree(sports);
                tree.setSelectionRow(2);

                frame.getContentPane().add(tree);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
            });
            Robot robot = new Robot();
            robot.waitForIdle();
            robot.delay(1000);
            int row = tree.getLeadSelectionRow();
            for (int i = 0; i < 2; i++) {
                robot.keyPress(KeyEvent.VK_CONTROL);
                robot.keyPress(KeyEvent.VK_SHIFT);
                robot.keyPress(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_DOWN);
                robot.keyRelease(KeyEvent.VK_SHIFT);
                robot.keyRelease(KeyEvent.VK_CONTROL);
                robot.waitForIdle();
                robot.delay(500);
            }
            if (tree.getLeadSelectionRow() != (row + 2)) {
                System.out.println(tree.getLeadSelectionRow());
                throw new RuntimeException("selectNextChangeLead not working");
            }
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(500);
            if (tree.getLeadSelectionRow() != (row + 1)) {
                System.out.println(tree.getLeadSelectionRow());
                throw new RuntimeException("selectPreviousChangeLead not working");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }
}
