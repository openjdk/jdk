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

package compiler.loopopts.superword;

import compiler.lib.ir_framework.*;
import jdk.test.lib.Utils;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.HashMap;
import java.util.Random;
import java.lang.foreign.*;

/*
 * @test id=byte-buffer-direct
 * @bug 8323582
 * @summary Test vectorization of loops over MemorySegment, with native memory where the address is not always aligned.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentUnalignedAddress ByteBufferDirect
 */

/*
 * @test id=native
 * @bug 8323582
 * @summary Test vectorization of loops over MemorySegment, with native memory where the address is not always aligned.
 * @library /test/lib /
 * @run driver compiler.loopopts.superword.TestMemorySegmentUnalignedAddress Native
 */

public class TestMemorySegmentUnalignedAddress {
    public static void main(String[] args) {
        TestFramework framework = new TestFramework(TestMemorySegmentUnalignedAddressImpl.class);
        framework.addFlags("-DmemorySegmentProviderNameForTestVM=" + args[0]);
        framework.setDefaultWarmup(100);
        framework.start();
    }
}

class TestMemorySegmentUnalignedAddressImpl {
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
            case "ByteBufferDirect" -> TestMemorySegmentUnalignedAddressImpl::newMemorySegmentOfByteBufferDirect;
            case "Native"           -> TestMemorySegmentUnalignedAddressImpl::newMemorySegmentOfNative;
            default -> throw new RuntimeException("Test argument not recognized: " + providerName);
        };
    }

    // List of tests
    Map<String, TestFunction> tests = new HashMap<>();

    // List of gold, the results from the first run before compilation
    Map<String, Object[]> golds = new HashMap<>();

    public TestMemorySegmentUnalignedAddressImpl () {
        // Generate two MemorySegments as inputs
        MemorySegment a = newMemorySegment();
        MemorySegment b = newMemorySegment();
        fillRandom(a);
        fillRandom(b);

        // Add all tests to list
        // TODO add the real tests
        tests.put("testIntLoop_iv_byte",                           () -> testIntLoop_iv_byte(copy(a)));

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

    static MemorySegment newMemorySegmentOfByteBufferDirect() {
        return MemorySegment.ofBuffer(ByteBuffer.allocateDirect(BACKING_SIZE));
    }

    static MemorySegment newMemorySegmentOfNative() {
        // Auto arena: GC decides when there is no reference to the MemorySegment,
        // and then it deallocates the backing memory.
        return Arena.ofAuto().allocate(BACKING_SIZE, 1);
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

    @Run(test = {"testIntLoop_iv_byte"})
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
    @IR(counts = {IRNode.LOAD_VECTOR_B, "> 0",
                  IRNode.ADD_VB,        "> 0",
                  IRNode.STORE_VECTOR,  "> 0"},
        applyIfPlatform = {"64-bit", "true"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    static Object[] testIntLoop_iv_byte(MemorySegment a) {
        for (int i = 0; i < (int)a.byteSize(); i++) {
            long adr = i;
            byte v = a.get(ValueLayout.JAVA_BYTE, adr);
            a.set(ValueLayout.JAVA_BYTE, adr, (byte)(v + 1));
        }
        return new Object[]{ a };
    }
}
