/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.test;

import java.lang.reflect.Field;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.compiler.nodes.memory.ReadNode;
import org.junit.Assert;
import org.junit.Test;

import jdk.vm.ci.meta.JavaKind;
import sun.misc.Unsafe;

/**
 * Tests that off-heap memory writes don't prevent optimization of on-heap accesses.
 */
public class OffHeapUnsafeAccessTest extends GraalCompilerTest {

    static final Unsafe UNSAFE = initUnsafe();

    private static Unsafe initUnsafe() {
        try {
            // Fast path when we are trusted.
            return Unsafe.getUnsafe();
        } catch (SecurityException se) {
            // Slow path when we are not trusted.
            try {
                Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                return (Unsafe) theUnsafe.get(Unsafe.class);
            } catch (Exception e) {
                throw new RuntimeException("exception while trying to get Unsafe", e);
            }
        }
    }

    public byte unboxByteAndStore(long memory, byte[] box) {
        byte val = box[0];
        UNSAFE.putByte(memory, val);
        UNSAFE.putByte(null, memory, val);
        return box[0];
    }

    public char unboxCharAndStore(long memory, char[] box) {
        char val = box[0];
        UNSAFE.putChar(memory, val);
        UNSAFE.putChar(null, memory, val);
        return box[0];
    }

    public int unboxIntAndStore(long memory, int[] box) {
        int val = box[0];
        UNSAFE.putInt(memory, val);
        UNSAFE.putInt(null, memory, val);
        return box[0];
    }

    public long unboxLongAndStore(long memory, long[] box) {
        long val = box[0];
        UNSAFE.putLong(memory, val);
        UNSAFE.putLong(null, memory, val);
        UNSAFE.putAddress(memory, val);
        return box[0];
    }

    public float unboxFloatAndStore(long memory, float[] box) {
        float val = box[0];
        UNSAFE.putFloat(memory, val);
        UNSAFE.putFloat(null, memory, val);
        return box[0];
    }

    public double unboxDoubleAndStore(long memory, double[] box) {
        double val = box[0];
        UNSAFE.putDouble(memory, val);
        UNSAFE.putDouble(null, memory, val);
        return box[0];
    }

    private void assertExactlyOneArrayLoad(JavaKind elementKind) {
        int total = 0;
        for (ReadNode read : lastCompiledGraph.getNodes().filter(ReadNode.class)) {
            if (read.getLocationIdentity().equals(NamedLocationIdentity.getArrayLocation(elementKind))) {
                total++;
            }
        }
        Assert.assertEquals(1, total);
    }

    @Test
    public void testGet() {
        long buf = allocBuf();
        if (buf != 0) {
            try {
                test("unboxByteAndStore", buf, new byte[]{40});
                assertExactlyOneArrayLoad(JavaKind.Byte);

                test("unboxCharAndStore", buf, new char[]{41});
                assertExactlyOneArrayLoad(JavaKind.Char);

                test("unboxIntAndStore", buf, new int[]{42});
                assertExactlyOneArrayLoad(JavaKind.Int);

                test("unboxLongAndStore", buf, new long[]{43});
                assertExactlyOneArrayLoad(JavaKind.Long);

                test("unboxFloatAndStore", buf, new float[]{44.0F});
                assertExactlyOneArrayLoad(JavaKind.Float);

                test("unboxDoubleAndStore", buf, new double[]{45.0D});
                assertExactlyOneArrayLoad(JavaKind.Double);
            } finally {
                UNSAFE.freeMemory(buf);
            }
        }
    }

    protected long allocBuf() {
        try {
            return UNSAFE.allocateMemory(16);
        } catch (OutOfMemoryError e) {
            return 0L;
        }
    }
}
