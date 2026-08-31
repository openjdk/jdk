/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8388476
 * @summary [Valhalla] C2 incorrectly eliminates store to flat array
 *
 * @enablePreview
 * @modules java.base/jdk.internal.value
 * @run main/othervm -XX:-TieredCompilation -Xbatch -XX:+UnlockDiagnosticVMOptions
 *                   -XX:+IgnoreUnrecognizedVMOptions -XX:-UseArrayLoadStoreProfile
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   -XX:+AlwaysIncrementalInline ${test.main.class}
 * @run main ${test.main.class}
 */


package compiler.valhalla.inlinetypes;
import jdk.internal.value.ValueClass;

public class TestIncorrectlyEliminatedStoreToFlatArray {
    static value class IntegerBox {
        int value;
        IntegerBox(int value) { this.value = value; }
        public String toString() { return "value: " + value; }
    }

    static Object test(Object obj, IntegerBox box) {
        Object[] array = (Object[])obj;
        try {
            array[0] = null;
            throw new NullPointerException("No NPE thrown");
        } catch (NullPointerException expected) {
            array[0] = box;
        }
        return array[0];
    }

    public static void main(String[] args) {
        IntegerBox[] array = (IntegerBox[])ValueClass.newNullRestrictedAtomicArray(IntegerBox.class, 1, new IntegerBox(1));

        for (int i = 0; i < 50_000; i++) {
            IntegerBox box = new IntegerBox(i);
            Object res = test(array, box);
            if (res != box) {
                throw new AssertionError(res + " vs. " + box);
            }
        }
    }
}
