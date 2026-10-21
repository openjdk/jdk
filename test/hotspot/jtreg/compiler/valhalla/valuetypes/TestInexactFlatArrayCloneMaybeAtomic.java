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
 * @requires vm.compiler2.enabled
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

public class TestInexactFlatArrayCloneMaybeAtomic {
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
    static final MyValue[] ATOMIC = (MyValue[])ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, VALUE);
    static final MyValue[] NON_ATOMIC = (MyValue[])ValueClass.newNullRestrictedNonAtomicArray(MyValue.class, 1, VALUE);

    static MyValue[] test(boolean atomic) {
        MyValue[] source = atomic ? ATOMIC : NON_ATOMIC;
        // Clone a possibly flat array that is either atomic or non-atomic.
        // Hence, the layout of the array element is not exact.
        return source.clone();
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            boolean atomic = (i & 1) == 0;
            boolean flat = ValueClass.isFlatArray(atomic ? ATOMIC : NON_ATOMIC);
            MyValue[] result = test(atomic);

            // Some additional checks just to ensure the cloned array has
            // the expected properties.
            if (result[0] != VALUE) {
                throw new AssertionError("Unexpected clone element value");
            }
            if (ValueClass.isAtomicArray(result) != atomic) {
                throw new AssertionError("Unexpected clone atomic property");
            }
            if (!ValueClass.isNullRestrictedArray(result)) {
                throw new AssertionError("Unexpected clone nullable property");
            }
            if (ValueClass.isFlatArray(result) != flat) {
                throw new AssertionError("Unexpected clone flat property");
            }
        }
    }
}
