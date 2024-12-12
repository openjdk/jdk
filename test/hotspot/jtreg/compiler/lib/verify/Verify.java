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

package compiler.lib.verify;

import java.util.Optional;
import java.lang.foreign.*;

/**
 * The Verify class provides a set of verification utility methods, which compare
 * single values, arrays, and some recursive data structures.
 *
 * Should a comparison fail, the methods print helpful messages, before throwing
 * a VerifyException.
 */
public final class Verify {

    /**
     * Verify that the content of two MemorySegments is identical.
     */
    public static void checkEQ(MemorySegment a, MemorySegment b) {
        long offset = a.mismatch(b);
        if (offset == -1) { return; }

        // Print some general info
        System.err.println("ERROR: Verify.checkEQ failed.");

        print(a, "a");
        print(b, "b");

        // (1) Mismatch on size
        if (a.byteSize() != b.byteSize()) {
            throw new VerifyException("MemorySegment byteSize mismatch.");
        }

        // (2) Value mismatch
        System.err.println("  Value mismatch at byte offset: " + offset);
        printValue(a, offset, 16);
        printValue(b, offset, 16);
        throw new VerifyException("MemorySegment value mismatch.");
    }

    public static void checkEQ(byte[] a, byte[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(char[] a, char[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(short[] a, short[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(int[] a, int[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(long[] a, long[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(float[] a, float[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    public static void checkEQ(double[] a, double[] b) {
        checkEQ(MemorySegment.ofArray(a), MemorySegment.ofArray(b));
    }

    private static void print(MemorySegment a, String name) {
        Optional<Object> maybeBase = a.heapBase();
        System.err.println("  MemorySegment " + name + ":");
        if (maybeBase.isEmpty()) {
            System.err.println("    no heap base.");
        } else {
            Object base = maybeBase.get();
            System.err.println("    heap base: " + base);
        }
        System.err.println("    address: " + a.address());
        System.err.println("    byteSize: " + a.byteSize());
    }

    private static void printValue(MemorySegment a, long offset, int range) {
        long start = Long.max(offset - range, 0);
        long end   = Long.min(offset + range, a.byteSize());
        for (long i = start; i < end; i++) {
            byte b = a.get(ValueLayout.JAVA_BYTE, i);
            System.err.print(String.format("%02x ", b));
        }
        System.err.println("");
        for (long i = start; i < end; i++) {
            if (i == offset) {
                System.err.print("^^ ");
            } else {
                System.err.print("   ");
            }
        }
        System.err.println("");
    }
}
