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
 * @bug 8360462
 * @summary Verifies ctrl+shift+down selects next row
 *          and ctrl+shift+up selects previous row in Aqua L&F
 * @run main TestTreeRowSelection
 */

import java.awt.Robot;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultMutableTreeNode;

public class TestTreeRowSelection {
    static JTree tree;
    static JFrame frame;
    static volatile int selectedRowCount;
    static volatile int curSelectedRowCount;

    public static void main(String[]  args) throws Exception {

        try {
            SwingUtilities.invokeAndWait(() -> {
                frame = new JFrame();
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
            SwingUtilities.invokeAndWait(() -> {
                selectedRowCount = tree.getSelectionCount();
            });
            System.out.println("rows selected " + selectedRowCount);
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
            SwingUtilities.invokeAndWait(() -> {
                curSelectedRowCount = tree.getSelectionCount();
            });
            System.out.println("rows selected " + curSelectedRowCount);
            if (curSelectedRowCount != selectedRowCount + 2) {
                throw new RuntimeException("ctrl+shift+down doesn't select next row");
            }
            robot.keyPress(KeyEvent.VK_CONTROL);
            robot.keyPress(KeyEvent.VK_SHIFT);
            robot.keyPress(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_UP);
            robot.keyRelease(KeyEvent.VK_SHIFT);
            robot.keyRelease(KeyEvent.VK_CONTROL);
            robot.waitForIdle();
            robot.delay(500);
            SwingUtilities.invokeAndWait(() -> {
                curSelectedRowCount = tree.getSelectionCount();
            });
            System.out.println("rows selected " + curSelectedRowCount);
            if (curSelectedRowCount != selectedRowCount + 1) {
                throw new RuntimeException("ctrl+shift+up doesn't select previous row");
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
