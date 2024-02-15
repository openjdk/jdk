/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8257531 8310190
 * @summary Test vectorization for Buffer operations.
 * @library /test/lib /
 * @requires vm.compiler2.enabled
 * @run driver compiler.vectorization.TestBufferVectorization
 */

package compiler.vectorization;

import compiler.lib.ir_framework.*;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

public class TestBufferVectorization {
    final static int N = 1024*16;
    static int offset = 0;
    final static VarHandle VH_arr_view = MethodHandles.byteArrayViewVarHandle(int[].class, ByteOrder.nativeOrder()).withInvokeExactBehavior();

    public static void main(String[] args) {
        TestFramework.run();
    }

    @Run(test = "testArray")
    public static void runArray() {
        int[] array = new int[N];

        for (int k = 0; k < array.length; k++) {
            array[k] = k;
        }

        testArray(array);

        for(int k = 0; k < array.length; k++) {
            if (array[k] != (k + 1)) {
                throw new RuntimeException(" Invalid result: array[" + k + "]: " + array[k] + " != " + (k + 1));
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,   ">0",
                  IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_VI,        ">0",
                  IRNode.STORE_VECTOR,  ">0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public static void testArray(int[] array) {
        for(int k = 0; k < array.length; k++) {
            array[k] += 1;
        }
    }

    @Run(test = "testArrayOffset")
    public static void runArrayOffset() {
        // Moving offset between 0..255
        offset = (offset + 1) % 256;

        int[] array = new int[N];

        for (int k = 0; k < array.length; k++) {
            array[k] = k;
        }

        testArrayOffset(array, offset);

        int l = array.length - offset;
        for(int k = 0; k < offset; k++) {
            if (array[k] != k) {
                throw new RuntimeException(" Invalid result: arrayOffset[" + k + "]: " + array[k] + " != " + (k + 1));
            }
        }
        for(int k = offset; k < array.length; k++) {
            if (array[k] != (k + 1)) {
                throw new RuntimeException(" Invalid result: arrayOffset[" + k + "]: " + array[k] + " != " + k);
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,   ">0",
                  IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_VI,        ">0",
                  IRNode.STORE_VECTOR,  ">0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public static void testArrayOffset(int[] array, int offset) {
        int l = array.length - offset;
        for(int k = 0; k < l; k++) {
            array[k + offset] += 1;
        }
    }

    @Run(test = "testBuffer")
    public static void runBuffer() {
        IntBuffer buffer = IntBuffer.allocate(N);
        initBuffer(buffer);
        testBuffer(buffer);
        verifyBuffer(buffer);
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,   ">0",
                  IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_VI,        ">0",
                  IRNode.STORE_VECTOR,  ">0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public static void testBuffer(IntBuffer buffer) {
        for (int k = 0; k < buffer.limit(); k++) {
            buffer.put(k, buffer.get(k) + 1);
        }
    }

    @Run(test = "testBufferHeap")
    public static void runBufferHeap() {
        IntBuffer buffer = ByteBuffer.allocate(N * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
        initBuffer(buffer);
        testBufferHeap(buffer);
        verifyBuffer(buffer);
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,   IRNode.VECTOR_SIZE_ANY, ">0",
                  IRNode.LOAD_VECTOR_I, IRNode.VECTOR_SIZE_ANY, ">0",
                  IRNode.ADD_VI,        IRNode.VECTOR_SIZE_ANY, ">0",
                  IRNode.STORE_VECTOR,                          ">0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"},
        applyIf = {"AlignVector", "false"},
        applyIfPlatform = {"64-bit", "true"})
    // VECTOR_SIZE_ANY: Unrolling does not always seem to go far enough to reach maximum vector size.
    //                  This looks like a BUG.
    // AlignVector: Buffer get/put have an invariant that is in bytes (LoadL in ByteBufferAsIntBufferL::byteOffset).
    //              This makes sense: we are accessing a byte buffer. But to be able to align the 4 byte ints,
    //              we would require to know that the invariant is a multiple of 4. Without that, we cannot
    //              guarantee alignment by adjusting the limit of the pre-loop with a stride of 4 bytes.
    // 64-bit: bufferHeap uses Long type for memory accesses which are not vectorized in 32-bit VM
    public static void testBufferHeap(IntBuffer buffer) {
        for (int k = 0; k < buffer.limit(); k++) {
            buffer.put(k, buffer.get(k) + 1);
        }
    }

    @Run(test = "testBufferDirect")
    public static void runBufferDirect() {
        IntBuffer buffer = ByteBuffer.allocateDirect(N * Integer.BYTES).order(ByteOrder.nativeOrder()).asIntBuffer();
        initBuffer(buffer);
        testBufferDirect(buffer);
        verifyBuffer(buffer);
    }

    @Test
    // bufferDirect uses Unsafe memory accesses which are not vectorized currently
    // We find a CastX2P in pointer analysis (VPointer)
    public static void testBufferDirect(IntBuffer buffer) {
        for (int k = 0; k < buffer.limit(); k++) {
            buffer.put(k, buffer.get(k) + 1);
        }
    }

    public static void initBuffer(IntBuffer buffer) {
        for (int k = 0; k < buffer.limit(); k++) {
            buffer.put(k, k);
        }
    }

    public static void verifyBuffer(IntBuffer buffer) {
        for(int k = 0; k < buffer.limit(); k++) {
            if (buffer.get(k) != (k + 1)) {
                throw new RuntimeException(" Invalid result: buffer.get(" + k + "): " + buffer.get(k) + " != " + (k + 1));
            }
        }
    }

    @Run(test = "testArrayView")
    public static void runArrayView() {
        byte[] b_arr = new byte[N * Integer.BYTES];

        for (int k = 0; k < N; k++) {
            VH_arr_view.set(b_arr, k, k);
        }

        // Save initial INT values
        int[] i_arr = new int[N];
        for (int k = 0; k < i_arr.length; k++) {
            i_arr[k] = (int) VH_arr_view.get(b_arr, k * Integer.BYTES);
        }
        testArrayView(b_arr);

        for (int k = 0; k < i_arr.length; k++) {
            int v = (int) VH_arr_view.get(b_arr, k * Integer.BYTES);
            if (v != (i_arr[k] + 1)) {
                throw new RuntimeException(" Invalid result: VH_arr_view.get(b_arr, " + (k * Integer.BYTES) + "): " + v + " != " + (i_arr[k] + 1));
            }
        }
    }

    @Test
    @IR(counts = {IRNode.REPLICATE_I,   ">0",
                  IRNode.LOAD_VECTOR_I, ">0",
                  IRNode.ADD_VI,        ">0",
                  IRNode.STORE_VECTOR,  ">0"},
        applyIfCPUFeatureOr = {"sse4.1", "true", "asimd", "true"})
    public static void testArrayView(byte[] b_arr) {
        for (int k = 0; k < b_arr.length; k += 4) {
            int v = (int) VH_arr_view.get(b_arr, k);
            VH_arr_view.set(b_arr, k, v + 1);
        }
    }
}
