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
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.ArrayList;


/**
 * TODO: update description
 * The {@link Verify} class provides a single {@link Verify#checkEQ} static method, which recursively
 * compares the two {@link Object}s by value. It deconstructs {@link Object[]}, compares boxed primitive
 * types, compares the content of arrays and {@link MemorySegment}s, and checks that the messages of two
 * {@link Exception}s are equal. We also check for equivalent content in {@link Vector}s from the Vector
 * API.
 *
 * TODO: mention that MemorySegment is always checked raw, also for floats.
 *
 * When a comparison fail, then methods print helpful messages, before throwing a {@link VerifyException}.
 */
public final class Verify {

    // TODO: fields for float exactness, maps, etc.
    private final boolean isFloatCheckWithRawBits;
    private final boolean isCheckWithArbitraryClasses;
    private final HashMap<Object, Object> a2b = new HashMap<>();
    private final HashMap<Object, Integer> a2id = new HashMap<>();
    private final ArrayList<Object> id2a = new ArrayList<>(); // TODO: remove?

    private Verify(boolean isFloatCheckWithRawBits, boolean isCheckWithArbitraryClasses) {
        this.isFloatCheckWithRawBits = isFloatCheckWithRawBits;
        this.isCheckWithArbitraryClasses = isCheckWithArbitraryClasses;
    }

    // TODO: desc
    public static void checkEQ(Object a, Object b, boolean isFloatCheckWithRawBits, boolean isCheckWithArbitraryClasses) {
        Verify v = new Verify(isFloatCheckWithRawBits, isCheckWithArbitraryClasses);
        v.checkEQdispatch(a, b, "<root>", null, null);
    }

    // recursive, so that we have nicer stack trace? - no need for map!
    // queue: allows deeper structures
    // We need to think about "edges": (a, b) -field-> (c, d)

    /**
     * Verify the content of two Objects, possibly recursively. Only limited types are implemented.
     *
     * @param a First object to be recursively compared with the second.
     * @param b Second object to be recursively compared with the first.
     * @throws VerifyException If the comparison fails.
     */
    public static void checkEQ(Object a, Object b) {
        checkEQ(a, b, false, false);
    }

    private void checkEQdispatch(Object a, Object b, String field, Object aParent, Object bParent) {
        // Both null
        if (a == null && b == null) {
            return;
        }

        // Null mismatch
        if (a == null || b == null) {
            System.err.println("ERROR: Verify.checkEQ failed: null mismatch");
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Object array null mismatch.");
        }

        // Class mismatch
        Class ca = a.getClass();
        Class cb = b.getClass();
        if (ca != cb) {
            System.err.println("ERROR: Verify.checkEQ failed: class mismatch.");
            System.err.println("       " + ca.getName() + " vs " + cb.getName());
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Object class mismatch.");
        }

        // Already visited?
        if (checkAlreadyVisited(a, b, field, aParent, bParent)) {
            return;
        }

        switch (a) {
            case Object[]  x -> checkEQimpl(x, (Object[])b,                 field, aParent, bParent);
            case Byte      x -> checkEQimpl(x, ((Byte)b).byteValue(),       field, aParent, bParent);
            case Character x -> checkEQimpl(x, ((Character)b).charValue(),  field, aParent, bParent);
            case Short     x -> checkEQimpl(x, ((Short)b).shortValue(),     field, aParent, bParent);
            case Integer   x -> checkEQimpl(x, ((Integer)b).intValue(),     field, aParent, bParent);
            case Long      x -> checkEQimpl(x, ((Long)b).longValue(),       field, aParent, bParent);
            case Float     x -> checkEQimpl(x, ((Float)b).floatValue(),     field, aParent, bParent);
            case Double    x -> checkEQimpl(x, ((Double)b).doubleValue(),   field, aParent, bParent);
            case Boolean   x -> checkEQimpl(x, ((Boolean)b).booleanValue(), field, aParent, bParent);
            case byte[]    x -> checkEQimpl(x, (byte[])b,                   field, aParent, bParent);
            case char[]    x -> checkEQimpl(x, (char[])b,                   field, aParent, bParent);
            case short[]   x -> checkEQimpl(x, (short[])b,                  field, aParent, bParent);
            case int[]     x -> checkEQimpl(x, (int[])b,                    field, aParent, bParent);
            case long[]    x -> checkEQimpl(x, (long[])b,                   field, aParent, bParent);
            case float[]   x -> checkEQimpl(x, (float[])b,                  field, aParent, bParent);
            case double[]  x -> checkEQimpl(x, (double[])b,                 field, aParent, bParent);
            case boolean[] x -> checkEQimpl(x, (boolean[])b,                field, aParent, bParent);
            case MemorySegment x -> checkEQimpl(x, (MemorySegment) b,       field, aParent, bParent);
            case Exception x -> checkEQimpl(x, (Exception) b,               field, aParent, bParent);
            default -> {
                if (ca.getName().startsWith("jdk.incubator.vector") && ca.getName().contains("Vector")) {
                    // We do not want to import jdk.incubator.vector explicitly, because it would mean we would also have
                    // to add "--add-modules=jdk.incubator.vector" to the command-line of every test that uses the Verify
                    // class. So we hack this via reflection.
                    Object va = null;
                    Object vb = null;
                    try {
                        Method m = ca.getMethod("toArray");
                        va = m.invoke(a);
                        vb = m.invoke(b);
                    } catch (NoSuchMethodException e) {
                        throw new RuntimeException("Could not invoke toArray on " + ca.getName(), e);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Could not invoke toArray on " + ca.getName(), e);
                    } catch (InvocationTargetException e) {
                        throw new RuntimeException("Could not invoke toArray on " + ca.getName(), e);
                    }
                    checkEQdispatch(va, vb, field + ".toArray", aParent, bParent);
                    return;
                }

                System.err.println("ERROR: Verify.checkEQ failed: type not supported: " + ca.getName());
                printX(a, b, field, aParent, bParent);
                throw new VerifyException("Object type not supported: " + ca.getName());
            }
        }
    }

