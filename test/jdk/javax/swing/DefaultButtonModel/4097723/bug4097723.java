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

/*
 * @test
 * @bug 4097723
 * @summary Tests that method DefaultButtonModel.getGroup() exists
 * @run main bug4097723
 */

import javax.swing.ButtonGroup;
import javax.swing.DefaultButtonModel;

public class bug4097723 {
    public static void main(String[] argv) {
        DefaultButtonModel dbm = new DefaultButtonModel();
        ButtonGroup group = new ButtonGroup();
        dbm.setGroup(group);
        ButtonGroup g = dbm.getGroup();
        if (g != group) {
            throw new RuntimeException("Failure: getGroup() returned wrong thing");
        }
    }
}
