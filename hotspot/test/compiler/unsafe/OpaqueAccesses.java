/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @bug 8155781
 * @modules java.base/jdk.internal.misc
 *
 * @run main/bootclasspath/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions
 *                                 -XX:-TieredCompilation -Xbatch
 *                                 -XX:CompileCommand=dontinline,compiler.unsafe.OpaqueAccesses::test*
 *                                 compiler.unsafe.OpaqueAccesses
 */
package compiler.unsafe;

import jdk.internal.misc.Unsafe;

import java.lang.reflect.Field;

public class OpaqueAccesses {
    private static final Unsafe UNSAFE = Unsafe.getUnsafe();

    private static final Object INSTANCE = new OpaqueAccesses();

    private static final Object[] ARRAY = new Object[10];

    private static final long F_OFFSET;
    private static final long E_OFFSET;

    static {
        try {
            Field field = OpaqueAccesses.class.getDeclaredField("f");
            F_OFFSET = UNSAFE.objectFieldOffset(field);

            E_OFFSET = UNSAFE.arrayBaseOffset(ARRAY.getClass());
        } catch (NoSuchFieldException e) {
            throw new Error(e);
        }
    }

    private Object f = new Object();

    static Object testFixedOffsetField(Object o) {
        return UNSAFE.getObject(o, F_OFFSET);
    }

    static int testFixedOffsetHeader0(Object o) {
        return UNSAFE.getInt(o, 0);
    }

    static int testFixedOffsetHeader4(Object o) {
        return UNSAFE.getInt(o, 4);
    }

    static Object testFixedBase(long off) {
        return UNSAFE.getObject(INSTANCE, off);
    }

    static Object testOpaque(Object o, long off) {
        return UNSAFE.getObject(o, off);
    }

    static int testFixedOffsetHeaderArray0(Object[] arr) {
        return UNSAFE.getInt(arr, 0);
    }

    static int testFixedOffsetHeaderArray4(Object[] arr) {
        return UNSAFE.getInt(arr, 4);
    }

    static Object testFixedOffsetArray(Object[] arr) {
        return UNSAFE.getObject(arr, E_OFFSET);
    }

    static Object testFixedBaseArray(long off) {
        return UNSAFE.getObject(ARRAY, off);
    }

    static Object testOpaqueArray(Object[] o, long off) {
        return UNSAFE.getObject(o, off);
    }

    static final long ADDR = UNSAFE.allocateMemory(10);
    static boolean flag;

    static int testMixedAccess() {
        flag = !flag;
        Object o = (flag ? INSTANCE : null);
        long off = (flag ? F_OFFSET : ADDR);
        return UNSAFE.getInt(o, off);
    }

    public static void main(String[] args) {
        for (int i = 0; i < 20_000; i++) {
            // Instance
            testFixedOffsetField(INSTANCE);
            testFixedOffsetHeader0(INSTANCE);
            testFixedOffsetHeader4(INSTANCE);
            testFixedBase(F_OFFSET);
            testOpaque(INSTANCE, F_OFFSET);
            testMixedAccess();

            // Array
            testFixedOffsetHeaderArray0(ARRAY);
            testFixedOffsetHeaderArray4(ARRAY);
            testFixedOffsetArray(ARRAY);
            testFixedBaseArray(E_OFFSET);
            testOpaqueArray(ARRAY, E_OFFSET);
        }
        System.out.println("TEST PASSED");
    }
}