    /**
     * Verify that two bytes are identical.
     */
    private void checkEQimpl(byte a, byte b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two chars are identical.
     */
    private void checkEQimpl(char a, char b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + (int)a + " vs " + (int)b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + (int)a + " vs " + (int)b);
        }
    }

    /**
     * Verify that two shorts are identical.
     */
    private void checkEQimpl(short a, short b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + (int)a + " vs " + (int)b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + (int)a + " vs " + (int)b);
        }
    }

    /**
     * Verify that two ints are identical.
     */
    private void checkEQimpl(int a, int b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two longs are identical.
     */
    private void checkEQimpl(long a, long b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * There are two comparison modes: one where we compare the raw bits, which sees different NaN
     * encodings as different values, and one where we see all NaN encodings as identical.
     * Ideally, we would want to assert that the Float.floatToRawIntBits are identical.
     * But the Java spec allows us to return different bits for a NaN, which allows swapping the inputs
     * of an add or mul (NaN1 * NaN2 does not have same bits as NaN2 * NaN1, because the multiplication
     * of two NaN values should always return the first of the two).
     * Hence, by default, we pick the non-raw coparison: we verify that we have the same bit
     * pattern in all cases, except for NaN we project to the canonical NaN, using Float.floatToIntBits.
     */
    private boolean isFloatEQ(float a, float b) {
        return isFloatCheckWithRawBits ? Float.floatToRawIntBits(a) != Float.floatToRawIntBits(b)
                                       : Float.floatToIntBits(a) != Float.floatToIntBits(b);
    }

    /**
     * See comments for "isFloatEQ".
     */
    private boolean isDoubleEQ(double a, double b) {
        return isFloatCheckWithRawBits ? Double.doubleToRawLongBits(a) != Double.doubleToRawLongBits(b)
                                       : Double.doubleToLongBits(a) != Double.doubleToLongBits(b);
    }

    /**
     * Check that two floats are equal according to "isFloatEQ".
     */
    private void checkEQimpl(float a, float b, String field, Object aParent, Object bParent) {
        if (isFloatEQ(a, b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch. check raw: " + isFloatCheckWithRawBits);
            System.err.println("  Values: " + a + " vs " + b);
            System.err.println("  Raw:    " + Float.floatToRawIntBits(a) + " vs " + Float.floatToRawIntBits(b));
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Check that two doubles are equal according to "isDoubleEQ".
     */
    private void checkEQimpl(double a, double b, String field, Object aParent, Object bParent) {
        if (isDoubleEQ(a, b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch. check raw: " + isFloatCheckWithRawBits);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Raw:    " + Double.doubleToRawLongBits(a) + " vs " + Double.doubleToRawLongBits(b));
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two booleans are identical.
     */
    private void checkEQimpl(boolean a, boolean b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that the content of two MemorySegments is identical. Note: we do not check the
     * backing type, only the size and content.
     */
    private void checkEQimpl(MemorySegment a, MemorySegment b, String field, Object aParent, Object bParent) {
        long offset = a.mismatch(b);
        if (offset == -1) { return; }

        // Print some general info
        System.err.println("ERROR: Verify.checkEQ failed");

        printX(a, b, field, aParent, bParent);
        printMemorySegment(a, "a");
        printMemorySegment(b, "b");

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
     * Verify that the content of two MemorySegments is identical. Note: we do not check the
     * backing type, only the size and content.
     */
    private void checkEQimpl(Exception a, Exception b, String field, Object aParent, Object bParent) {
        String am = a.getMessage();
        String bm = b.getMessage();

        // Missing messages is expected, but if they both have one, they must agree.
        if (am == null || bm == null) { return; }
        if (am.equals(bm)) { return; }

        System.err.println("ERROR: Verify.checkEQ failed:");
        System.out.println("a: " + a.getMessage());
        System.out.println("b: " + b.getMessage());
        printX(a, b, field, aParent, bParent);
        throw new VerifyException("Exception message mismatch: " + a + " vs " + b);
    }

    /**
     * Verify that the content of two byte arrays is identical.
     */
    private void checkEQimpl(byte[] a, byte[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the content of two char arrays is identical.
     */
    private void checkEQimpl(char[] a, char[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the content of two short arrays is identical.
     */
    private void checkEQimpl(short[] a, short[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the content of two int arrays is identical.
     */
    private void checkEQimpl(int[] a, int[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the content of two long arrays is identical.
     */
    private void checkEQimpl(long[] a, long[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Check that two float arrays are equal according to "isFloatEQ".
     */
    private void checkEQimpl(float[] a, float[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Float array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (isFloatEQ(a[i], b[i])) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + ". check raw: " + isFloatCheckWithRawBits);
                printX(a, b, field, aParent, bParent);
                throw new VerifyException("Float array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Check that two double arrays are equal according to "isDoubleEQ".
     */
    private void checkEQimpl(double[] a, double[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Double array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (isDoubleEQ(a[i], b[i])) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + ". check raw: " + isFloatCheckWithRawBits);
                printX(a, b, field, aParent, bParent);
                throw new VerifyException("Double array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Verify that the content of two boolean arrays is identical.
     */
    private void checkEQimpl(boolean[] a, boolean[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Boolean array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i]);
                printX(a, b, field, aParent, bParent);
                throw new VerifyException("Boolean array value mismatch.");
            }
        }
    }

    /**
     * Verify that the content of two Object arrays is identical, recursively:
     * every element is compared with checkEQimpl for the corresponding type.
     */
    private void checkEQimpl(Object[] a, Object[] b, String field, Object aParent, Object bParent) {
        // (1) Length mismatch
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length);
            printX(a, b, field, aParent, bParent);
            throw new VerifyException("Object array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            // Recursive checkEQ call.
            checkEQdispatch(a[i], b[i], "[" + i + "]", a, b);
        }
    }

    private void printX(Object a, Object b, String field, Object aParent, Object bParent) {
        System.err.println("  aParent: " + aParent);
        System.err.println("  bParent: " + bParent);
        System.err.println("  field:   " + field);
        System.err.println("  a:       " + a);
        System.err.println("  b:       " + b);
    }

    private boolean checkAlreadyVisited(Object a, Object b, String field, Object aParent, Object bParent) {
        // TODO: must also check reverse direction?
        Integer id = a2id.get(a);
        if (id == null) {
            // Record for next time.
            id = id2a.size();
            a2id.put(a, id);
            a2b.put(a, b);
            id2a.add(a);
            return false;
        } else {
            Object bPrevious = a2b.get(a);
            if (b != bPrevious) {
                System.err.println("ERROR: Verify.checkEQ failed:");
                printX(a, b, field, aParent, bParent);
                System.err.println("  bPrevious: " + bPrevious);
                throw new VerifyException("Mismatch with previous pair.");
            }
            return true;
        }
    }

    private void printMemorySegment(MemorySegment a, String name) {
        Optional<Object> maybeBase = a.heapBase();
        System.err.println("  MemorySegment " + name + ":");
        if (maybeBase.isEmpty()) {
            System.err.println("    no heap base (native).");
        } else {
            Object base = maybeBase.get();
            System.err.println("    heap base: " + base);
        }
        System.err.println("    address: " + a.address());
        System.err.println("    byteSize: " + a.byteSize());
    }

    private void printMemorySegmentValue(MemorySegment a, long offset, int range) {
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
