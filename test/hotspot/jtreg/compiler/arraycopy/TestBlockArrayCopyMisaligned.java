/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8380060
 * @summary C2 block arraycopy initial-word pick-off folds load to zero
 *
 * @run main/othervm -Xbatch -XX:+UseCompactObjectHeaders
 *                   -XX:CompileCommand=compileonly,compiler.arraycopy.TestBlockArrayCopyMisaligned::test*
 *                   compiler.arraycopy.TestBlockArrayCopyMisaligned
 * @run main/othervm -Xbatch -XX:-UseCompactObjectHeaders
 *                   -XX:CompileCommand=compileonly,compiler.arraycopy.TestBlockArrayCopyMisaligned::test*
 *                   compiler.arraycopy.TestBlockArrayCopyMisaligned
 */

package compiler.arraycopy;

import java.util.Arrays;

public class TestBlockArrayCopyMisaligned {

    // Byte array with enough data to exercise the block arraycopy path.
    // The clone + substring pattern triggers a tightly-coupled allocation
    // where the source is initialized via a raw arraycopy (clone) and the
    // destination is a nozero allocation filled by a block arraycopy.
    static final byte[] BYTES = new byte[] {
            108, 77, 120, 120, 100, 77, 105, 113,
             83, 97,  71, 108, 116, 107,  90,  71,
             72, 107,  73,  85,  54,  65,  61,  61
    };

    // Test via String(byte[],hibyte,off,count) + substring.
    // The deprecated String constructor clones the byte array via
    // Arrays.copyOfRange; substring then does another copyOfRange
    // on the clone, triggering the block arraycopy optimization.
    static String testStringSubstring() {
        String s = new String(BYTES, 0, 0, 24);
        return s.substring(0, 23);
    }

    // Test via explicit clone + Arrays.copyOfRange.
    static byte[] testCloneCopyOfRange() {
        byte[] clone = BYTES.clone();
        return Arrays.copyOfRange(clone, 0, 23);
    }

    // Test via explicit clone + Arrays.copyOf.
    static byte[] testCloneCopyOf() {
        byte[] clone = BYTES.clone();
        return Arrays.copyOf(clone, 20);
    }

    public static void main(String[] args) {
        String golden = new String(BYTES, 0, 0, 23);

        for (int i = 0; i < 10_000; i++) {
            // Test 1: String path
            String result1 = testStringSubstring();
            if (!result1.equals(golden)) {
                throw new RuntimeException("testStringSubstring: expected "
                    + golden + ", got " + result1);
            }

            // Test 2: clone + copyOfRange
            byte[] result2 = testCloneCopyOfRange();
            for (int j = 0; j < 23; j++) {
                if (result2[j] != BYTES[j]) {
                    throw new RuntimeException("testCloneCopyOfRange: mismatch at index " + j
                        + ", expected " + BYTES[j] + ", got " + result2[j]);
                }
            }

            // Test 3: clone + copyOf
            byte[] result3 = testCloneCopyOf();
            for (int j = 0; j < 20; j++) {
                if (result3[j] != BYTES[j]) {
                    throw new RuntimeException("testCloneCopyOf: mismatch at index " + j
                        + ", expected " + BYTES[j] + ", got " + result3[j]);
                }
            }
        }
    }
}
