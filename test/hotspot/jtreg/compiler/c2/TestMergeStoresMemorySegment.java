/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package compiler.c2;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test id=byte-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment ByteArray
 */

/*
 * @test id=char-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment CharArray
 */

/*
 * @test id=short-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment ShortArray
 */

/*
 * @test id=int-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment IntArray
 */

/*
 * @test id=long-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment LongArray
 */

/*
 * @test id=float-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment FloatArray
 */

/*
 * @test id=double-array
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment DoubleArray
 */

/*
 * @test id=byte-buffer
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment ByteBuffer
 */

/*
 * @test id=byte-buffer-direct
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment ByteBufferDirect
 */

/*
 * @test id=native
 * @bug 8335392
 * @summary Test MergeStores optimization for MemorySegment
 * @library /test/lib /
 * @run driver compiler.c2.TestMergeStoresMemorySegment Native
 */

// FAILS: mixed providers currently do not merge stores. Maybe there is some inlining issue.
// /*
//  * @test id=mixed-array
//  * @bug 8335392
//  * @summary Test MergeStores optimization for MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.c2.TestMergeStoresMemorySegment MixedArray
//  */
//
// /*
//  * @test id=MixedBuffer
//  * @bug 8335392
//  * @summary Test MergeStores optimization for MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.c2.TestMergeStoresMemorySegment MixedBuffer
//  */
//
// /*
//  * @test id=mixed
//  * @bug 8335392
//  * @summary Test MergeStores optimization for MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.c2.TestMergeStoresMemorySegment Mixed
//  */

public class TestMergeStoresMemorySegment {
    public static void main(String[] args) {
        for (String unaligned : new String[]{"-XX:-UseUnalignedAccesses", "-XX:+UseUnalignedAccesses"}) {
            TestFramework framework = new TestFramework(TestMergeStoresMemorySegmentImpl.class);
            framework.addFlags("-DmemorySegmentProviderNameForTestVM=" + args[0], unaligned);
            framework.start();
        }
    }
}

class TestMergeStoresMemorySegmentImpl {
    static final int BACKING_SIZE = 1024 * 8;
    static final Random RANDOM = Utils.getRandomInstance();

    private static final String START = "(\\d+(\\s){2}(";
    private static final String MID = ".*)+(\\s){2}===.*";
    private static final String END = ")";

    // Custom Regex: allows us to only match Store that come from MemorySegment internals.
    private static final String REGEX_STORE_B_TO_MS_FROM_B = START + "StoreB" + MID + END + "ScopedMemoryAccess::putByteInternal";
    private static final String REGEX_STORE_C_TO_MS_FROM_B = START + "StoreC" + MID + END + "ScopedMemoryAccess::putByteInternal";
    private static final String REGEX_STORE_I_TO_MS_FROM_B = START + "StoreI" + MID + END + "ScopedMemoryAccess::putByteInternal";
    private static final String REGEX_STORE_L_TO_MS_FROM_B = START + "StoreL" + MID + END + "ScopedMemoryAccess::putByteInternal";

    interface TestFunction {
        Object[] run();
    }

    interface MemorySegmentProvider {
        MemorySegment newMemorySegment();
    }

    static MemorySegmentProvider provider;

    static {
        String providerName = System.getProperty("memorySegmentProviderNameForTestVM");
        provider = switch (providerName) {
            case "ByteArray"        -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfByteArray;
            case "CharArray"        -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfCharArray;
            case "ShortArray"       -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfShortArray;
            case "IntArray"         -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfIntArray;
            case "LongArray"        -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfLongArray;
            case "FloatArray"       -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfFloatArray;
            case "DoubleArray"      -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfDoubleArray;
            case "ByteBuffer"       -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfByteBuffer;
            case "ByteBufferDirect" -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfByteBufferDirect;
            case "Native"           -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfNative;
            case "MixedArray"       -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfMixedArray;
            case "MixedBuffer"      -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfMixedBuffer;
            case "Mixed"            -> TestMergeStoresMemorySegmentImpl::newMemorySegmentOfMixed;
            default -> throw new RuntimeException("Test argument not recognized: " + providerName);
        };
    }

    // List of tests
    Map<String, TestFunction> tests = new HashMap<>();

    // List of golden values, the results from the first run before compilation
    Map<String, Object[]> golds = new HashMap<>();

