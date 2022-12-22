/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4702690
 * @key headful
 * @summary Make an automatic AccessibleRelation between
 * JScrollBars and what they scroll (TP)
 * @run main JScrollPaneAccessibleRelationsTest
 */
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.accessibility.AccessibleRelation;
import javax.swing.JFrame;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public class JScrollPaneAccessibleRelationsTest
implements PropertyChangeListener {

    private static JFrame jFrame;
    private static JScrollPane jScrollPane;
    private static JScrollBar horizontalScrollBar;
    private static JScrollBar verticalScrollBar;

    private static Object[] jScrollPaneTarget;
    private static Object[] horizontalScrollBarTarget;
    private static Object[] verticalScrollBarTarget;

    public static void createGUI() {
        jFrame = new JFrame();

        jScrollPane = new JScrollPane();
        horizontalScrollBar = jScrollPane.createHorizontalScrollBar();
        verticalScrollBar = jScrollPane.createVerticalScrollBar();
        jScrollPane.setHorizontalScrollBar(horizontalScrollBar);
        jScrollPane.setVerticalScrollBar(verticalScrollBar);

        jFrame.getContentPane().add(jScrollPane);
    }

    public static void doTest() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> createGUI());

            SwingUtilities.invokeAndWait(() -> jScrollPaneTarget =
                jScrollPane.getAccessibleContext().getAccessibleRelationSet()
                .get(AccessibleRelation.CONTROLLED_BY).getTarget());
            SwingUtilities.invokeAndWait(
                () -> horizontalScrollBarTarget = horizontalScrollBar
                .getAccessibleContext().getAccessibleRelationSet()
                .get(AccessibleRelation.CONTROLLER_FOR).getTarget());
            SwingUtilities
            .invokeAndWait(() -> verticalScrollBarTarget = verticalScrollBar
            .getAccessibleContext().getAccessibleRelationSet()
            .get(AccessibleRelation.CONTROLLER_FOR).getTarget());

            if (!(jScrollPaneTarget[0] instanceof javax.swing.JScrollBar)) {
                throw new RuntimeException("JScrollPane doesn't have "
                    + "JScrollBar as target for CONTROLLED_BY");
            }
            if (!(jScrollPaneTarget[1] instanceof javax.swing.JScrollBar)) {
                throw new RuntimeException("JScrollPane doesn't have "
                    + "JScrollBar as target for CONTROLLED_BY");
            }
            if (!(horizontalScrollBarTarget[0] instanceof JScrollPane)) {
                throw new RuntimeException("HorizontalScrollBar doesn't have "
                    + "JScrollPane as target for CONTROLLER_FOR");
            }
            if (!(verticalScrollBarTarget[0] instanceof JScrollPane)) {
                throw new RuntimeException("VerticalScrollBar doesn't have "
                    + "JScrollPane as target for CONTROLLER_FOR");
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> jFrame.dispose());
        }
    }

    public void propertyChange(PropertyChangeEvent e) {
        if (!("AccessibleActiveDescendant".equals(e.getPropertyName()))) {
            throw new RuntimeException(
                "Active Descendant of JScrollBar has not changed");
        }
        if (!("AccessibleSelection".equals(e.getPropertyName()))) {
            throw new RuntimeException(
                "Accessible Selection of JScrollBar has not changed");
        }
    }

    public static void main(String[] args) throws Exception {
        doTest();
        System.out.println("Test Passed.");
    }
}

