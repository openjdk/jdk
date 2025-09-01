/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
import compiler.lib.verify.*;
import jdk.test.lib.Utils;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test id=byte-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray
 */

/*
 * @test id=byte-array-AlignVector
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray AlignVector
 */

/*
 * @test id=byte-array-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray NoSpeculativeAliasingCheck
 */

/*
 * @test id=byte-array-AlignVector-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray AlignVector NoSpeculativeAliasingCheck
 */

/*
 * @test id=byte-array-NoAutoAlignment
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray NoAutoAlignment
 */

/*
 * @test id=byte-array-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray NoShortRunningLongLoop
 */

/*
 * @test id=byte-array-AlignVector-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray AlignVector NoShortRunningLongLoop
 */

/*
 * @test id=byte-array-NoSpeculativeAliasingCheck-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteArray NoSpeculativeAliasingCheck NoShortRunningLongLoop
 */

/*
 * @test id=char-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing CharArray
 */

/*
 * @test id=short-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ShortArray
 */

/*
 * @test id=int-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray
 */

/*
 * @test id=int-array-AlignVector
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray AlignVector
 */

/*
 * @test id=int-array-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray NoSpeculativeAliasingCheck
 */

/*
 * @test id=int-array-AlignVector-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray AlignVector NoSpeculativeAliasingCheck
 */

/*
 * @test id=int-array-NoAutoAlignment
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray NoAutoAlignment
 */

/*
 * @test id=int-array-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray NoShortRunningLongLoop
 */

/*
 * @test id=int-array-NoSpeculativeAliasingCheck-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing IntArray NoSpeculativeAliasingCheck NoShortRunningLongLoop
 */

/*
 * @test id=long-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing LongArray
 */

/*
 * @test id=long-array-AlignVector
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing LongArray AlignVector
 */

/*
 * @test id=long-array-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing LongArray NoSpeculativeAliasingCheck
 */

/*
 * @test id=long-array-AlignVector-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing LongArray AlignVector NoSpeculativeAliasingCheck
 */

/*
 * @test id=float-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing FloatArray
 */

/*
 * @test id=double-array
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing DoubleArray
 */

/*
 * @test id=byte-buffer
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteBuffer
 */

/*
 * @test id=byte-buffer-direct
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing ByteBufferDirect
 */

/*
 * @test id=native
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native
 */

/*
 * @test id=native-AlignVector
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native AlignVector
 */

/*
 * @test id=native-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native NoSpeculativeAliasingCheck
 */

/*
 * @test id=native-AlignVector-NoSpeculativeAliasingCheck
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native AlignVector NoSpeculativeAliasingCheck
 */

/*
 * @test id=native-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native NoShortRunningLongLoop
 */

/*
 * @test id=native-AlignVector-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native AlignVector NoShortRunningLongLoop
 */

/*
 * @test id=native-NoSpeculativeAliasingCheck-NoShortRunningLongLoop
 * @bug 8324751
 * @summary Test vectorization of loops over MemorySegment
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentAliasing Native NoSpeculativeAliasingCheck NoShortRunningLongLoop
 */

public class TestMemorySegmentAliasing {
    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMemorySegmentAliasingImpl.class);
        framework.addFlags("-DmemorySegmentProviderNameForTestVM=" + args[0]);
        for (int i = 1; i < args.length; i++) {
            String tag = args[i];
            switch (tag) {
                case "AlignVector" ->                framework.addFlags("-XX:+AlignVector");
                case "NoSpeculativeAliasingCheck" -> framework.addFlags("-XX:-UseAutoVectorizationSpeculativeAliasingChecks");
                // automatic alignment has an impact on where the main-loop starts, and that affects init and limit
                // of the main loop.
                case "NoAutoAlignment" ->            framework.addFlags("-XX:SuperWordAutomaticAlignment=0");
                // Disabling the ShortRunningLongLoop optimization changes the shape of the loop.
                // Testing both with and without it allows us to simulate long running loops with short running loops,
                // i.e. we don't need to allocate massive amounts of memory.
                case "NoShortRunningLongLoop" ->     framework.addFlags("-XX:-ShortRunningLongLoop");
                default ->                           throw new RuntimeException("Bad tag: " + tag);
            }
        }
        framework.setDefaultWarmup(100);
        framework.start();
    }
}

