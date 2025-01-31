/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8338546
 * @summary Testing ClassFile Util.
 * @library java.base
 * @modules java.base/jdk.internal.constant
 *          java.base/jdk.internal.classfile.impl
 * @build java.base/jdk.internal.classfile.impl.*
 * @run junit UtilTest
 */
import java.lang.classfile.Opcode;
import java.lang.constant.MethodTypeDesc;
import java.util.Arrays;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;


import jdk.internal.classfile.impl.RawBytecodeHelper;
import jdk.internal.classfile.impl.Util;
import jdk.internal.classfile.impl.UtilAccess;
import jdk.internal.constant.ConstantUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UtilTest
 */
class UtilTest {
    @ParameterizedTest
    @ValueSource(classes = {
            Long.class,
            Object.class,
            Util.class,
            Test.class,
    })
    void testDescToBinaryName(Class<?> type) throws ReflectiveOperationException {
        var cd = type.describeConstable().orElseThrow();
        assertEquals(type, Class.forName(Util.toBinaryName(cd)));
        assertEquals(type.getName(), Util.toBinaryName(cd));
    }

    @Test
    void testParameterSlots() {
        assertSlots("(IIII)V", 4);
        assertSlots("([I[I[I[I)V", 4);
        assertSlots("(IJLFoo;IJ)V", 7);
        assertSlots("([[[[I)V", 1);
        assertSlots("([[[[LFoo;)V", 1);
        assertSlots("([I[LFoo;)V", 2);
        assertSlots("()V", 0);
        assertSlots("(I)V", 1);
        assertSlots("(S)V", 1);
        assertSlots("(C)V", 1);
        assertSlots("(B)V", 1);
        assertSlots("(Z)V", 1);
        assertSlots("(F)V", 1);
        assertSlots("(LFoo;)V", 1);
        assertSlots("(J)V", 2);
        assertSlots("(D)V", 2);
        assertSlots("([J)V", 1);
        assertSlots("([D)V", 1);
    }

    private void assertSlots(String methodDesc, int slots) {
        assertEquals(Util.parameterSlots(MethodTypeDesc.ofDescriptor(methodDesc)), slots);
    }

    @Test
    void testPow31() {
        int p = 1;
        // Our calculation only prepares up to 65536,
        // max length of CP Utf8 + 1
        for (int i = 0; i <= 65536; i++) {
            final int t = i;
            assertEquals(p, Util.pow31(i), () -> "31's power to " + t);
            p *= 31;
        }
    }

    // Ensures the initialization statement of the powers array is filling in the right values
    @Test
    void testPowersArray() {
        int[] powers = new int[64];
        for (int i = 1, k = 31; i <= 7; i++, k *= 31) {
            int t = powers[UtilAccess.powersIndex(i, 0)] = k;

            for (int j = 1; j < UtilAccess.significantOctalDigits(); j++) {
                t *= t;
                t *= t;
                t *= t;
                powers[UtilAccess.powersIndex(i, j)] = t;
            }
        }

        assertArrayEquals(powers, UtilAccess.powersTable());
    }

    @Test
    void testOpcodeLengthTable() {
        var lengths = new byte[0x100];
        Arrays.fill(lengths, (byte) -1);
        for (var op : Opcode.values()) {
            if (!op.isWide()) {
                lengths[op.bytecode()] = (byte) op.sizeIfFixed();
            } else {
                // Wide pseudo-opcodes have double the length as normal variants
                // Must match logic in checkSpecialInstruction()
                assertEquals(op.sizeIfFixed(), lengths[op.bytecode() & 0xFF] * 2, op + " size");
            }
        }

        assertArrayEquals(lengths, RawBytecodeHelper.LENGTHS);
    }
}
