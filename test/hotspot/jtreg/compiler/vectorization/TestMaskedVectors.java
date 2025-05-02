/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8317121
 * @summary Test masked vectors and unsafe access to memory modified by arraycopy
 * @requires vm.compiler2.enabled
 * @modules java.base/jdk.internal.misc
 * @library /test/lib /
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xbatch -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,TestMaskedVectors::test* -XX:+StressLCM -XX:+StressGCM -XX:StressSeed=2210259638 TestMaskedVectors
 * @run main/othervm -XX:+IgnoreUnrecognizedVMOptions -XX:+UnlockDiagnosticVMOptions -XX:-TieredCompilation -Xbatch -XX:CompileCommand=quiet -XX:CompileCommand=compileonly,TestMaskedVectors::test* -XX:+StressLCM -XX:+StressGCM TestMaskedVectors
 */

import java.lang.reflect.*;
import java.util.*;

import jdk.internal.misc.Unsafe;

public class TestMaskedVectors {

    private static Unsafe UNSAFE = Unsafe.getUnsafe();
    private static final long BASE_OFFSET = UNSAFE.arrayBaseOffset(byte[].class);

    static void testLoadVectorMasked(byte[] src, byte[] dst, int len) {
        byte[] tmp = new byte[64];

        // (3) The LoadVectorMasked is found to be dependent on below arraycopy and
        // therefore scheduled just below it. As a result, the LoadVectorMasked misses the
        // updated elements at index 16..48 and dst will contain incorrect values.
        System.arraycopy(src, 0, tmp, 0, 16);

        // (2) The LoadVectorMasked is incorrectly found to be independent of this arraycopy
        // because the LoadVectorMasked has offset 0 whereas the arraycopy writes offset >= 16.
        // The problem is that MemNode::find_previous_store() -> LoadNode::find_previous_arraycopy()
        // -> ArrayCopyNode::modifies does not account for the size of the load.
        System.arraycopy(src, 0, tmp, 16, 48);

        // (1) The following arraycopy is expanded into a LoadVectorMasked and a
        // StoreVectorMasked in PhaseMacroExpand::generate_partial_inlining_block().
        System.arraycopy(tmp, 0, dst, 0, len);
    }

    static long testUnsafeGetLong(byte[] src) {
        byte[] tmp = new byte[16];

        // (3) The unsafe load is found to be dependent on below arraycopy and
        // therefore scheduled just below it. As a result, the unsafe load misses the
        // updated elements at index 1..16 and therefore returns an incorrect result.
        System.arraycopy(src, 0, tmp, 0, 16);

        // (2) The unsafe load is incorrectly found to be independent of this arraycopy
        // because the load has offset 0 in 'tmp' whereas the arraycopy writes offsets >= 1.
        // The problem is that MemNode::find_previous_store() -> LoadNode::find_previous_arraycopy()
        // -> ArrayCopyNode::modifies does not account for the size of the load.
        System.arraycopy(src, 0, tmp, 1, 15);

        // (1) Below unsafe load reads the first 8 (byte) array elements.
        return UNSAFE.getLong(tmp, BASE_OFFSET);
    }

    public static void main(String[] args) {
        // Initialize src array with increasing byte values
        byte[] src = new byte[64];
        for (byte i = 0; i < src.length; ++i) {
            src[i] = (byte)i;
        }

        // Compute expected outputs once
        byte[] golden1 = new byte[64];
        testLoadVectorMasked(src, golden1, 64);

        long golden2 = testUnsafeGetLong(src);

        // Trigger compilation of test methods and verify the results
        for (int i = 0; i < 50_000; ++i) {
            int len = i % 32;
            byte[] dst = new byte[len];
            testLoadVectorMasked(src, dst, len);

            boolean error = false;
            for (int j = 0; j < dst.length; ++j) {
                if (dst[j] != golden1[j]) {
                    System.out.println("Incorrect value of element " + j + ": Expected " + golden1[j] + " but got " + dst[j]);
                    error = true;
                }
            }
            if (error) {
                throw new RuntimeException("Test LoadVectorMasked failed");
            }

            long res = testUnsafeGetLong(src);
            if (res != golden2) {
                throw new RuntimeException("Incorrect result in test UnsafeGetLong: Expected " + golden2 + " but got " + res);
            }
        }
    }
}
