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
   @test
   @bug 4234119
   @summary Tests if adding items to ComboBox is slow
*/

import javax.swing.JComboBox;

public class bug4234119 {

    public static void main(String args[]) {
        JComboBox jComboBox1 = new JComboBox();
        long startTime = System.currentTimeMillis();
        for (int i = 0 ; i < 500; i++) {
            jComboBox1.addItem(Integer.valueOf(i));
        }
        long deltaTime = System.currentTimeMillis() - startTime;
        if (deltaTime > 20000) {
            throw new Error("Test failed: adding items to ComboBox is SLOW! (it took " + deltaTime + " ms");
        }
        System.out.println("Elapsed time: " + deltaTime);
    }
}
