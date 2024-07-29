/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8321974
 * @summary Test that the TypeAryPtr::_klass field is properly initialized on use.
 * @comment This test only reproduces the issue with release builds of the JVM because
 *          verification code in debug builds leads to eager initialization of the field.
 * @run main/othervm -Xbatch -XX:CompileCommand=compileonly,TestUninitializedKlassField::test*
 *                   -XX:-TieredCompilation TestUninitializedKlassField
 */

public class TestUninitializedKlassField {
    static void test(long array2[]) {
        long array1[] = new long[1];
        // Loop is needed to create a backedge phi that is processed only during IGVN.
        for (int i = 0; i < 1; i++) {
            // CmpPNode::sub will check if classes of array1 and array2 are unrelated
            // and the subtype checks will crash if the _klass field is not initialized.
            if (array2 != array1) {
                array1 = new long[2];
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 50_000; ++i) {
            test(null);
        }
    }
}
