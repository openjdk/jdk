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

package compiler.arraycopy;

/**
 * @test
 * @bug 8315082
 * @summary Test that idealization of clone-derived ArrayCopy nodes does not
 *          trigger assertion failures for different combinations of
 *          constant/variable array length (in number of elements) and array
 *          copy length (in words).
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileOnly=compiler.arraycopy.TestCloneArrayWithDifferentLengthConstness::test*
 *                   compiler.arraycopy.TestCloneArrayWithDifferentLengthConstness
 */

public class TestCloneArrayWithDifferentLengthConstness {

    public static void main(String[] args) {
        for (int i = 0; i < 10_000; i++) {
            testConstantArrayLengthAndConstantArrayCopyLength();
        }
        for (int i = 0; i < 10_000; i++) {
            testVariableArrayLengthAndConstantArrayCopyLength(i % 2 == 0);
        }
        for (int i = 0; i < 10_000; i++) {
            testVariableArrayLengthAndVariableArrayCopyLength(i % 2 == 0);
        }
    }

    static int[] testConstantArrayLengthAndConstantArrayCopyLength() {
        int[] src = new int[3];
        return (int[])src.clone();
    }

    static int[] testVariableArrayLengthAndConstantArrayCopyLength(boolean p) {
        int[] src = new int[p ? 3 : 4];
        return (int[])src.clone();
    }

    static int[] testVariableArrayLengthAndVariableArrayCopyLength(boolean p) {
        int[] src = new int[p ? 3 : 42];
        return (int[])src.clone();
    }
}
