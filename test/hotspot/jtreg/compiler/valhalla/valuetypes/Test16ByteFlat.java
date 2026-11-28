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
package compiler.valhalla.valuetypes;

import compiler.lib.ir_framework.*;
import java.util.Random;
import jdk.internal.value.ValueClass;
import jdk.internal.vm.annotation.NullRestricted;
import jdk.test.lib.Asserts;
import jdk.test.lib.Utils;

/*
 * @test
 * @key randomness
 * @bug 8398028
 * @summary Test C2 support for loading/storing 16-byte flat fields and array elements.
 * @library /test/lib /
 * @enablePreview
 * @modules java.base/jdk.internal.value
 *          java.base/jdk.internal.vm.annotation
 * @run driver ${test.main.class}
 */
public class Test16ByteFlat {
    private static value class MyValue {
        long x;
        int y;
        short z;
        byte t;

        MyValue() {
            x = 0;
            y = 0;
            z = (short) 0;
            t = (byte) 0;
        }

        MyValue(Random r) {
            x = r.nextLong();
            y = r.nextInt();
            z = (short) r.nextInt();
            t = (byte) r.nextInt();
        }
    }

    private static class NullableHolder {
        MyValue v;
    }

    private static class NullFreeHolder {
        @NullRestricted
        MyValue v;

        NullFreeHolder(MyValue v) {
            this.v = v;
            super();
        }
    }

    private static final MyValue[] NULLABLE_ARRAY = new MyValue[1];
    private static final MyValue[] NULL_FREE_ARRAY = (MyValue[]) ValueClass.newNullRestrictedAtomicArray(MyValue.class, 1, new MyValue());

    public static void main(String[] args) {
        var framework = new TestFramework();
        framework.setDefaultWarmup(1);
        framework.addFlags("--enable-preview", "--add-exports=java.base/jdk.internal.value=ALL-UNNAMED",
                           "--add-exports=java.base/jdk.internal.vm.annotation=ALL-UNNAMED");
        framework.addFlags("-XX:ObjectAlignmentInBytes=16", "-XX:+UnlockExperimentalVMOptions", "-XX:+Use16ByteAtomicFlattening");
        framework.start();
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static MyValue testLoadNullableField(NullableHolder h) {
        return h.v;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static MyValue testLoadNullFreeField(NullFreeHolder h) {
        return h.v;
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static void testStoreNullableField(NullableHolder h, MyValue v) {
        h.v = v;
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static void testStoreNullFreeField(NullFreeHolder h, MyValue v) {
        h.v = v;
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static MyValue testLoadNullableArray() {
        return NULLABLE_ARRAY[0];
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_LOAD_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static MyValue testLoadNullFreeArray() {
        return NULL_FREE_ARRAY[0];
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static void testStoreNullableArray(MyValue v) {
        NULLABLE_ARRAY[0] = v;
    }

    @Test
    @IR(counts = {IRNode.STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"})
    @IR(counts = {IRNode.X86_STORE_VECTOR_ATOMIC, "1"}, applyIf = {"Use16ByteAtomicFlattening", "true"},
        applyIfPlatform = {"x64", "true"})
    private static void testStoreNullFreeArray(MyValue v) {
        NULL_FREE_ARRAY[0] = v;
    }

    @Run(test = {"testLoadNullableField", "testLoadNullFreeField", "testStoreNullableField", "testStoreNullFreeField",
                 "testLoadNullableArray", "testLoadNullFreeArray", "testStoreNullableArray", "testStoreNullFreeArray"})
    public void run() {
        Random random = Utils.getRandomInstance();

        NullableHolder h1 = new NullableHolder();
        MyValue v1 = new MyValue(random);
        Asserts.assertNull(testLoadNullableField(h1));
        h1.v = v1;
        Asserts.assertEQ(v1, testLoadNullableField(h1));
        testStoreNullableField(h1, null);
        Asserts.assertNull(h1.v);
        testStoreNullableField(h1, v1);
        Asserts.assertEQ(v1, h1.v);

        NullFreeHolder h2 = new NullFreeHolder(v1);
        MyValue v2 = new MyValue(random);
        Asserts.assertEQ(v1, testLoadNullFreeField(h2));
        h2.v = v2;
        Asserts.assertEQ(v2, testLoadNullFreeField(h2));
        testStoreNullFreeField(h2, v1);
        Asserts.assertEQ(v1, h2.v);

        NULLABLE_ARRAY[0] = null;
        Asserts.assertNull(testLoadNullableArray());
        NULLABLE_ARRAY[0] = v1;
        Asserts.assertEQ(v1, testLoadNullableArray());
        testStoreNullableArray(null);
        Asserts.assertNull(NULLABLE_ARRAY[0]);
        testStoreNullableArray(v1);
        Asserts.assertEQ(v1, NULLABLE_ARRAY[0]);

        NULL_FREE_ARRAY[0] = v1;
        Asserts.assertEQ(v1, testLoadNullFreeArray());
        NULL_FREE_ARRAY[0] = v2;
        Asserts.assertEQ(v2, testLoadNullFreeArray());
        testStoreNullFreeArray(v1);
        Asserts.assertEQ(v1, NULL_FREE_ARRAY[0]);
    }
}
