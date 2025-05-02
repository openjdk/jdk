/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4480602
 * @summary Verifies if DefaultTreeCellEditor.inHitRegion() incorrectly
 *          handles row bounds
 * @key headful
 * @run main bug4480602
*/

import java.awt.ComponentOrientation;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellEditor;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.SwingUtilities;

import java.util.Date;

public class bug4480602 {

    static JTree tree;
    static JFrame fr;
    static MyTreeCellEditor editor;

    static Robot robot;
    boolean passed = false;
    boolean do_test = false;

    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        robot.setAutoDelay(100);
        try {
            SwingUtilities.invokeAndWait(() -> {
                fr = new JFrame("Test");

                String s = "0\u05D01\u05D02\u05D03\u05D04\u05D05";
                DefaultMutableTreeNode root = new DefaultMutableTreeNode(s);
                root.add(new DefaultMutableTreeNode(s));
                root.add(new DefaultMutableTreeNode(s));

                tree = new JTree(root);
                editor = new MyTreeCellEditor(tree, new DefaultTreeCellRenderer());
                tree.setCellEditor(editor);
                tree.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
                tree.setEditable(true);
                JScrollPane sp = new JScrollPane(tree);
                fr.getContentPane().add(sp);

                fr.setSize(250,200);
                fr.setLocationRelativeTo(null);
                fr.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            SwingUtilities.invokeAndWait(() -> {
                Rectangle rect = tree.getRowBounds(1);
                editor.testTreeCellEditor(rect);
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (fr != null) {
                    fr.dispose();
                }
            });
        }
    }

    static class MyTreeCellEditor extends DefaultTreeCellEditor {

        public MyTreeCellEditor(JTree tree, DefaultTreeCellRenderer renderer) {
            super(tree, renderer);
        }

        public void testTreeCellEditor(Rectangle rect) {
            int x = rect.x + 10;
            int y = rect.y + rect.height / 2;
            MouseEvent me = new MouseEvent(tree,
                                           MouseEvent.MOUSE_PRESSED,
                                           (new Date()).getTime(),
                                           MouseEvent.BUTTON1_DOWN_MASK,
                                           rect.x + 10, rect.y + 10,
                                           1, true);
            isCellEditable(me);

            if (tree == null) {
                throw new RuntimeException("isCellEditable() should set the tree");
            }
            if (lastRow != 1) {
                throw new RuntimeException("isCellEditable() should set the lastRow");
            }
            if (offset == 0) {
                throw new RuntimeException("isCellEditable() should determine offset");
            }

            if (!inHitRegion(x,y)) {
                throw new RuntimeException("Hit region should contain point ("+x+", "+y+")");
            }
            x = rect.x + rect.width - 10;
            if (inHitRegion(x,y)) {
                throw new RuntimeException("Hit region shouldn't contain point ("+x+", "+y+")");
            }
        }
    }

}
