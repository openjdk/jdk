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
   @bug 4247487
   @summary Tests that the following methods of JTable are public:
            int getAccessibleColumnAtIndex(int)
            int getAccessibleRowAtIndex(int)
            int getAccessibleIndexAt(int, int)
*/

import javax.swing.JTable;

public class bug4247487 {

    static class TestTable extends JTable {

        public TestTable() {
            super(new Object[][]{{"one", "two"}},
                  new Object[]{"A", "B"});
        }

        public void test() {
            int[] rowIndices = {0, 0, 1, 1};
            int[] colIndices = {0, 1, 0, 1};
            JTable.AccessibleJTable at =
                (JTable.AccessibleJTable)getAccessibleContext();

            for (int i=0; i<4; i++) {
                if (at.getAccessibleRowAtIndex(i) != rowIndices[i]) {
                    throw new Error("Failed: wrong row index");
                }
                if (at.getAccessibleColumnAtIndex(i) != colIndices[i]) {
                    throw new Error("Failed: wrong column index");
                }
            }
            if (at.getAccessibleIndexAt(0,0) != 0 ||
                at.getAccessibleIndexAt(0,1) != 1 ||
                at.getAccessibleIndexAt(1,0) != 2 ||
                at.getAccessibleIndexAt(1,1) != 3) {

                throw new Error("Failed: wrong index");
            }
        }
    }

    public static void main(String[] argv) {
        TestTable test = new TestTable();
        test.test();
    }
}
