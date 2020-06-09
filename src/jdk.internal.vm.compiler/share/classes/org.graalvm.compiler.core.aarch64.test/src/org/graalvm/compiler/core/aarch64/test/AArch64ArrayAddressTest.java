/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019, 2020, Arm Limited. All rights reserved.
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


package org.graalvm.compiler.core.aarch64.test;

import org.graalvm.compiler.api.directives.GraalDirectives;
import org.graalvm.compiler.lir.LIRInstruction;
import org.graalvm.compiler.lir.aarch64.AArch64ArithmeticOp.ExtendedAddSubShiftOp;
import org.junit.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class AArch64ArrayAddressTest extends AArch64MatchRuleTest {
    private static final Predicate<LIRInstruction> predicate = op -> (op instanceof ExtendedAddSubShiftOp);

    public static byte loadByte(byte[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadByte() {
        byte[] arr = {3, 4, 5, 6, 7, 8};
        test("loadByte", arr, 5);
        checkLIR("loadByte", predicate, 1, 1);
    }

    public static char loadChar(char[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadChar() {
        char[] arr = {'a', 'b', 'c', 'd', 'e', 'f', 'g'};
        test("loadChar", arr, 5);
        checkLIR("loadChar", predicate, 1, 1);
    }

    public static short loadShort(short[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadShort() {
        short[] arr = {3, 4, 5, 6, 7, 8};
        test("loadShort", arr, 5);
        checkLIR("loadShort", predicate, 1, 1);
    }

    public static int loadInt(int[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadInt() {
        int[] arr = {3, 4, 5, 6, 7, 8};
        test("loadInt", arr, 5);
        checkLIR("loadInt", predicate, 1, 1);
    }

    public static long loadLong(long[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadLong() {
        long[] arr = {3L, 4L, 5L, 6L, 7L, 8L};
        test("loadLong", arr, 5);
        checkLIR("loadLong", predicate, 1, 1);
    }

    public static float loadFloat(float[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadFloat() {
        float[] arr = {3.0F, 4.0F, 5.0F, 6.0F, 7.0F, 8.0F};
        test("loadFloat", arr, 5);
        checkLIR("loadFloat", predicate, 1, 1);
    }

    public static double loadDouble(double[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadDouble() {
        double[] arr = {3.0, 4.0, 5.0, 6.0, 7.0, 8.0};
        test("loadDouble", arr, 5);
        checkLIR("loadDouble", predicate, 1, 1);
    }

    public static String loadObject(String[] arr, int n) {
        return arr[n];
    }

    @Test
    public void testLoadObject() {
        String[] arr = {"ac", "ad", "ew", "asf", "sdad", "aff"};
        test("loadObject", arr, 5);
        checkLIRforAll("loadObject", predicate, 1);
    }

    public static int storeInt(int[] arr, int n) {
        arr[n] = n * n;
        return arr[n];
    }

    @Test
    public void testStoreInt() {
        int[] arr = {3, 4, 5, 6, 7, 8};
        test("storeInt", arr, 5);
        checkLIRforAll("storeInt", predicate, 1);
    }

    public static Integer loadAndStoreObject(Integer[] arr, int i) {
        if (arr[i] > 0) {
            return 0;
        }
        arr[i] += 3;
        return arr[i];
    }

    @Test
    public void testLoadAndStoreObject() {
        Integer[] arr = new Integer[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        test("loadAndStoreObject", arr, 2);
        checkLIRforAll("loadAndStoreObject", predicate, 2);
    }

    public static int useArrayInLoop(int[] arr) {
        int ret = 0;
        for (int i = 0; i < arr.length; i++) {
            ret += GraalDirectives.opaque(arr[i]);
        }
        return ret;
    }

    @Test
    public void testUseArrayInLoop() {
        int[] arr = {1, 2, 3, 4, 5, 6, 7, 8};
        test("useArrayInLoop", arr);
        checkLIRforAll("useArrayInLoop", predicate, 1);
    }

    public static int useArrayDeque(ArrayDeque<Integer> ad) {
        ad.addFirst(4);
        return ad.removeFirst();
    }

    @Test
    public void testUseArrayDeque() {
        ArrayDeque<Integer> ad = new ArrayDeque<>();
        test("useArrayDeque", ad);
    }

    // Array load test when the index is narrowed firstly.
    private static class Frame {
        int index;

        Frame(int index) {
            this.index = index;
        }
    }

    private static final Frame[] frameCache = new Frame[256];

    private static Frame newFrame(byte data) {
        return frameCache[data & 255];
    }

    public static int getFrameIndex(int n) {
        return newFrame((byte) n).index;
    }

    @Test
    public void testGetFrameIndex() {
        for (int i = 0; i < 256; i++) {
            frameCache[i] = new Frame(i * i);
        }
        test("getFrameIndex", 258);
        checkLIRforAll("getFrameIndex", predicate, 1);
    }

    static Set<Long> allBarcodes = new HashSet<>();
    static Set<Long> localBarcodes = new HashSet<>();

    public static long useConstReferenceAsBase(long l) {
        localBarcodes.add(l);
        allBarcodes.add(l);
        return l;
    }

    @Test
    public void testUseConstReferenceAsBase() {
        test("useConstReferenceAsBase", 2L);
        int l = localBarcodes.size() + allBarcodes.size();
        test("useConstReferenceAsBase", (long) l);
    }
}
