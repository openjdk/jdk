/*
 * Copyright (c) 2016, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.core.test.ea.EATestBase.TestClassInt;
import org.graalvm.compiler.nodes.StructuredGraph;
import org.graalvm.compiler.nodes.StructuredGraph.AllowAssumptions;
import org.graalvm.compiler.nodes.extended.RawLoadNode;
import org.graalvm.compiler.nodes.extended.RawStoreNode;
import org.graalvm.compiler.nodes.spi.CoreProviders;
import org.graalvm.compiler.nodes.virtual.VirtualArrayNode;
import org.graalvm.compiler.nodes.virtual.VirtualObjectNode;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.phases.common.CanonicalizerPhase;
import org.graalvm.compiler.virtual.phases.ea.PartialEscapePhase;
import org.junit.Assume;
import org.junit.Test;

import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import sun.misc.Unsafe;

public class UnsafeVirtualizationTest extends GraalCompilerTest {

    private static boolean[] FT = new boolean[]{false, true};

    // Side effect to create a deopt point, after possible virtualization.
    static int sideEffectField;

    private static void sideEffect() {
        sideEffectField = 5;
    }

    public static int unsafeSnippet1(double i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getInt(a, TestClassInt.fieldOffset1) + UNSAFE.getInt(a, TestClassInt.fieldOffset2);
    }

    public static long unsafeSnippet2a(int i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        a.setFirstField(i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet2b(int i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        a.setSecondField(i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet3a(int i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putInt(a, TestClassInt.fieldOffset1, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static long unsafeSnippet3b(int i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putInt(a, TestClassInt.fieldOffset2, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(a, TestClassInt.fieldOffset1);
    }

    public static int unsafeSnippet4(double i1, boolean c) {
        TestClassInt a = new TestClassInt();
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        UNSAFE.putDouble(a, TestClassInt.fieldOffset1, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getInt(a, TestClassInt.fieldOffset1) + UNSAFE.getInt(a, TestClassInt.fieldOffset2);
    }

    public static int unsafeSnippet5(long i1, boolean c) {
        int[] t = new int[2];
        UNSAFE.putLong(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(t, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet6(long i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet7(int i1, boolean c) {
        byte[] b = new byte[4];
        UNSAFE.putInt(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 0 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet8(long i1, int i2, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        UNSAFE.putInt(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + 4 * Unsafe.ARRAY_BYTE_INDEX_SCALE, i2);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 2 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet9(long i1, short i2, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        UNSAFE.putShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET + 4 * Unsafe.ARRAY_BYTE_INDEX_SCALE, i2);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet10(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 2 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static float unsafeSnippet11(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getFloat(b, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 4 + Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static long unsafeSnippet12(double i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putDouble(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static short unsafeSnippet13(short i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static int unsafeSnippet14(long l, int i, boolean c) {
        int[] t = new int[2];
        if (i < l) {
            UNSAFE.putLong(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, l);
        } else {
            UNSAFE.putInt(t, (long) Unsafe.ARRAY_INT_BASE_OFFSET, i);
        }
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(t, (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * 6 + Unsafe.ARRAY_INT_BASE_OFFSET);
    }

    public static int unsafeSnippet15(long i1, boolean c) {
        byte[] b = new byte[8];
        UNSAFE.putLong(b, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getShort(b, getUnsafeByteArrayOffset(0));
    }

    private static long getUnsafeByteArrayOffset(int i) {
        return (long) Unsafe.ARRAY_BYTE_INDEX_SCALE * i + Unsafe.ARRAY_BYTE_BASE_OFFSET;
    }

    public static byte[] unsafeSnippet16(long l, int i, short s, double d, float f, boolean c) {
        byte[] b = new byte[128];
        UNSAFE.putLong(b, getUnsafeByteArrayOffset(8), l);
        UNSAFE.putInt(b, getUnsafeByteArrayOffset(20), i);
        UNSAFE.putShort(b, getUnsafeByteArrayOffset(26), s);
        UNSAFE.putDouble(b, getUnsafeByteArrayOffset(32), d);
        UNSAFE.putFloat(b, getUnsafeByteArrayOffset(44), f);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return b;
    }

    public static long unsafeSnippet17(long i1, boolean c) {
        byte[] t = new byte[8];
        UNSAFE.putLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET, i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static long unsafeSnippet18(int i1, boolean c) {
        byte[] t = new byte[8];
        UNSAFE.putInt(t, getUnsafeByteArrayOffset(3), i1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, (long) Unsafe.ARRAY_BYTE_BASE_OFFSET);
    }

    public static long unsafeSnippet19(long l1, long l2, boolean c) {
        byte[] t = new byte[16];
        if (l1 < l2) {
            UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l1);
        } else {
            UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l2);
        }
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, getUnsafeByteArrayOffset(0));
    }

    public static long unsafeSnippet20(long l1, int i2, boolean c) {
        byte[] t = new byte[16];
        if (l1 < i2) {
            UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l1);
        } else {
            UNSAFE.putInt(t, getUnsafeByteArrayOffset(0), i2);
        }
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, getUnsafeByteArrayOffset(0));
    }

    public static long unsafeSnippet21(long l1, boolean c) {
        byte[] t = new byte[16];
        if (l1 < 0) {
            UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l1);
        } else {
            sideEffect();
        }
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, getUnsafeByteArrayOffset(0));
    }

    public static long unsafeSnippet22(long l1, double d1, boolean c) {
        byte[] t = new byte[16];
        if (l1 < d1) {
            UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l1);
        } else {
            UNSAFE.putDouble(t, getUnsafeByteArrayOffset(0), d1);
        }
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, getUnsafeByteArrayOffset(0));
    }

    public static long unsafeSnippet23(long l1, short s1, float f1, byte[][] box, boolean c) {
        byte[] t = new byte[16];
        UNSAFE.putLong(t, getUnsafeByteArrayOffset(0), l1);
        if (s1 < f1) {
            UNSAFE.putShort(t, getUnsafeByteArrayOffset(12), (short) 0);
        }
        // escape
        box[0] = t;
        UNSAFE.putFloat(t, getUnsafeByteArrayOffset(12), f1);
        sideEffect();
        if (c) {
            GraalDirectives.deoptimize();
        }
        return UNSAFE.getLong(t, getUnsafeByteArrayOffset(0));
    }

    @Test
    public void testUnsafePEA01() {
        performTest("unsafeSnippet1", false, true, 1.0);
    }

    @Test
    public void testUnsafePEA02() {
        performTest("unsafeSnippet2a", false, true, 1);

        performTest("unsafeSnippet2b", false, true, 1);
    }

    @Test
    public void testUnsafePEA03() {
        performTest("unsafeSnippet3a", false, true, 1);

        performTest("unsafeSnippet3b", false, true, 1);
    }

    @Test
    public void testUnsafePEA04() {
        performTest("unsafeSnippet4", false, true, 1.0);
    }

    @Test
    public void testUnsafePEA05() {
        performTest("unsafeSnippet5", false, true, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA06() {
        performTest("unsafeSnippet6", false, true, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA07() {
        performTest("unsafeSnippet7", false, true, 0x01020304);
    }

    @Test
    public void testUnsafePEA08() {
        performTest("unsafeSnippet8", false, true, 0x0102030405060708L, 0x01020304);
    }

    @Test
    public void testUnsafePEA09() {
        performTest("unsafeSnippet9", false, true, 0x0102030405060708L, (short) 0x0102);
    }

    @Test
    public void testUnsafePEA10() {
        performTest("unsafeSnippet10", false, true, Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA11() {
        performTest("unsafeSnippet11", false, true, Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA12() {
        performTest("unsafeSnippet12", false, true, Double.longBitsToDouble(0x0102030405060708L));
    }

    @Test
    public void testUnsafePEA13() {
        performTest("unsafeSnippet13", true, true, (short) 0x0102);
    }

    @Test
    public void testUnsafePEA14() {
        performTest("unsafeSnippet14", false, true, 0x0102030405060708L, 0x01020304);
    }

    @Test
    public void testUnsafePEA15() {
        performTest("unsafeSnippet15", false, true, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA16() {
        performTest("unsafeSnippet16", false, true, 0x0102030405060708L, 0x01020304, (short) 0x0102, Double.longBitsToDouble(0x0102030405060708L), Float.intBitsToFloat(0x01020304));
    }

    @Test
    public void testUnsafePEA17() {
        performTest("unsafeSnippet17", true, true, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA18() {
        Assume.assumeTrue(getBackend().getTarget().arch.supportsUnalignedMemoryAccess());
        performTest("unsafeSnippet18", false, false, 0x01020304);
    }

    @Test
    public void testUnsafePEA19() {
        performTest("unsafeSnippet19", true, true, 0x0102030405060708L, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA20() {
        performTest("unsafeSnippet20", false, false, 0x0102030405060708L, 0x01020304);
    }

    @Test
    public void testUnsafePEA21() {
        performTest("unsafeSnippet21", true, true, 0x0102030405060708L);
    }

    @Test
    public void testUnsafePEA22() {
        performTest("unsafeSnippet22", false, false, 0x0102030405060708L, Double.longBitsToDouble(0x0506070801020304L));
    }

    @Test
    public void testUnsafePEA23() {
        performTest("unsafeSnippet23", false, false, 0x0102030405060708L, (short) 0x0102, Float.intBitsToFloat(0x01020304), new byte[1][]);
    }

    private void performTest(String snippet, boolean shouldEscapeRead, boolean shouldEscapeWrite, Object... args) {
        Object[] boolArgs = Arrays.copyOf(args, args.length + 1);
        for (boolean b1 : FT) {
            for (boolean b2 : FT) {
                boolArgs[args.length] = b2;
                testPartialEscapeReadElimination(snippet, b1, shouldEscapeRead, shouldEscapeWrite, boolArgs);
            }
        }
    }

    public void testPartialEscapeReadElimination(String snippet, boolean canonicalizeBefore, boolean shouldEscapeRead, boolean shouldEscapeWrite, Object... args) {
        assert TestClassInt.fieldOffset1 % 8 == 0 : "First of the two int-fields must be 8-byte aligned";

        ResolvedJavaMethod method = getResolvedJavaMethod(snippet);
        StructuredGraph graph = parseEager(snippet, AllowAssumptions.NO);
        OptionValues options = graph.getOptions();
        CoreProviders context = getDefaultHighTierContext();
        CanonicalizerPhase canonicalizer = createCanonicalizerPhase();
        if (canonicalizeBefore) {
            canonicalizer.apply(graph, context);
        }
        Result r = executeExpected(method, null, args);
        int readCount = graph.getNodes().filter(RawLoadNode.class).count();
        int writeCount = graph.getNodes().filter(RawStoreNode.class).count();

        new PartialEscapePhase(true, true, canonicalizer, null, options).apply(graph, context);

        boolean canVirtualize = true;
        assert graph.getNodes().filter(VirtualObjectNode.class).count() == 1;
        VirtualObjectNode virtual = graph.getNodes().filter(VirtualObjectNode.class).first();
        if (virtual instanceof VirtualArrayNode) {
            VirtualArrayNode array = (VirtualArrayNode) virtual;
            if (array.isVirtualByteArray(context.getMetaAccessExtensionProvider())) {
                canVirtualize = context.getPlatformConfigurationProvider().canVirtualizeLargeByteArrayAccess();
            }
        }
        boolean escapeReads = shouldEscapeRead && canVirtualize;
        boolean escapeWrites = shouldEscapeWrite && canVirtualize;
        if (escapeReads) {
            int newCount = graph.getNodes().filter(RawLoadNode.class).count();
            assertTrue(readCount > newCount, "PEA did not escape reads. before: " + readCount + ", after " + newCount);
        }
        if (escapeWrites) {
            int newCount = graph.getNodes().filter(RawStoreNode.class).count();
            assertTrue(writeCount > newCount, "PEA did not escape writes, before: " + writeCount + ", after: " + newCount);
        }
        try {
            InstalledCode code = getCode(method, graph);
            Object result = code.executeVarargs(args);
            assertEquals(r, new Result(result, null));
        } catch (Throwable e) {
            assertFalse(true, e.toString());
        }
    }
}
