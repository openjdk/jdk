/*
 * Copyright (c) 2010, 2016, Oracle and/or its affiliates. All rights reserved.
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

import org.jtregext.GuiTestListener;
import com.sun.swingset3.demos.tree.TreeDemo;
import static com.sun.swingset3.demos.tree.TreeDemo.DEMO_TITLE;
import javax.swing.tree.TreePath;
import static org.testng.AssertJUnit.*;
import org.testng.annotations.Test;
import org.netbeans.jemmy.ClassReference;
import org.netbeans.jemmy.operators.JFrameOperator;
import org.netbeans.jemmy.operators.JTreeOperator;
import org.testng.annotations.Listeners;

/*
 * @test
 * @key headful
 * @summary Verifies SwingSet3 TreeDemo by expanding all collapsed nodes in the
 *          tree and then collapsing all the expanded nodes in the tree. It
 *          verifies the number of nodes expanded, number of nodes collapsed and
 *          number of rows in the tree in the begininng, after expanding and
 *          after collapsing the nodes. It also checks that the tree grows
 *          vertically (as ScrollPane allows it).
 *
 * @library /sanity/client/lib/jemmy/src
 * @library /sanity/client/lib/Extensions/src
 * @library /sanity/client/lib/SwingSet3/src
 * @build org.jemmy2ext.JemmyExt
 * @build com.sun.swingset3.demos.tree.TreeDemo
 * @run testng TreeDemoTest
 */
@Listeners(GuiTestListener.class)
public class TreeDemoTest {

    @Test
    public void test() throws Exception {

        new ClassReference(TreeDemo.class.getCanonicalName()).startApplication();

        JFrameOperator frame = new JFrameOperator(DEMO_TITLE);

        JTreeOperator tree = new JTreeOperator(frame);

        assertEquals("Initial number of rows in the tree", 4, tree.getRowCount());

        int initialTreeHeight = tree.getHeight();

        // expand all nodes
        int expandsCount = 0;
        for (int i = 0; i < tree.getRowCount(); i++) {
            TreePath tp = tree.getPathForRow(i);
            if (tree.getChildCount(tp) > 0 && !tree.isExpanded(tp)) {
                tree.expandRow(i);
                expandsCount++;
            }
        }

        assertEquals("Number of rows expanded", 75, expandsCount);
        assertEquals("Number of rows in the tree after expanding all of them",
                616, tree.getRowCount());

        int expandedTreeHeight = tree.getHeight();
        assertTrue("Expanded tree height has increased, current "
                + expandedTreeHeight + " > initial " + initialTreeHeight,
                expandedTreeHeight > initialTreeHeight);

        // collapse all nodes
        int collapsesCount = 0;
        for (int i = tree.getRowCount() - 1; i >= 0; i--) {
            TreePath tp = tree.getPathForRow(i);
            if (tree.getChildCount(tp) > 0 && tree.isExpanded(tp)) {
                tree.collapseRow(i);
                collapsesCount++;
            }
        }

        assertEquals("Number of rows collapsed", 76, collapsesCount);
        assertEquals("Number of rows in the tree after collapsing all of them",
                1, tree.getRowCount());

        int collapsedTreeHeight = tree.getHeight();
        assertTrue("Collpased tree height is not longer than initial, "
                + "current " + collapsedTreeHeight + " <= initial "
                + initialTreeHeight,
                collapsedTreeHeight <= initialTreeHeight);

    }

}
