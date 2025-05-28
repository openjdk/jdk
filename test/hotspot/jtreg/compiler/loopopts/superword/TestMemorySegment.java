/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test id=byte-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteArray
 */

/*
 * @test id=byte-array-AlignVector
 * @bug 8329273 8348263
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteArray AlignVector
 */

/*
 * @test id=byte-array-NoShortRunningLongLoop
 * @bug 8329273 8342692
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteArray NoShortRunningLongLoop
 */

/*
 * @test id=byte-array-AlignVector-NoShortRunningLongLoop
 * @bug 8329273 8348263 8342692
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteArray AlignVector NoShortRunningLongLoop
 */


/*
 * @test id=char-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment CharArray
 */

/*
 * @test id=short-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ShortArray
 */

/*
 * @test id=int-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment IntArray
 */

/*
 * @test id=int-array-AlignVector
 * @bug 8329273 8348263
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment IntArray AlignVector
 */

/*
 * @test id=long-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment LongArray
 */

/*
 * @test id=long-array-AlignVector
 * @bug 8329273 8348263
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment LongArray AlignVector
 */

/*
 * @test id=float-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment FloatArray
 */

/*
 * @test id=double-array
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment DoubleArray
 */

/*
 * @test id=byte-buffer
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteBuffer
 */

/*
 * @test id=byte-buffer-direct
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment ByteBufferDirect
 */

/*
 * @test id=native
 * @bug 8329273
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment Native
 */

/*
 * @test id=native-AlignVector
 * @bug 8329273 8348263
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegment Native AlignVector
 */

// FAILS: mixed providers currently do not vectorize. Maybe there is some inlining issue.
// /*
//  * @test id=mixed-array
//  * @bug 8329273
//  * @summary Test vectorization of loops over MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.loopopts.superword.TestMemorySegment MixedArray
//  */
//
// /*
//  * @test id=MixedBuffer
//  * @bug 8329273
//  * @summary Test vectorization of loops over MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.loopopts.superword.TestMemorySegment MixedBuffer
//  */
//
// /*
//  * @test id=mixed
//  * @bug 8329273
//  * @summary Test vectorization of loops over MemorySegment
//  * @library /test/lib /
//  * @run driver compiler.loopopts.superword.TestMemorySegment Mixed
//  */

public class TestMemorySegment {
    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMemorySegmentImpl.class);
        framework.addFlags("-DmemorySegmentProviderNameForTestVM=" + args[0]);
        for (int i = 1; i < args.length; i++) {
            String tag = args[i];
            switch (tag) {
                case "AlignVector" ->                framework.addFlags("-XX:+AlignVector");
                case "NoShortRunningLongLoop" ->     framework.addFlags("-XX:-ShortRunningLongLoop");
            }
        }
        if (args.length > 1 && args[1].equals("AlignVector")) {
            framework.addFlags("-XX:+AlignVector");
        }
        framework.setDefaultWarmup(100);
        framework.start();
    }
}

