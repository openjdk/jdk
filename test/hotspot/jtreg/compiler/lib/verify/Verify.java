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
        v.checkX(a, b, "root", null, null);
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

    private void checkX(Object a, Object b, String field, Object aParent, Object bParent) {
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
        // FIXME: continue here!

        String context = "TODO rm";

        switch (a) {
            case Object[]  x -> checkEQimpl(x, (Object[])b,                context);
            case Byte      x -> checkEQimpl(x, ((Byte)b).byteValue(),      context);
            case Character x -> checkEQimpl(x, ((Character)b).charValue(), context);
            case Short     x -> checkEQimpl(x, ((Short)b).shortValue(),    context);
            case Integer   x -> checkEQimpl(x, ((Integer)b).intValue(),    context);
            case Long      x -> checkEQimpl(x, ((Long)b).longValue(),      context);
            case Float     x -> checkEQimpl(x, ((Float)b).floatValue(),    context);
            case Double    x -> checkEQimpl(x, ((Double)b).doubleValue(),  context);
            case Boolean   x -> checkEQimpl(x, ((Boolean)b).booleanValue(),context);
            case byte[]    x -> checkEQimpl(x, (byte[])b,                  context);
            case char[]    x -> checkEQimpl(x, (char[])b,                  context);
            case short[]   x -> checkEQimpl(x, (short[])b,                 context);
            case int[]     x -> checkEQimpl(x, (int[])b,                   context);
            case long[]    x -> checkEQimpl(x, (long[])b,                  context);
            case float[]   x -> checkEQimpl(x, (float[])b,                 context);
            case double[]  x -> checkEQimpl(x, (double[])b,                context);
            case boolean[] x -> checkEQimpl(x, (boolean[])b,               context);
            case MemorySegment x -> checkEQimpl(x, (MemorySegment) b,      context);
            case Exception x -> checkEQimpl(x, (Exception) b,              context);
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
                    checkEQ(va, vb, context);
                    return;
                }

                System.err.println("ERROR: Verify.checkEQ failed: type not supported: " + ca.getName());
                printX(a, b, field, aParent, bParent);
                throw new VerifyException("Object type not supported: " + ca.getName());
            }
        }
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
            case Boolean   x -> checkEQimpl(x, ((Boolean)b).booleanValue(),context);
            case byte[]    x -> checkEQimpl(x, (byte[])b,                  context);
            case char[]    x -> checkEQimpl(x, (char[])b,                  context);
            case short[]   x -> checkEQimpl(x, (short[])b,                 context);
            case int[]     x -> checkEQimpl(x, (int[])b,                   context);
            case long[]    x -> checkEQimpl(x, (long[])b,                  context);
            case float[]   x -> checkEQimpl(x, (float[])b,                 context);
            case double[]  x -> checkEQimpl(x, (double[])b,                context);
            case boolean[] x -> checkEQimpl(x, (boolean[])b,               context);
            case MemorySegment x -> checkEQimpl(x, (MemorySegment) b,      context);
            case Exception x -> checkEQimpl(x, (Exception) b,              context);
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
                    checkEQ(va, vb, context);
                    return;
                }

                System.err.println("ERROR: Verify.checkEQ failed: type not supported: " + ca.getName());
                print(a, "a " + context);
                print(b, "b " + context);
                throw new VerifyException("Object type not supported: " + ca.getName());
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
     * Ideally, we would want to assert that the Float.floatToRawIntBits are identical.
     * But the Java spec allows us to return different bits for a NaN, which allows swapping the inputs
     * of an add or mul (NaN1 * NaN2 does not have same bits as NaN2 * NaN1, because the multiplication
     * of two NaN values should always return the first of the two). So we verify that we have the same bit
     * pattern in all cases, except for NaN we project to the canonical NaN, using Float.floatToIntBits.
     */
    private static void checkEQimpl(float a, float b, String context) {
        if (Float.floatToIntBits(a) != Float.floatToIntBits(b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch for " + context);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Values: " + Float.floatToRawIntBits(a) + " vs " + Float.floatToRawIntBits(b));
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Same as Float case, see above.
     */
    private static void checkEQimpl(double a, double b, String context) {
        if (Double.doubleToLongBits(a) != Double.doubleToLongBits(b)) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch for " + context);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Values: " + Double.doubleToRawLongBits(a) + " vs " + Double.doubleToRawLongBits(b));
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two booleans are identical.
     */
    private static void checkEQimpl(boolean a, boolean b, String context) {
        if (a != b) {
            System.err.println("ERROR: Verify.checkEQ failed: value mismatch: " + a + " vs " + b + " for " + context);
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
     * Verify that the content of two MemorySegments is identical. Note: we do not check the
     * backing type, only the size and content.
     */
    private static void checkEQimpl(Exception a, Exception b, String context) {
        String am = a.getMessage();
        String bm = b.getMessage();

        // Missing messages is expected, but if they both have one, they must agree.
        if (am == null || bm == null) { return; }
        if (am.equals(bm)) { return; }

        System.err.println("ERROR: Verify.checkEQ failed for: " + context);
        System.out.println("a: " + a.getMessage());
        System.out.println("b: " + b.getMessage());
        System.out.println(a);
        System.out.println(b);
        throw new VerifyException("Exception message mismatch: " + a + " vs " + b);
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
     * Ideally, we would want to assert that the Float.floatToRawIntBits are identical.
     * But the Java spec allows us to return different bits for a NaN, which allows swapping the inputs
     * of an add or mul (NaN1 * NaN2 does not have same bits as NaN2 * NaN1, because the multiplication
     * of two NaN values should always return the first of the two). So we verify that we have the same bit
     * pattern in all cases, except for NaN we project to the canonical NaN, using Float.floatToIntBits.
     */
    private static void checkEQimpl(float[] a, float[] b, String context) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length + " " + context);
            throw new VerifyException("Float array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (Float.floatToIntBits(a[i]) != Float.floatToIntBits(b[i])) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + " " + context);
                throw new VerifyException("Float array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Verify that the content of two double arrays is identical.
     * Same issue with NaN as above for floats.
     */
    private static void checkEQimpl(double[] a, double[] b, String context) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length + " " + context);
            throw new VerifyException("Double array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (Double.doubleToLongBits(a[i]) != Double.doubleToLongBits(b[i])) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + " " + context);
                throw new VerifyException("Double array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Verify that the content of two boolean arrays is identical.
     */
    private static void checkEQimpl(boolean[] a, boolean[] b, String context) {
        if (a.length != b.length) {
            System.err.println("ERROR: Verify.checkEQ failed: length mismatch: " + a.length + " vs " + b.length + " " + context);
            throw new VerifyException("Boolean array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("ERROR: Verify.checkEQ failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + " " + context);
                throw new VerifyException("Boolean array value mismatch.");
            }
        }
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

    private void printX(Object a, Object b, String field, Object aParent, Object bParent) {
        System.err.println("  aParent: " + aParent);
        System.err.println("  bParent: " + bParent);
        System.err.println("  field:   " + field);
        System.err.println("  a:       " + a);
        System.err.println("  b:       " + b);
    }

    private boolean checkAlreadyVisited(Object a, Object b, String field, Object aParent, Object bParent) {
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
