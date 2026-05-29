/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 * @bug 8380841
 * @summary Test AVX2 SIMD binary search intrinsic correctness and IR shape
 * @library /test/lib /
 * @run driver compiler.intrinsics.binarySearch.TestBinarySearchIntrinsic
 */

package compiler.intrinsics.binarySearch;

import java.util.Arrays;
import jdk.test.lib.Asserts;
import compiler.lib.ir_framework.*;

public class TestBinarySearchIntrinsic {

    // Large arrays to exercise N-ary loop
    static char[]  ca = new char[3000];
    static short[] sa = new short[3000];
    static int[]   ia = new int[3000];
    static long[]  la = new long[3000];

    // Negative-value large arrays for signed comparison coverage in N-ary loop
    static short[] sa_neg = new short[3000];
    static int[]   ia_neg = new int[3000];
    static long[]  la_neg = new long[3000];

    // Arrays at the C2 intrinsic entry thresholds
    static int[]   ia_threshold = new int[512];
    static long[]  la_threshold = new long[768];

    // High char values (> 0x7FFF) to exercise unsigned-as-signed comparison
    static char[]  ca_high = new char[3000];

    // Small arrays that go directly to per-type scalar tail
    static char[]  ca_small = {Character.MIN_VALUE, 'A', '\u0100', '\u8000', '\uFFFE', Character.MAX_VALUE};
    static short[] sa_small = {Short.MIN_VALUE, -1, 0, 1, 42, Short.MAX_VALUE};
    static int[]   ia_small = {Integer.MIN_VALUE, -1, 0, 1, 42, Integer.MAX_VALUE};
    static long[]  la_small = {Long.MIN_VALUE, -1L, 0L, 1L, 42L, Long.MAX_VALUE};

    static long[]  la_tiny = {10L, 20L, 30L};
    static int[]   ia_dup = {1, 2, 2, 2, 3, 4, 5};

    static {
        for (int i = 0; i < ca.length; i++) ca[i] = (char)(i * 3);
        for (int i = 0; i < sa.length; i++) sa[i] = (short)(i * 5);
        for (int i = 0; i < ia.length; i++) ia[i] = i * 7;
        for (int i = 0; i < la.length; i++) la[i] = i * 9;

        for (int i = 0; i < sa_neg.length; i++) sa_neg[i] = (short)(i - 1500);
        for (int i = 0; i < ia_neg.length; i++) ia_neg[i] = i * 7 + Integer.MIN_VALUE;
        for (int i = 0; i < la_neg.length; i++) la_neg[i] = i * 9 + Long.MIN_VALUE;

        for (int i = 0; i < ia_threshold.length; i++) ia_threshold[i] = i * 3;
        for (int i = 0; i < la_threshold.length; i++) la_threshold[i] = i * 5;

        for (int i = 0; i < ca_high.length; i++) ca_high[i] = (char)(0x8000 + i);
    }

    public static void main(String[] args) {
        TestFramework framework = new TestFramework();
        framework.addScenarios(
            new Scenario(0, "-XX:-UseAVX2BinarySearchIntrinsic"),
            new Scenario(1, "-XX:+UseAVX2BinarySearchIntrinsic")
        );
        framework.start();
    }

    static final String STUB_CALL = ".*CallLeafNoFP.*array_binary_search.*";

    // -----------------------------------------------------------------------
    // Correctness tests, no IR verification
    // -----------------------------------------------------------------------

    @Test
    static void testSmallArrays() {
        Asserts.assertEquals(Arrays.binarySearch(ca_small, '\u8000'), 3);
        Asserts.assertEquals(Arrays.binarySearch(sa_small, (short) 42), 4);
        Asserts.assertEquals(Arrays.binarySearch(ia_small, 42), 4);
        Asserts.assertEquals(Arrays.binarySearch(la_small, 42L), 4);
        Asserts.assertEquals(Arrays.binarySearch(ia_small, 2), -(4 + 1));
        Asserts.assertEquals(Arrays.binarySearch(new int[]{}, 42), -1);
    }

    @Test
    static void testLongTinyArrays() {
        Asserts.assertEquals(Arrays.binarySearch(la_tiny, 20L), 1);
        Asserts.assertEquals(Arrays.binarySearch(la_tiny, 15L), -(1 + 1));
    }

