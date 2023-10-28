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
   @bug 4143690
   @summary Tests that TreeSelectionEvent has isAddedPath(int) method
   @run main bug4143690
*/

import javax.swing.event.TreeSelectionEvent;
import javax.swing.tree.TreePath;

public class bug4143690 {

    public static void main(String[] argv) throws Exception {
        bug4143690 test = new bug4143690();
        TreePath p = new TreePath("");
        TreeSelectionEvent e = new TreeSelectionEvent(test, p, true, p, p);

        TreePath[] paths = e.getPaths();
        for(int i = 0; i < paths.length; i++) {
            TreePath path = paths[i];
            if (e.isAddedPath(i) != true) {
                throw new RuntimeException("Incorrect isAddedPath(int)...");
            }
        }
    }
}