class TestMemorySegmentAliasingImpl {
    static final int BACKING_SIZE = 1024 * 8;
    static final Random RANDOM = Utils.getRandomInstance();


    interface TestFunction {
        void run();
    }

    interface MemorySegmentProvider {
        MemorySegment newMemorySegment();
    }

    public static MemorySegmentProvider provider;

    static {
        String providerName = System.getProperty("memorySegmentProviderNameForTestVM");
        provider = switch (providerName) {
            case "ByteArray"        -> TestMemorySegmentAliasingImpl::newMemorySegmentOfByteArray;
            case "CharArray"        -> TestMemorySegmentAliasingImpl::newMemorySegmentOfCharArray;
            case "ShortArray"       -> TestMemorySegmentAliasingImpl::newMemorySegmentOfShortArray;
            case "IntArray"         -> TestMemorySegmentAliasingImpl::newMemorySegmentOfIntArray;
            case "LongArray"        -> TestMemorySegmentAliasingImpl::newMemorySegmentOfLongArray;
            case "FloatArray"       -> TestMemorySegmentAliasingImpl::newMemorySegmentOfFloatArray;
            case "DoubleArray"      -> TestMemorySegmentAliasingImpl::newMemorySegmentOfDoubleArray;
            case "ByteBuffer"       -> TestMemorySegmentAliasingImpl::newMemorySegmentOfByteBuffer;
            case "ByteBufferDirect" -> TestMemorySegmentAliasingImpl::newMemorySegmentOfByteBufferDirect;
            case "Native"           -> TestMemorySegmentAliasingImpl::newMemorySegmentOfNative;
            case "MixedArray"       -> TestMemorySegmentAliasingImpl::newMemorySegmentOfMixedArray;
            case "MixedBuffer"      -> TestMemorySegmentAliasingImpl::newMemorySegmentOfMixedBuffer;
            case "Mixed"            -> TestMemorySegmentAliasingImpl::newMemorySegmentOfMixed;
            default -> throw new RuntimeException("Test argument not recognized: " + providerName);
        };
    }

    // Map of goldTests
    public static Map<String, TestFunction> goldTests = new HashMap<>();

    // Map of gold for the goldTests, the results from the first run before compilation
    public static Map<String, Object> golds = new HashMap<>();

    // Map of referenceTests, i.e. tests that have a reference implementation that is run with the interpreter.
    // The TestFunction must run both the test and reference methods.
    public static Map<String, TestFunction> referenceTests = new HashMap<>();

    // Original data.
    public static MemorySegment ORIG_A = fillRandom(newMemorySegment());
    public static MemorySegment ORIG_B = fillRandom(newMemorySegment());
    public static MemorySegment ORIG_C = fillRandom(newMemorySegment());

    // The data we use in the tests. It is initialized from ORIG_* every time.
    public static MemorySegment A = newMemorySegment();
    public static MemorySegment B = newMemorySegment();
    public static MemorySegment C = newMemorySegment();

    // Parallel to data above, but for use in reference methods.
    public static MemorySegment A_REFERENCE = newMemorySegment();
    public static MemorySegment B_REFERENCE = newMemorySegment();
    public static MemorySegment C_REFERENCE = newMemorySegment();

