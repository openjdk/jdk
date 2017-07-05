/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003400
 * @summary Tests that JTree shows the last row
 * @author Sergey Malenkov
 * @run main/othervm Test8003400
 * @run main/othervm Test8003400 reverse
 * @run main/othervm Test8003400 system
 * @run main/othervm Test8003400 system reverse
 */

import sun.awt.SunToolkit;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;

public class Test8003400 {

    private static final String TITLE = "Test JTree with a large model";
    private static List<String> OBJECTS = Arrays.asList(TITLE, "x", "y", "z");
    private static JScrollPane pane;

    public static void main(String[] args) throws Exception {
        for (String arg : args) {
            if (arg.equals("reverse")) {
                Collections.reverse(OBJECTS);
            }
            if (arg.equals("system")) {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
        }
        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                DefaultMutableTreeNode root = new DefaultMutableTreeNode();

                JTree tree = new JTree(root);
                tree.setLargeModel(true);
                tree.setRowHeight(16);

                JFrame frame = new JFrame(TITLE);
                frame.add(pane = new JScrollPane(tree));
                frame.setSize(200, 100);
                frame.setLocationRelativeTo(null);
                frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                frame.setVisible(true);

                for (String object : OBJECTS) {
                    root.add(new DefaultMutableTreeNode(object));
                }
                tree.expandRow(0);
            }
        });

        SunToolkit toolkit = (SunToolkit) Toolkit.getDefaultToolkit();
        toolkit.realSync(500);
        new Robot().keyPress(KeyEvent.VK_END);
        toolkit.realSync(500);

        SwingUtilities.invokeAndWait(new Runnable() {
            public void run() {
                JTree tree = (JTree) pane.getViewport().getView();
                Rectangle inner = tree.getRowBounds(tree.getRowCount() - 1);
                Rectangle outer = SwingUtilities.convertRectangle(tree, inner, pane);
                outer.y += tree.getRowHeight() - 1 - pane.getVerticalScrollBar().getHeight();
                // error reporting only for automatic testing
                if (null != System.getProperty("test.src", null)) {
                    SwingUtilities.getWindowAncestor(pane).dispose();
                    if (outer.y != 0) {
                        throw new Error("TEST FAILED: " + outer.y);
                    }
                }
            }
        });
    }
}
