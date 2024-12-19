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
 * The {@link Verify} class provides a single {@link Verify#checkEQ} static method, which recursively
 * compares the two {@link Object}s by value. It deconstructs {@link Object[]}, compares boxed primitive
 * types, and compares the content of arrays and {@link MemorySegment}s.
 *
 * When a comparison fail, then methods print helpful messages, before throwing a {@link VerifyException}.
 */
public final class Verify {

    private Verify() {}

    /**
     * Verify the content of two Objects, possibly recursively. Only limited types are implemented.
     *
     * @param a First object to be recursively compared with the second.
     * @param b Second object to be recursively compared with the first.
     * @throws VerifyException If the comparison fails.
     */
    public static void checkEQ(Object a, Object b) {
        checkEQ(a, b, "");
    }

    /**
     * Verify the content of two Objects, possibly recursively. Only limited types are implemented.
     */
    private static void checkEQ(Object a, Object b, String context) {
        // Both null
        if (a == null && b == null) {
            return;
        }

        // Null mismatch
        if (a == null || b == null) {
            System.err.println("ERROR: Verify.checkEQ failed: null mismatch");
            print(a, "a " + context);
            print(b, "b " + context);
            throw new VerifyException("Object array null mismatch.");
        }

        // Class mismatch
        Class ca = a.getClass();
        Class cb = b.getClass();
        if (ca != cb) {
            System.err.println("ERROR: Verify.checkEQ failed: class mismatch.");
            System.err.println("       " + ca.getName() + " vs " + cb.getName());
            print(a, "a " + context);
            print(b, "b " + context);
            throw new VerifyException("Object class mismatch.");
        }

        switch (a) {
            case Object[]  x -> checkEQimpl(x, (Object[])b,                context);
            case Byte      x -> checkEQimpl(x, ((Byte)b).byteValue(),      context);
            case Character x -> checkEQimpl(x, ((Character)b).charValue(), context);
            case Short     x -> checkEQimpl(x, ((Short)b).shortValue(),    context);
            case Integer   x -> checkEQimpl(x, ((Integer)b).intValue(),    context);
            case Long      x -> checkEQimpl(x, ((Long)b).longValue(),      context);
            case Float     x -> checkEQimpl(x, ((Float)b).floatValue(),    context);
            case Double    x -> checkEQimpl(x, ((Double)b).doubleValue(),  context);
            case byte[]    x -> checkEQimpl(x, (byte[])b,                  context);
            case char[]    x -> checkEQimpl(x, (char[])b,                  context);
            case short[]   x -> checkEQimpl(x, (short[])b,                 context);
            case int[]     x -> checkEQimpl(x, (int[])b,                   context);
            case long[]    x -> checkEQimpl(x, (long[])b,                  context);
            case float[]   x -> checkEQimpl(x, (float[])b,                 context);
            case double[]  x -> checkEQimpl(x, (double[])b,                context);
            case MemorySegment x -> checkEQimpl(x, (MemorySegment) b,      context);
            default -> {
                System.err.println("ERROR: Verify.checkEQ failed: type not supported: " + ca.getName());
                print(a, "a " + context);
                print(b, "b " + context);
                throw new VerifyException("Object array type not supported: " + ca.getName());
            }
        }
    }

    /**
     * Verify that two bytes are identical.
     */
    private static void checkEQimpl(byte a, byte b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b + " for " + context);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two chars are identical.
     */
    private static void checkEQimpl(char a, char b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + (int)a + " vs " + (int)b + " for " + context);
            throw new VerifyException("Value mismatch: " + (int)a + " vs " + (int)b);
        }
    }

    /**
     * Verify that two shorts are identical.
     */
    private static void checkEQimpl(short a, short b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + (int)a + " vs " + (int)b + " for " + context);
            throw new VerifyException("Value mismatch: " + (int)a + " vs " + (int)b);
        }
    }

    /**
     * Verify that two ints are identical.
     */
    private static void checkEQimpl(int a, int b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b + " for " + context);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two longs are identical.
     */
    private static void checkEQimpl(long a, long b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b + " for " + context);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two floats have identical bits.
     */
    private static void checkEQimpl(float a, float b, String context) {
        if (Float.floatToRawIntBits(a) != Float.floatToRawIntBits(b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch for " + context);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Values: " + Float.floatToRawIntBits(a) + " vs " + Float.floatToRawIntBits(b));
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two doubles have identical bits.
     */
    private static void checkEQimpl(double a, double b, String context) {
        if (Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch for " + context);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Values: " + Double.doubleToRawLongBits(a) + " vs " + Double.doubleToRawLongBits(b));
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that the content of two MemorySegments is identical. Note: we do not check the
     * backing type, only the size and content.
     */
    private static void checkEQimpl(MemorySegment a, MemorySegment b, String context) {
        long offset = a.mismatch(b);
        if (offset == -1) { return; }

        // Print some general info
        System.err.println("ERROR: Verify.checkEQ failed for: " + context);

        printMemorySegment(a, "a " + context);
        printMemorySegment(b, "b " + context);

        // (1) Mismatch on size
        if (a.byteSize() != b.byteSize()) {
            throw new VerifyException("MemorySegment byteSize mismatch.");
        }

        // (2) Value mismatch
        System.err.println("  Value mismatch at byte offset: " + offset);
        printMemorySegmentValue(a, offset, 16);
        printMemorySegmentValue(b, offset, 16);
        throw new VerifyException("MemorySegment value mismatch.");
    }

    /**
     * Verify that the content of two byte arrays is identical.
     */
    private static void checkEQimpl(byte[] a, byte[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two char arrays is identical.
     */
    private static void checkEQimpl(char[] a, char[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two short arrays is identical.
     */
    private static void checkEQimpl(short[] a, short[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two int arrays is identical.
     */
    private static void checkEQimpl(int[] a, int[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two long arrays is identical.
     */
    private static void checkEQimpl(long[] a, long[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two float arrays is identical.
     */
    private static void checkEQimpl(float[] a, float[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two double arrays is identical.
     */
    private static void checkEQimpl(double[] a, double[] b, String context) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), context);
    }

    /**
     * Verify that the content of two Object arrays is identical, recursively:
     * every element is compared with checkEQimpl for the corresponding type.
     */
    private static void checkEQimpl(Object[] a, Object[] b, String context) {
        // (1) Length mismatch
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length);
            throw new VerifyException("Object array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            // Recursive checkEQ call.
            checkEQ(a[i], b[i], "[" + i + "]" + context);
        }
    }

    private static void print(Object a, String context) {
        if (a == null) {
            System.err.println("  " + context + ": null");
        } else {
            System.err.println("  " + context + ": " + a);
        }
    }

    private static void printMemorySegment(MemorySegment a, String context) {
        Optional<Object> maybeBase = a.heapBase();
        System.err.println("  " + context + " via MemorySegment:");
        if (maybeBase.isEmpty()) {
            System.err.println("    no heap base (native).");
        } else {
            Object base = maybeBase.get();
            System.err.println("    heap base: " + base);
        }
        System.err.println("    address: " + a.address());
        System.err.println("    byteSize: " + a.byteSize());
    }

    private static void printMemorySegmentValue(MemorySegment a, long offset, int range) {
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
