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

/* @test
   @bug 4485322
   @summary DefaultTreeSelectionModel.insureRowContinuity is broken for CONTIGUOUS_TREE_SELECTION
   @run main bug4485322
*/

import javax.swing.SwingUtilities;
import javax.swing.tree.DefaultTreeSelectionModel;
import javax.swing.tree.TreeSelectionModel;
import javax.swing.tree.RowMapper;
import javax.swing.tree.TreePath;

import java.util.Arrays;

public class bug4485322 {

    Object obj1[] = {"9", "2", "5", "3", "1"};
    Object obj2[] = {"1", "2", "3"};

    public void init() {
        DummyDefaultTreeSelectionModel model = new DummyDefaultTreeSelectionModel();

        TreePath sPaths[] = new TreePath[obj1.length];
        for (int i=0; i<obj1.length; i++) {
            sPaths[i] = new TreePath(obj1[i]);
        };
        model.setSelectionPaths(sPaths);

        model.setRowMapper(new DummyRowMapper());
        model.setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);
        model.insureRowContinuity();

        TreePath real[] = model.getSelectionPaths();
        TreePath expected[] = new TreePath[obj2.length];
        for (int i=0; i<obj2.length; i++) {
            expected[i] = new TreePath(obj2[i]);
        };

        if ( !Arrays.equals(real, expected) ) {
            throw new RuntimeException("The tree selection path is wrong.");
        }
    }

    public static class DummyDefaultTreeSelectionModel extends DefaultTreeSelectionModel {
        public void insureRowContinuity() {
            super.insureRowContinuity();
        }
    }

    public static class DummyRowMapper implements RowMapper {
        public int[] getRowsForPaths(TreePath[] path) {
            int rows[] = new int[path.length];
            for (int i = 0;i < path.length; i++) {
                String userObject = path[i].getPathComponent(0).toString();
                rows[i] = Integer.valueOf(userObject);
            }
            return rows;
        }
    }

    public static void main(String[] argv) throws Exception {
        bug4485322 b = new bug4485322();
        SwingUtilities.invokeAndWait(() -> b.init());
    }
}