    public TestMemorySegmentAliasingImpl () {
        // Add all goldTests to list
        goldTests.put("test_byte_incr_noaliasing",     () -> test_byte_incr_noaliasing(A, B));
        goldTests.put("test_byte_incr_aliasing",       () -> test_byte_incr_aliasing(A, A));
        goldTests.put("test_byte_incr_aliasing_fwd3",  () -> {
            MemorySegment x = A.asSlice(0, BACKING_SIZE - 3);
            MemorySegment y = A.asSlice(3, BACKING_SIZE - 3);
            test_byte_incr_aliasing_fwd3(x, y);
        });
        goldTests.put("test_byte_incr_noaliasing_fwd128",  () -> {
            MemorySegment x = A.asSlice(0,   BACKING_SIZE - 128);
            MemorySegment y = A.asSlice(120, BACKING_SIZE - 128);
            test_byte_incr_noaliasing_fwd128(x, y);
        });

        goldTests.put("test_int_to_long_noaliasing",   () -> test_int_to_long_noaliasing(A, B));

        // Compute gold value for all test methods before compilation
        for (Map.Entry<String,TestFunction> entry : goldTests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            init();
            test.run();
            Object gold = snapshotCopy();
            golds.put(name, gold);
        }

        referenceTests.put("test_fill_byte_sameMS_alias", () -> {
            int invar1 = RANDOM.nextInt(64);
            int invar2 = RANDOM.nextInt(64);
            test_fill_byte_sameMS_alias(A, A, invar1, invar2);
            reference_fill_byte_sameMS_alias(A_REFERENCE, A_REFERENCE, invar1, invar2);
        });
        referenceTests.put("test_fill_byte_sameMS_noalias", () -> {
            // The accesses either start at the middle and go out,
            // or start from opposite sides and meet in the middle.
            // But they never overlap.
            //      <------|------>
            //      ------>|<------
            //
            // This tests that the checks we emit are not too relaxed.
            int middle = BACKING_SIZE / 2 + RANDOM.nextInt(-256, 256);
            int limit = BACKING_SIZE / 3 + RANDOM.nextInt(256);
            int invar1 = middle;
            int invar2 = middle;
            if (RANDOM.nextBoolean()) {
                invar1 -= limit;
                invar2 += limit;
            }
            test_fill_byte_sameMS_noalias(A, A, invar1, invar2, limit);
            reference_fill_byte_sameMS_noalias(A_REFERENCE, A_REFERENCE, invar1, invar2, limit);
        });
        referenceTests.put("test_fill_byte_sameMS_maybeAlias", () -> {
            // The accesses either start at the middle and go out,
            // or start from opposite sides and meet in the middle.
            // In the middle, sometimes we overlap and sometimes not.
            //      <------|------>
            //      ------>|<------
            //
            // This tests that the checks we emit are not too relaxed.
            int middle = BACKING_SIZE / 2 + RANDOM.nextInt(-256, 256);
            int limit = BACKING_SIZE / 3 + RANDOM.nextInt(256);
            int invar1 = middle + RANDOM.nextInt(-256, 256);
            int invar2 = middle + RANDOM.nextInt(-256, 256);
            // Are the bounds safe? Assume extreme values:
            // invar1 = 8k/2 + 256 + 256
            // limit = 8k/3 + 256
            // invar1 + limit = 8k * 5/6 + 3 * 256
            //                = 8k * 5/6 + 3/4 * 1k = 7.41k < 8k
            if (RANDOM.nextBoolean()) {
                invar1 -= limit;
                invar2 += limit;
            }
            test_fill_byte_sameMS_maybeAlias(A, A, invar1, invar2, limit);
            reference_fill_byte_sameMS_maybeAlias(A_REFERENCE, A_REFERENCE, invar1, invar2, limit);
        });
        referenceTests.put("test_fill_int_sameMS_alias", () -> {
            int invar1 = RANDOM.nextInt(64);
            int invar2 = RANDOM.nextInt(64);
            test_fill_int_sameMS_alias(A, A, invar1, invar2);
            reference_fill_int_sameMS_alias(A_REFERENCE, A_REFERENCE, invar1, invar2);
        });
        referenceTests.put("test_fill_int_sameMS_noalias", () -> {
            // The accesses either start at the middle and go out,
            // or start from opposite sides and meet in the middle.
            // But they never overlap.
            //      <------|------>
            //      ------>|<------
            //
            // This tests that the checks we emit are not too relaxed.
            int middle = BACKING_SIZE / 2 + RANDOM.nextInt(-256, 256);
            int limit = BACKING_SIZE / 3 + RANDOM.nextInt(256);
            int invar1 = middle;
            int invar2 = middle;
            if (RANDOM.nextBoolean()) {
                invar1 -= limit;
                invar2 += limit;
            }
            test_fill_int_sameMS_noalias(A, A, invar1, invar2, limit);
            reference_fill_int_sameMS_noalias(A_REFERENCE, A_REFERENCE, invar1, invar2, limit);
        });
        referenceTests.put("test_fill_int_sameMS_maybeAlias", () -> {
            // The accesses either start at the middle and go out,
            // or start from opposite sides and meet in the middle.
            // In the middle, sometimes we overlap and sometimes not.
            //      <------|------>
            //      ------>|<------
            //
            // This tests that the checks we emit are not too relaxed.
            int middle = BACKING_SIZE / 2 + RANDOM.nextInt(-256, 256);
            int limit = BACKING_SIZE / 3 + RANDOM.nextInt(256);
            int invar1 = middle + RANDOM.nextInt(-256, 256);
            int invar2 = middle + RANDOM.nextInt(-256, 256);
            // Are the bounds safe? Assume extreme values:
            // invar1 = 8k/2 + 256 + 256
            // limit = 8k/3 + 256
            // invar1 + limit = 8k * 5/6 + 3 * 256
            //                = 8k * 5/6 + 3/4 * 1k = 7.41k < 8k
            if (RANDOM.nextBoolean()) {
                invar1 -= limit;
                invar2 += limit;
            }
            test_fill_int_sameMS_maybeAlias(A, A, invar1, invar2, limit);
            reference_fill_int_sameMS_maybeAlias(A_REFERENCE, A_REFERENCE, invar1, invar2, limit);
        });
    }

