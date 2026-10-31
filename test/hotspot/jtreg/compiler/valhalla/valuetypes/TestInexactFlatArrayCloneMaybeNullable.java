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
 * @summary Test clone of flat array that does not have an exact klass,
 *          i.e. multiple layouts are possible.
 * @bug 8390508
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run main/othervm -Xbatch ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 * @run main/othervm -Xbatch -XX:-TieredCompilation -XX:-DoEscapeAnalysis
 *                   -XX:CompileCommand=compileonly,${test.main.class}::test
 *                   ${test.main.class}
 */

package compiler.valhalla.valuetypes;

import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.LooselyConsistentValue;
import jdk.test.lib.Asserts;

public class TestInexactFlatArrayCloneMaybeNullable {
    @LooselyConsistentValue
    static value class MyValue {
        byte x;
        byte y;

        MyValue(byte value) {
            x = value;
            y = value;
        }
    }

    static final MyValue VALUE = new MyValue((byte)42);
    static final MyValue[] NULL_FREE = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, VALUE);
    static final MyValue[] NULLABLE = (MyValue[])ValueClass.newNullableAtomicArray(MyValue.class, 1);

    static MyValue[] test(boolean nullFree) {
        MyValue[] source = nullFree ? NULL_FREE : NULLABLE;
        // Clone a possibly flat array that is either null-free or nullable.
        // Hence, the layout of the array element is not exact.
        return source.clone();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            boolean nullFree = (i & 1) == 0;
            MyValue[] source = nullFree ? NULL_FREE : NULLABLE;
            boolean flat = ValueClass.isFlatArray(source);
            MyValue[] result = test(nullFree);

            // Some additional checks just to ensure the cloned array has
            // the expected properties.
            Asserts.assertEQ(result[0], source[0]);
            Asserts.assertTrue(ValueClass.isAtomicArray(result));
            Asserts.assertEQ(ValueClass.isNullRestrictedArray(result), nullFree);
            Asserts.assertEQ(ValueClass.isFlatArray(result), flat);
        }
    }
}