    public TestMergeStoresMemorySegmentImpl () {
        // Generate two MemorySegments as inputs
        MemorySegment a = newMemorySegment();
        MemorySegment b = newMemorySegment();
        fillRandom(a);
        fillRandom(b);

        // Future Work: add more test cases. For now, the issue seems to be that
        //              RangeCheck smearing does not remove the RangeChecks, thus
        //              we can only ever merge two stores.
        //
        // Ideas for more test cases, once they are better optimized:
        //
        //   Have about 3 variables, each either int or long. Add all in int or
        //   long. Give them different scales. Compute the address in the same
        //   expression or separately. Use different element store sizes (BCIL).
        //
        tests.put("test_xxx",       () -> test_xxx(copy(a), 5, 11, 31));
        tests.put("test_yyy",       () -> test_yyy(copy(a), 5, 11, 31));
        tests.put("test_zzz",       () -> test_zzz(copy(a), 5, 11, 31));

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            Object[] gold = test.run();
            golds.put(name, gold);
        }
    }

    MemorySegment newMemorySegment() {
        return provider.newMemorySegment();
    }

    MemorySegment copy(MemorySegment src) {
        MemorySegment dst = newMemorySegment();
        MemorySegment.copy(src, 0, dst, 0, src.byteSize());
        return dst;
    }

    static MemorySegment newMemorySegmentOfByteArray() {
        return MemorySegment.ofArray(new byte[BACKING_SIZE]);
    }

    static MemorySegment newMemorySegmentOfCharArray() {
        return MemorySegment.ofArray(new char[BACKING_SIZE / 2]);
    }

    static MemorySegment newMemorySegmentOfShortArray() {
        return MemorySegment.ofArray(new short[BACKING_SIZE / 2]);
    }

    static MemorySegment newMemorySegmentOfIntArray() {
        return MemorySegment.ofArray(new int[BACKING_SIZE / 4]);
    }

    static MemorySegment newMemorySegmentOfLongArray() {
        return MemorySegment.ofArray(new long[BACKING_SIZE / 8]);
    }

    static MemorySegment newMemorySegmentOfFloatArray() {
        return MemorySegment.ofArray(new float[BACKING_SIZE / 4]);
    }

    static MemorySegment newMemorySegmentOfDoubleArray() {
        return MemorySegment.ofArray(new double[BACKING_SIZE / 8]);
    }

    static MemorySegment newMemorySegmentOfByteBuffer() {
        return MemorySegment.ofBuffer(ByteBuffer.allocate(BACKING_SIZE));
    }

    static MemorySegment newMemorySegmentOfByteBufferDirect() {
        return MemorySegment.ofBuffer(ByteBuffer.allocateDirect(BACKING_SIZE));
    }

    static MemorySegment newMemorySegmentOfNative() {
        // Auto arena: GC decides when there is no reference to the MemorySegment,
        // and then it deallocates the backing memory.
        return Arena.ofAuto().allocate(BACKING_SIZE, 1);
    }

    static MemorySegment newMemorySegmentOfMixedArray() {
        switch(RANDOM.nextInt(7)) {
            case 0  -> { return newMemorySegmentOfByteArray(); }
            case 1  -> { return newMemorySegmentOfCharArray(); }
            case 2  -> { return newMemorySegmentOfShortArray(); }
            case 3  -> { return newMemorySegmentOfIntArray(); }
            case 4  -> { return newMemorySegmentOfLongArray(); }
            case 5  -> { return newMemorySegmentOfFloatArray(); }
            default -> { return newMemorySegmentOfDoubleArray(); }
        }
    }

    static MemorySegment newMemorySegmentOfMixedBuffer() {
        switch (RANDOM.nextInt(2)) {
            case 0  -> { return newMemorySegmentOfByteBuffer(); }
            default -> { return newMemorySegmentOfByteBufferDirect(); }
        }
    }

    static MemorySegment newMemorySegmentOfMixed() {
        switch (RANDOM.nextInt(3)) {
            case 0  -> { return newMemorySegmentOfMixedArray(); }
            case 1  -> { return newMemorySegmentOfMixedBuffer(); }
            default -> { return newMemorySegmentOfNative(); }
        }
    }

    static void fillRandom(MemorySegment data) {
        for (int i = 0; i < (int)data.byteSize(); i += 8) {
            data.set(ValueLayout.JAVA_LONG_UNALIGNED, i, RANDOM.nextLong());
        }
    }


    static void verify(String name, Object[] gold, Object[] result) {
        if (gold.length != result.length) {
            throw new RuntimeException("verify " + name + ": not the same number of outputs: gold.length = " +
                                       gold.length + ", result.length = " + result.length);
        }
        for (int i = 0; i < gold.length; i++) {
            Object g = gold[i];
            Object r = result[i];
            if (g == r) {
                throw new RuntimeException("verify " + name + ": should be two separate objects (with identical content):" +
                                           " gold[" + i + "] == result[" + i + "]");
            }

            if (!(g instanceof MemorySegment && r instanceof MemorySegment)) {
                throw new RuntimeException("verify " + name + ": only MemorySegment supported, i=" + i);
            }

            MemorySegment mg = (MemorySegment)g;
            MemorySegment mr = (MemorySegment)r;

            if (mg.byteSize() != mr.byteSize()) {
                throw new RuntimeException("verify " + name + ": MemorySegment must have same byteSize:" +
                                       " gold[" + i + "].byteSize = " + mg.byteSize() +
                                       " result[" + i + "].byteSize = " + mr.byteSize());
            }

            for (int j = 0; j < (int)mg.byteSize(); j++) {
                byte vg = mg.get(ValueLayout.JAVA_BYTE, j);
                byte vr = mr.get(ValueLayout.JAVA_BYTE, j);
                if (vg != vr) {
                    throw new RuntimeException("verify " + name + ": MemorySegment must have same content:" +
                                               " gold[" + i + "][" + j + "] = " + vg +
                                               " result[" + i + "][" + j + "] = " + vr);
                }
            }
        }
    }

    @Run(test = { "test_xxx", "test_yyy", "test_zzz" })
    void runTests() {
        for (Map.Entry<String,TestFunction> entry : tests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object[] gold = golds.get(name);
            // Compute new result
            Object[] result = test.run();
            // Compare gold and new result
            verify(name, gold, result);
        }
    }

    @Test
    @IR(counts = {REGEX_STORE_B_TO_MS_FROM_B, "<=5", // 4x RC
                  REGEX_STORE_C_TO_MS_FROM_B, ">=3", // 4x merged
                  REGEX_STORE_I_TO_MS_FROM_B, "0",
                  REGEX_STORE_L_TO_MS_FROM_B, "0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test_xxx(MemorySegment a, int xI, int yI, int zI) {
         // All RangeChecks remain -> RC smearing not good enough?
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 0), (byte)'h');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 1), (byte)'e');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 2), (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 3), (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 4), (byte)'o');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 5), (byte)' ');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 6), (byte)':');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI + yI + zI + 7), (byte)')');
         return new Object[]{ a };
    }

    @Test
    @IR(counts = {REGEX_STORE_B_TO_MS_FROM_B, "<=5", // 4x RC
                  REGEX_STORE_C_TO_MS_FROM_B, ">=3", // 4x merged
                  REGEX_STORE_I_TO_MS_FROM_B, "0",
                  REGEX_STORE_L_TO_MS_FROM_B, "0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test_yyy(MemorySegment a, int xI, int yI, int zI) {
         // All RangeChecks remain -> RC smearing not good enough?
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 0L, (byte)'h');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 1L, (byte)'e');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 2L, (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 3L, (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 4L, (byte)'o');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 5L, (byte)' ');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 6L, (byte)':');
         a.set(ValueLayout.JAVA_BYTE, (long)(xI) + (long)(yI) + (long)(zI) + 7L, (byte)')');
         return new Object[]{ a };
    }

    @Test
    @IR(counts = {REGEX_STORE_B_TO_MS_FROM_B, "<=5", // 4x RC
                  REGEX_STORE_C_TO_MS_FROM_B, ">=3", // 4x merged
                  REGEX_STORE_I_TO_MS_FROM_B, "0",
                  REGEX_STORE_L_TO_MS_FROM_B, "0"},
        phase = CompilePhase.PRINT_IDEAL,
        applyIf = {"UseUnalignedAccesses", "true"})
    static Object[] test_zzz(MemorySegment a, long xL, long yL, long zL) {
         // All RangeChecks remain -> RC smearing not good enough?
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 0L, (byte)'h');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 1L, (byte)'e');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 2L, (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 3L, (byte)'l');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 4L, (byte)'o');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 5L, (byte)' ');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 6L, (byte)':');
         a.set(ValueLayout.JAVA_BYTE, xL + yL + zL + 7L, (byte)')');
         return new Object[]{ a };
    }
}