    static MemorySegment newMemorySegment() {
        return provider.newMemorySegment();
    }

    static MemorySegment copy(MemorySegment src) {
        MemorySegment dst = newMemorySegment();
        dst.copyFrom(src);
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

    static MemorySegment fillRandom(MemorySegment data) {
        for (int i = 0; i < (int)data.byteSize(); i += 8) {
            data.set(ValueLayout.JAVA_LONG_UNALIGNED, i, RANDOM.nextLong());
        }
        return data;
    }

    public static void init() {
        A.copyFrom(ORIG_A);
        B.copyFrom(ORIG_B);
        C.copyFrom(ORIG_C);
    }

    public static void initReference() {
        A_REFERENCE.copyFrom(ORIG_A);
        B_REFERENCE.copyFrom(ORIG_B);
        C_REFERENCE.copyFrom(ORIG_C);
    }

    public static Object snapshotCopy() {
        return new Object[]{copy(A), copy(B), copy(C)};
    }

    public static Object snapshot() {
        return new Object[]{A, B, C};
    }

    public static Object snapshotReference() {
        return new Object[]{A_REFERENCE, B_REFERENCE, C_REFERENCE};
    }

    @Run(test = {"test_byte_incr_noaliasing",
                 "test_byte_incr_aliasing",
                 "test_byte_incr_aliasing_fwd3",
                 "test_byte_incr_noaliasing_fwd128",
                 "test_int_to_long_noaliasing",
                 "test_fill_byte_sameMS_alias",
                 "test_fill_byte_sameMS_noalias",
                 "test_fill_byte_sameMS_maybeAlias",
                 "test_fill_int_sameMS_alias",
                 "test_fill_int_sameMS_noalias",
                 "test_fill_int_sameMS_maybeAlias"})
    void runTests(RunInfo info) {
        for (Map.Entry<String,TestFunction> entry : goldTests.entrySet()) {
            String name = entry.getKey();
            TestFunction test = entry.getValue();
            // Recall gold value from before compilation
            Object gold = golds.get(name);
            // Compute new result
            init();
            test.run();
            Object result = snapshot();
            // Compare gold and new result
            try {
                Verify.checkEQ(gold, result);
            } catch (VerifyException e) {
                throw new RuntimeException("Verify failed for " + name, e);
            }
        }

        // Once warmup is over (100x), repeat 10x to get reasonable coverage of the
        // randomness in the tests.
        int reps = info.isWarmUp() ? 10 : 1;
        for (int r = 0; r < reps; r++) {
            for (Map.Entry<String,TestFunction> entry : referenceTests.entrySet()) {
                String name = entry.getKey();
                TestFunction test = entry.getValue();
                // Init data for test and reference
                init();
                initReference();
                // Run test and reference
                test.run();
                // Capture results from test and reference
                Object result = snapshot();
                Object expected = snapshotReference();
                // Compare expected and new result
                try {
                    Verify.checkEQ(expected, result);
                } catch (VerifyException e) {
                    throw new RuntimeException("Verify failed for " + name, e);
                }
            }
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0",
                  ".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_byte_incr_noaliasing(MemorySegment a, MemorySegment b) {
        for (long i = 0; i < a.byteSize(); i++) {
            byte v = a.get(ValueLayout.JAVA_BYTE, i);
            b.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0",
                  ".*multiversion.*",   "> 0"}, // AutoVectorization Predicate FAILS
        phase = CompilePhase.PRINT_IDEAL,
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_byte_incr_aliasing(MemorySegment a, MemorySegment b) {
        for (long i = 0; i < a.byteSize(); i++) {
            byte v = a.get(ValueLayout.JAVA_BYTE, i);
            b.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0",
                  ".*multiversion.*",   "> 0"}, // AutoVectorization Predicate FAILS
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_byte_incr_aliasing_fwd3(MemorySegment a, MemorySegment b) {
        for (long i = 0; i < a.byteSize(); i++) {
            byte v = a.get(ValueLayout.JAVA_BYTE, i);
            b.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0",
                  ".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_byte_incr_noaliasing_fwd128(MemorySegment a, MemorySegment b) {
        for (long i = 0; i < a.byteSize(); i++) {
            byte v = a.get(ValueLayout.JAVA_BYTE, i);
            b.set(ValueLayout.JAVA_BYTE, i, (byte)(v + 1));
        }
    }

    @Test
    @IR(counts = {IRNode.LOAD_VECTOR_I,   IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.VECTOR_CAST_I2L, IRNode.VECTOR_SIZE + "min(max_int, max_long)", "> 0",
                  IRNode.STORE_VECTOR,                                                   "> 0",
                  ".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"avx", "true", "asimd", "true"})
    // In this case, the limit is pre-loop independent, but its assigned
    // ctrl sits between main and pre loop. Only the early ctrl is before
    // the pre loop.
    static void test_int_to_long_noaliasing(MemorySegment a, MemorySegment b) {
        long limit = a.byteSize() / 8L;
        for (long i = 0; i < limit; i++) {
            int v = a.get(ValueLayout.JAVA_INT_UNALIGNED, 4L * i);
            b.set(ValueLayout.JAVA_LONG_UNALIGNED, 8L * i, v);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0",
    //               ".*multiversion.*",   "> 0"}, // AutoVectorization Predicate FAILS
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    static void test_fill_byte_sameMS_alias(MemorySegment a, MemorySegment b, long invar1, long invar2) {
        for (long i = 0; i < a.byteSize() - 100; i++) {
            a.set(ValueLayout.JAVA_BYTE, i + invar1, (byte)0x0a);
            b.set(ValueLayout.JAVA_BYTE, a.byteSize() - i - 1 - invar2, (byte)0x0b);
        }
    }

    @DontCompile
    static void reference_fill_byte_sameMS_alias(MemorySegment a, MemorySegment b, long invar1, long invar2) {
        for (long i = 0; i < a.byteSize() - 100; i++) {
            a.set(ValueLayout.JAVA_BYTE, i + invar1, (byte)0x0a);
            b.set(ValueLayout.JAVA_BYTE, a.byteSize() - i - 1 - invar2, (byte)0x0b);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0",
    //               ".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    //
    // For now, we just assert that there is never multiversioning, which holds with or without vectorization:
    @IR(counts = {".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_fill_byte_sameMS_noalias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i < limit; i++) {
            a.set(ValueLayout.JAVA_BYTE, invar1 + i, (byte)0xa);
            b.set(ValueLayout.JAVA_BYTE, invar2 - i, (byte)0xb);
        }
    }

    @DontCompile
    static void reference_fill_byte_sameMS_noalias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i < limit; i++) {
            a.set(ValueLayout.JAVA_BYTE, invar1 + i, (byte)0xa);
            b.set(ValueLayout.JAVA_BYTE, invar2 - i, (byte)0xb);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0"},
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    //
    // Note: we may or may not use multiversioning, depending if we alias or not at runtime.
    static void test_fill_byte_sameMS_maybeAlias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i < limit; i++) {
            a.set(ValueLayout.JAVA_BYTE, invar1 + i, (byte)0xa);
            b.set(ValueLayout.JAVA_BYTE, invar2 - i, (byte)0xb);
        }
    }

    @DontCompile
    static void reference_fill_byte_sameMS_maybeAlias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i < limit; i++) {
            a.set(ValueLayout.JAVA_BYTE, invar1 + i, (byte)0xa);
            b.set(ValueLayout.JAVA_BYTE, invar2 - i, (byte)0xb);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0",
    //               ".*multiversion.*",   "> 0"}, // AutoVectorization Predicate FAILS
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    static void test_fill_int_sameMS_alias(MemorySegment a, MemorySegment b, long invar1, long invar2) {
        for (long i = 0; i < a.byteSize() - 100; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, i + invar1, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, a.byteSize() - i - 4 - invar2, 0x11121314);
        }
    }

    @DontCompile
    static void reference_fill_int_sameMS_alias(MemorySegment a, MemorySegment b, long invar1, long invar2) {
        for (long i = 0; i < a.byteSize() - 100; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, i + invar1, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, a.byteSize() - i - 4 - invar2, 0x11121314);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0",
    //               ".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    //
    // For now, we just assert that there is never multiversioning, which holds with or without vectorization:
    @IR(counts = {".*multiversion.*",   "= 0"}, // AutoVectorization Predicate SUFFICES
        phase = CompilePhase.PRINT_IDEAL,
        applyIfPlatform = {"64-bit", "true"},
        applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static void test_fill_int_sameMS_noalias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i <= limit - 4; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, invar1 + i, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, invar2 - i, 0x11121314);
        }
    }

    @DontCompile
    static void reference_fill_int_sameMS_noalias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i <= limit - 4; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, invar1 + i, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, invar2 - i, 0x11121314);
        }
    }

    @Test
    // @IR(counts = {IRNode.STORE_VECTOR,  "> 0"},
    //     phase = CompilePhase.PRINT_IDEAL,
    //     applyIfPlatform = {"64-bit", "true"},
    //     applyIfAnd = {"AlignVector", "false", "UseAutoVectorizationSpeculativeAliasingChecks", "true"},
    //     applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    //
    // FAILS: but only on "native" and "byte-buffer-direct"
    //        The issue is that one of the VPointers is invalid.
    //
    // Note: we may or may not use multiversioning, depending if we alias or not at runtime.
    static void test_fill_int_sameMS_maybeAlias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i <= limit - 4; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, invar1 + i, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, invar2 - i, 0x11121314);
        }
    }

    @DontCompile
    static void reference_fill_int_sameMS_maybeAlias(MemorySegment a, MemorySegment b, long invar1, long invar2, long limit) {
        for (long i = 0; i <= limit - 4; i+=4) {
            a.set(ValueLayout.JAVA_INT_UNALIGNED, invar1 + i, 0x01020304);
            b.set(ValueLayout.JAVA_INT_UNALIGNED, invar2 - i, 0x11121314);
        }
    }
}