class TestMemorySegmentImpl {
    static final int BACKING_SIZE = 1024 * 8;
    static final Random RANDOM = Utils.getRandomInstance();


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
            case "ByteArray"        -> TestMemorySegmentImpl::newMemorySegmentOfByteArray;
            case "CharArray"        -> TestMemorySegmentImpl::newMemorySegmentOfCharArray;
            case "ShortArray"       -> TestMemorySegmentImpl::newMemorySegmentOfShortArray;
            case "IntArray"         -> TestMemorySegmentImpl::newMemorySegmentOfIntArray;
            case "LongArray"        -> TestMemorySegmentImpl::newMemorySegmentOfLongArray;
            case "FloatArray"       -> TestMemorySegmentImpl::newMemorySegmentOfFloatArray;
            case "DoubleArray"      -> TestMemorySegmentImpl::newMemorySegmentOfDoubleArray;
            case "ByteBuffer"       -> TestMemorySegmentImpl::newMemorySegmentOfByteBuffer;
            case "ByteBufferDirect" -> TestMemorySegmentImpl::newMemorySegmentOfByteBufferDirect;
            case "Native"           -> TestMemorySegmentImpl::newMemorySegmentOfNative;
            case "MixedArray"       -> TestMemorySegmentImpl::newMemorySegmentOfMixedArray;
            case "MixedBuffer"      -> TestMemorySegmentImpl::newMemorySegmentOfMixedBuffer;
            case "Mixed"            -> TestMemorySegmentImpl::newMemorySegmentOfMixed;
            default -> throw new RuntimeException("Test argument not recognized: " + providerName);
        };
    }

    // List of tests
    Map<String, TestFunction> tests = new HashMap<>();

    // List of gold, the results from the first run before compilation
    Map<String, Object[]> golds = new HashMap<>();

    public TestMemorySegmentImpl () {
        // Generate two MemorySegments as inputs
        MemorySegment a = newMemorySegment();
        MemorySegment b = newMemorySegment();
        fillRandom(a);
        fillRandom(b);

        // Add all tests to list
        tests.put("testMemorySegmentBadExitCheck",                 () -> testMemorySegmentBadExitCheck(copy(a)));
        tests.put("testIntLoop_iv_byte",                           () -> testIntLoop_iv_byte(copy(a)));
        tests.put("testIntLoop_longIndex_intInvar_sameAdr_byte",   () -> testIntLoop_longIndex_intInvar_sameAdr_byte(copy(a), 0));
        tests.put("testIntLoop_longIndex_longInvar_sameAdr_byte",  () -> testIntLoop_longIndex_longInvar_sameAdr_byte(copy(a), 0));
        tests.put("testIntLoop_longIndex_intInvar_byte",           () -> testIntLoop_longIndex_intInvar_byte(copy(a), 0));
        tests.put("testIntLoop_longIndex_longInvar_byte",          () -> testIntLoop_longIndex_longInvar_byte(copy(a), 0));
        tests.put("testIntLoop_intIndex_intInvar_byte",            () -> testIntLoop_intIndex_intInvar_byte(copy(a), 0));
        tests.put("testIntLoop_iv_int",                            () -> testIntLoop_iv_int(copy(a)));
        tests.put("testIntLoop_longIndex_intInvar_sameAdr_int",    () -> testIntLoop_longIndex_intInvar_sameAdr_int(copy(a), 0));
        tests.put("testIntLoop_longIndex_longInvar_sameAdr_int",   () -> testIntLoop_longIndex_longInvar_sameAdr_int(copy(a), 0));
        tests.put("testIntLoop_longIndex_intInvar_int",            () -> testIntLoop_longIndex_intInvar_int(copy(a), 0));
        tests.put("testIntLoop_longIndex_longInvar_int",           () -> testIntLoop_longIndex_longInvar_int(copy(a), 0));
        tests.put("testIntLoop_intIndex_intInvar_int",             () -> testIntLoop_intIndex_intInvar_int(copy(a), 0));
        tests.put("testLongLoop_iv_byte",                          () -> testLongLoop_iv_byte(copy(a)));
        tests.put("testLongLoop_longIndex_intInvar_sameAdr_byte",  () -> testLongLoop_longIndex_intInvar_sameAdr_byte(copy(a), 0));
        tests.put("testLongLoop_longIndex_longInvar_sameAdr_byte", () -> testLongLoop_longIndex_longInvar_sameAdr_byte(copy(a), 0));
        tests.put("testLongLoop_longIndex_intInvar_byte",          () -> testLongLoop_longIndex_intInvar_byte(copy(a), 0));
        tests.put("testLongLoop_longIndex_longInvar_byte",         () -> testLongLoop_longIndex_longInvar_byte(copy(a), 0));
        tests.put("testLongLoop_intIndex_intInvar_byte",           () -> testLongLoop_intIndex_intInvar_byte(copy(a), 0));
        tests.put("testLongLoop_iv_int",                           () -> testLongLoop_iv_int(copy(a)));
        tests.put("testLongLoop_longIndex_intInvar_sameAdr_int",   () -> testLongLoop_longIndex_intInvar_sameAdr_int(copy(a), 0));
        tests.put("testLongLoop_longIndex_longInvar_sameAdr_int",  () -> testLongLoop_longIndex_longInvar_sameAdr_int(copy(a), 0));
        tests.put("testLongLoop_longIndex_intInvar_int",           () -> testLongLoop_longIndex_intInvar_int(copy(a), 0));
        tests.put("testLongLoop_longIndex_longInvar_int",          () -> testLongLoop_longIndex_longInvar_int(copy(a), 0));
        tests.put("testLongLoop_intIndex_intInvar_int",            () -> testLongLoop_intIndex_intInvar_int(copy(a), 0));

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

    @Run(test = {"testMemorySegmentBadExitCheck",
                 "testIntLoop_iv_byte",
                 "testIntLoop_longIndex_intInvar_sameAdr_byte",
                 "testIntLoop_longIndex_longInvar_sameAdr_byte",
                 "testIntLoop_longIndex_intInvar_byte",
                 "testIntLoop_longIndex_longInvar_byte",
                 "testIntLoop_intIndex_intInvar_byte",
                 "testIntLoop_iv_int",
                 "testIntLoop_longIndex_intInvar_sameAdr_int",
                 "testIntLoop_longIndex_longInvar_sameAdr_int",
                 "testIntLoop_longIndex_intInvar_int",
                 "testIntLoop_longIndex_longInvar_int",
                 "testIntLoop_intIndex_intInvar_int",
                 "testLongLoop_iv_byte",
                 "testLongLoop_longIndex_intInvar_sameAdr_byte",
                 "testLongLoop_longIndex_longInvar_sameAdr_byte",
                 "testLongLoop_longIndex_intInvar_byte",
                 "testLongLoop_longIndex_longInvar_byte",
                 "testLongLoop_intIndex_intInvar_byte",
                 "testLongLoop_iv_int",
                 "testLongLoop_longIndex_intInvar_sameAdr_int",
                 "testLongLoop_longIndex_longInvar_sameAdr_int",
                 "testLongLoop_longIndex_intInvar_int",
                 "testLongLoop_longIndex_longInvar_int",
                 "testLongLoop_intIndex_intInvar_int"})
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
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS
    // Exit check: iv < long_limit      ->     (long)iv < long_limit
    // Thus, we have an int-iv, but a long-exit-check.
    // Is not properly recognized by either CountedLoop or LongCountedLoop
    static Object[] testMemorySegmentBadExitCheck(MemorySegment a) {
        for (int i = 0; i < a.byteSize(); i++) {
            long adr = i;
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_iv_byte(MemorySegment a) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr = i;
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_intInvar_sameAdr_byte(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_longInvar_sameAdr_byte(MemorySegment a, long invar) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_intInvar_byte(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(invar);
            a.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_longInvar_byte(MemorySegment a, long invar) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(invar);
            a.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: RangeCheck cannot be eliminated because of int_index
    static Object[] testIntLoop_intIndex_intInvar_byte(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            int int_index = i + invar;
            byte v = a.get(ValueLayout.JAVA_BYTE, int_index);
            a.set(ValueLayout.JAVA_BYTE, int_index, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_iv_int(MemorySegment a) {
        for (int i = 0; i < (int)a.byteSize()/4; i++ ) {
            long adr = 4L * i;
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_intInvar_sameAdr_int(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_longInvar_sameAdr_int(MemorySegment a, long invar) {
        for (int i = 0; i < (int)a.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_intInvar_int(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(invar);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testIntLoop_longIndex_longInvar_int(MemorySegment a, long invar) {
        for (int i = 0; i < (int)a.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(invar);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.ADD_VI,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: RangeCheck cannot be eliminated because of int_index
    static Object[] testIntLoop_intIndex_intInvar_int(MemorySegment a, int invar) {
        for (int i = 0; i < (int)a.byteSize()/4; i++) {
            int int_index = i + invar;
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_iv_byte(MemorySegment a) {
        for (long i = 0; i < a.byteSize(); i++) {
            long adr = i;
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_longIndex_intInvar_sameAdr_byte(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize(); i++) {
            long adr = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_longIndex_longInvar_sameAdr_byte(MemorySegment a, long invar) {
        for (long i = 0; i < a.byteSize(); i++) {
            long adr = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    // FAILS: invariants are sorted differently, because of differently inserted Cast.
    // See: JDK-8331659
    // Interestingly, it now vectorizes for native, but not for arrays.
    static Object[] testLongLoop_longIndex_intInvar_byte(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(invar);
            a.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    // FAILS: invariants are sorted differently, because of differently inserted Cast.
    // See: JDK-8331659
    // Interestingly, it now vectorizes for native, but not for arrays.
    static Object[] testLongLoop_longIndex_longInvar_byte(MemorySegment a, long invar) {
        for (long i = 0; i < a.byteSize(); i++) {
            long adr1 = (long)(i) + (long)(invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, adr1);
            long adr2 = (long)(i) + (long)(invar);
            a.set(ValueLayout.JAVA_BYTE, adr2, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "= 0",
                  IRNode.ADD_VB,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: RangeCheck cannot be eliminated because of int_index
    static Object[] testLongLoop_intIndex_intInvar_byte(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize(); i++) {
            int int_index = (int)(i + invar);
            byte v = a.get(ValueLayout.JAVA_BYTE, int_index);
            a.set(ValueLayout.JAVA_BYTE, int_index, (byte)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_iv_int(MemorySegment a) {
        for (long i = 0; i < a.byteSize()/4; i++ ) {
            long adr = 4L * i;
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_longIndex_intInvar_sameAdr_int(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    static Object[] testLongLoop_longIndex_longInvar_sameAdr_int(MemorySegment a, long invar) {
        for (long i = 0; i < a.byteSize()/4; i++) {
            long adr = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.ADD_VI,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfAnd = { "ShortRunningLongLoop", "false", "AlignVector", "false" },
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfAnd = { "ShortRunningLongLoop", "true", "AlignVector", "false" },
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: invariants are sorted differently, because of differently inserted Cast.
    // See: JDK-8331659
    static Object[] testLongLoop_longIndex_intInvar_int(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(invar);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.ADD_VI,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfAnd = { "ShortRunningLongLoop", "false", "AlignVector", "false" },
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    @IR(counts = {IRNode.LOAD_VECTOR_I, "> 0",
                  IRNode.ADD_VI,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfAnd = { "ShortRunningLongLoop", "true", "AlignVector", "false" },
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: invariants are sorted differently, because of differently inserted Cast.
    // See: JDK-8331659
    static Object[] testLongLoop_longIndex_longInvar_int(MemorySegment a, long invar) {
        for (long i = 0; i < a.byteSize()/4; i++) {
            long adr1 = 4L * (long)(i) + 4L * (long)(invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, adr1);
            long adr2 = 4L * (long)(i) + 4L * (long)(invar);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, adr2, (int)(v + 1));
        }
        return new Object[]{ a };
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I, "= 0",
                  IRNode.ADD_VI,        "= 0",
                  IRNode.STORE_VECTOR,  "= 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true", "rvv", "true"})
    // FAILS: RangeCheck cannot be eliminated because of int_index
    static Object[] testLongLoop_intIndex_intInvar_int(MemorySegment a, int invar) {
        for (long i = 0; i < a.byteSize()/4; i++) {
            int int_index = (int)(i + invar);
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index);
            a.set(ValueLayout.JAVA_INT_UNALIGNED, 4L * int_index, (int)(v + 1));
        }
        return new Object[]{ a };
    }
}
