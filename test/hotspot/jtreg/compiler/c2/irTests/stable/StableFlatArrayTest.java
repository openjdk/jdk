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
 * @bug 8372700
 * @summary Check stable flat array field folding
 * @library /test/lib /
 * @requires (os.simpleArch == "x64" | os.simpleArch == "aarch64")
 * @build jdk.test.whitebox.WhiteBox
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI compiler.c2.irTests.stable.StableFlatArrayTest
 */

package compiler.c2.irTests.stable;

import compiler.lib.ir_framework.*;
import compiler.valhalla.inlinetypes.*;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.Stable;
import jdk.test.lib.Asserts;

public class StableFlatArrayTest {
    public static void main(String[] args) {
        TestFramework tf = new TestFramework();
        tf.addTestClassesToBootClassPath();
                tf.addFlags(
                        "--enable-preview",
                        "--add-exports", "java.base/jdk.internal.value=ALL-UNNAMED"
                )
                .start();
    }

    static final Integer[] NULL_RESTRICTED_NON_ATOMIC_ARRAY = (Integer[]) ValueClass.newNullRestrictedNonAtomicArray(Integer.class, 1, 42);
    static final Integer[] NULL_RESTRICTED_ATOMIC_ARRAY = (Integer[]) ValueClass.newNullRestrictedAtomicArray(Integer.class, 1, 43);
    static final Integer[] NULLABLE_ATOMIC_ARRAY = (Integer[]) ValueClass.newNullableAtomicArray(Integer.class, 1);
    static {
        NULLABLE_ATOMIC_ARRAY[0] = 44;
    }

    static final int NULL_RESTRICTED_NON_ATOMIC_CASE = 1;
    static final int NULL_RESTRICTED_ATOMIC_CASE = 2;
    static final int NULLABLE_ATOMIC_CASE = 3;

    static class Carrier {
        @Stable
        Integer[] field;

        @ForceInline
        public Carrier(int initLevel) {
            switch (initLevel) {
                case NULL_RESTRICTED_NON_ATOMIC_CASE:
                    field = NULL_RESTRICTED_NON_ATOMIC_ARRAY;
                    break;
                case NULL_RESTRICTED_ATOMIC_CASE:
                    field = NULL_RESTRICTED_ATOMIC_ARRAY;
                    break;
                case NULLABLE_ATOMIC_CASE:
                    field = NULLABLE_ATOMIC_ARRAY;
                    break;
                default:
                    throw new IllegalStateException("Unknown level");
            }
        }
    }

    static final Carrier NULL_RESTRICTED_NON_ATOMIC_CARRIER = new Carrier(NULL_RESTRICTED_NON_ATOMIC_CASE);
    static final Carrier NULL_RESTRICTED_ATOMIC_CARRIER = new Carrier(NULL_RESTRICTED_ATOMIC_CASE);
    static final Carrier NULLABLE_ATOMIC_CARRIER = new Carrier(NULLABLE_ATOMIC_CASE);

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.MEMBAR})
    static int testNullRestrictedNonAtomic() {
        Integer[] is = NULL_RESTRICTED_NON_ATOMIC_CARRIER.field;
        if (is != null) {
            Integer i = is[0];
            if (i != null) {
                return i;
            }
        }
        return 0;
    }

    @Run(test = "testNullRestrictedNonAtomic")
    public void testNullRestrictedNonAtomic_verifier() {
        int result = testNullRestrictedNonAtomic();
        Asserts.assertEquals(result, 42);
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.MEMBAR})
    static int testNullRestrictedAtomic() {
        Integer[] is = NULL_RESTRICTED_ATOMIC_CARRIER.field;
        if (is != null) {
            Integer i = is[0];
            if (i != null) {
                return i;
            }
        }
        return 0;
    }

    @Run(test = "testNullRestrictedAtomic")
    public void testNullRestrictedAtomic_verifier() {
        int result = testNullRestrictedAtomic();
        Asserts.assertEquals(result, 43);
    }

    @Test
    @IR(failOn = {IRNode.LOAD, IRNode.MEMBAR})
    static int testNullableAtomic() {
        Integer[] is = NULLABLE_ATOMIC_CARRIER.field;
        if (is != null) {
            Integer i = is[0];
            if (i != null) {
                return i;
            }
        }
        return 0;
    }

    @Run(test = "testNullableAtomic")
    public void testNullableAtomic_verifier() {
        int result = testNullableAtomic();
        Asserts.assertEquals(result, 44);
    }
}
