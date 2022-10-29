/*
 * Copyright (c) 2008, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4670319
 * @summary AccessibleJTree should fire a PropertyChangeEvent
 * using a AccessibleJTreeNode as source.
 * @run main AccessibleJTreePCESourceTest
 */

import java.awt.Robot;
import java.beans.PropertyChangeEvent;
import java.util.ArrayList;

import javax.swing.JFrame;
import javax.swing.JTree;
import javax.swing.SwingUtilities;

public class AccessibleJTreePCESourceTest {
    private static JTree jTree;
    private static JFrame jFrame;

    private static ArrayList<PropertyChangeEvent> eventsList =
        new ArrayList<PropertyChangeEvent>();

    private static void doTest() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> createGUI());
            Robot robot = new Robot();
            robot.waitForIdle();

            expand(1);
            robot.waitForIdle();
            collapse(1);
            robot.waitForIdle();
            expand(2);
            robot.waitForIdle();
            collapse(2);
            robot.waitForIdle();
        } finally {
            SwingUtilities.invokeAndWait(() -> jFrame.dispose());
        }
    }

    public static void expand(int row) throws Exception {
        SwingUtilities.invokeAndWait(() -> jTree.expandRow(row));
    }

    public static void collapse(int row) throws Exception {
        SwingUtilities.invokeAndWait(() -> jTree.collapseRow(row));
    }

    private static void createGUI() {
        jTree = new JTree();
        jFrame = new JFrame();

        jFrame.add(jTree);

        jTree.getAccessibleContext().addPropertyChangeListener(event -> {
            if (event.getNewValue() != null) {
                eventsList.add(event);
            }
        });

        jFrame.setSize(200, 200);
        jFrame.getContentPane().add(jTree);
        jFrame.setVisible(true);
    }

    public static void main(String args[]) throws Exception {
        doTest();

        for (int i = 0; i < eventsList.size(); i++) {
            PropertyChangeEvent obj = eventsList.get(i);
            String state = obj.getNewValue().toString();

            if ((state.equals("expanded") || state.equals("collapsed"))
                && (obj.getPropertyName().toString())
                .equals("AccessibleState")) {
                if (!(obj.getSource().getClass().getName()).equals(
                    "javax.swing.JTree$AccessibleJTree$AccessibleJTreeNode")) {
                    throw new RuntimeException("Test Failed: When tree node is "
                        + state + ", PropertyChangeEventSource is "
                        + obj.getSource().getClass().getName());
                }
            }
        }
        System.out.println(
            "Test Passed: When tree node is expanded/collapsed, "
            + "PropertyChangeEventSource is the Node");
    }
}