    @Test
    static void testFirstLastElement() {
        Asserts.assertEquals(Arrays.binarySearch(ia, ia[0]), 0);
        Asserts.assertEquals(Arrays.binarySearch(ia, ia[ia.length - 1]), ia.length - 1);
        Asserts.assertEquals(Arrays.binarySearch(la, la[0]), 0);
        Asserts.assertEquals(Arrays.binarySearch(la, la[la.length - 1]), la.length - 1);
    }

    @Test
    static void testNegativeValues() {
        Asserts.assertEquals(Arrays.binarySearch(sa_neg, sa_neg[750]), 750);
        Asserts.assertEquals(Arrays.binarySearch(ia_neg, ia_neg[1500]), 1500);
        Asserts.assertEquals(Arrays.binarySearch(la_neg, la_neg[1500]), 1500);
    }

    @Test
    static void testNonZeroFromIndex() {
        Asserts.assertEquals(Arrays.binarySearch(ia, 500, 2500, ia[1500]), 1500);
        Asserts.assertEquals(Arrays.binarySearch(la, 500, 2500, la[1500]), 1500);
    }

    @Test
    static void testThresholdBoundary() {
        Asserts.assertEquals(Arrays.binarySearch(ia_threshold, ia_threshold[256]), 256);
        Asserts.assertEquals(Arrays.binarySearch(ia_threshold, ia_threshold[256] + 1), -(257 + 1));
        Asserts.assertEquals(Arrays.binarySearch(la_threshold, la_threshold[384]), 384);
        Asserts.assertEquals(Arrays.binarySearch(la_threshold, la_threshold[384] + 1), -(385 + 1));
    }

    @Test
    static void testDuplicateKeys() {
        int r = Arrays.binarySearch(ia_dup, 2);
        Asserts.assertTrue(r >= 1 && r <= 3, "expected index 1..3, got " + r);
        Asserts.assertEquals(ia_dup[r], 2);
    }

    @Test
    static void testCharAbove7FFF() {
        Asserts.assertEquals(Arrays.binarySearch(ca_high, ca_high[1500]), 1500);
        Asserts.assertEquals(Arrays.binarySearch(ca_high, '\uFFFF'), -(ca_high.length + 1));
    }

    // -----------------------------------------------------------------------
    // IR-verified tests (one per type x found/missing)
    // -----------------------------------------------------------------------

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testCharFound() {
        return Arrays.binarySearch(ca, ca[1500]);
    }
    @Check(test = "testCharFound")
    static void checkCharFound(int r) { Asserts.assertEquals(r, 1500); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testCharMissing() {
        return Arrays.binarySearch(ca, 0, 1500, ca[1500]);
    }
    @Check(test = "testCharMissing")
    static void checkCharMissing(int r) { Asserts.assertEquals(r, -(1500 + 1)); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testShortFound() {
        return Arrays.binarySearch(sa, sa[1500]);
    }
    @Check(test = "testShortFound")
    static void checkShortFound(int r) { Asserts.assertEquals(r, 1500); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testShortMissing() {
        return Arrays.binarySearch(sa, 0, 1500, sa[1500]);
    }
    @Check(test = "testShortMissing")
    static void checkShortMissing(int r) { Asserts.assertEquals(r, -(1500 + 1)); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testIntFound() {
        return Arrays.binarySearch(ia, ia[1500]);
    }
    @Check(test = "testIntFound")
    static void checkIntFound(int r) { Asserts.assertEquals(r, 1500); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testIntMissing() {
        return Arrays.binarySearch(ia, ia[1500] + 1);
    }
    @Check(test = "testIntMissing")
    static void checkIntMissing(int r) { Asserts.assertEquals(r, -(1501 + 1)); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testLongFound() {
        return Arrays.binarySearch(la, la[1500]);
    }
    @Check(test = "testLongFound")
    static void checkLongFound(int r) { Asserts.assertEquals(r, 1500); }

    @Test
    @IR(counts = {STUB_CALL, "1"}, phase = CompilePhase.BEFORE_MATCHING,
        applyIfCPUFeature = {"avx2", "true"},
        applyIf = {"UseAVX2BinarySearchIntrinsic", "true"})
    @IR(failOn = {STUB_CALL}, phase = CompilePhase.BEFORE_MATCHING,
        applyIf = {"UseAVX2BinarySearchIntrinsic", "false"})
    static int testLongMissing() {
        return Arrays.binarySearch(la, la[1500] + 1);
    }
    @Check(test = "testLongMissing")
    static void checkLongMissing(int r) { Asserts.assertEquals(r, -(1501 + 1)); }
}
