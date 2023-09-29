/*
 * Copyright (c) 2004, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5039542
 * @summary JTree's setToolTipText() doesn't work
 * @run main bug5039542
 */

import javax.swing.JTree;

public class bug5039542 {
    public static void main(String[] args) throws Exception {
        final String exampleStr = "TEST";
        JTree tree = new JTree();
        tree.setToolTipText(exampleStr);
        if (tree.getToolTipText(null) != exampleStr) {
            throw new RuntimeException("The default JTree tooltip text " +
                    "have to be used if renderer doesn't provide it.");
        }
    }
}
