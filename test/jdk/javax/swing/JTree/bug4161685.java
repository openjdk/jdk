/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4161685
 * @summary JTree now has the public methods setAnchorSelectionPath,
 * getAnchorSelectionPath, setLeadSelectionPath.
 * @run main bug4161685
 */

import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;

public class bug4161685 {
    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JTree tr = new JTree();
            TreePath tp = tr.getPathForRow(2);
            tr.setAnchorSelectionPath(tp);
            if (tr.getAnchorSelectionPath() != tp) {
                throw new RuntimeException("AnchorSelectionPath isn't " +
                        "set correctly...");
            }
            tr.setLeadSelectionPath(tp);
            if (tr.getLeadSelectionPath() != tp) {
                throw new RuntimeException("LeadSelectionPath isn't " +
                        "set correctly...");
            }
        });
    }
}
