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

/* @test
 * @bug 4466250
 * @summary DefaultListModel.removeRange does not throw IllegalArgumentException
 * @run main bug4466250
*/

import javax.swing.DefaultListModel;
import javax.swing.JLabel;

public class bug4466250 {
    public static void main(String[] args) {
        DefaultListModel model = new DefaultListModel();
        int size = 16;
        for (int i = 0; i < size; i++ ) {
            model.addElement(new JLabel("wow"));
        }

        try {
            model.removeRange(3, 1);
            throw new RuntimeException("IllegalArgumentException has not been thrown");
        } catch (IllegalArgumentException e) {
        }
    }
}
