/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;


/**
 * The {@link Verify} class provides {@link Verify#checkEQ} and {@link Verify#checkEQWithRawBits},
 * which recursively compare the two {@link Object}s by value. They deconstruct an array of objects,
 * compare boxed primitive types, compare the content of arrays and {@link MemorySegment}s, and check
 * that the messages of two {@link Exception}s are equal. They also check for the equivalent content
 * in {@code Vector}s from the Vector API.
 *
 * <p>
 * Further, they compare Objects from arbitrary classes, using reflection. We check the fields of the
 * Objects, and compare their recursive structure. Since we use reflection, this can be slow.
 *
 * <p>
 * When a comparison fails, then methods print helpful messages, before throwing a {@link VerifyException}.
 *
 * <p>
 * We have to take special care of {@link Float}s and {@link Double}s, since they both have various
 * encodings for NaN values while the Java specification regards them as equal. Hence, we
 * have two modes of comparison. With {@link Verify#checkEQ} different NaN values are regarded as equal.
 * This applies to the boxed floating types, as well as arrays of floating arrays. With
 * {@link Verify#checkEQWithRawBits} we compare the raw bits, and so different NaN encodings are not equal.
 * Note: {@link MemorySegment} data is always compared with raw bits.
 */
public final class Verify {
    private final boolean isFloatCheckWithRawBits;

    /**
     * When comparing arbitrary classes recursively, we need to remember which
     * pairs of objects {@code (a, b)} we have already visited. The maps
     * {@code a2b} and {@code b2a} track these edges. Caching which pairs
     * we have already visited means the traversal only needs to visit every
     * pair once. And should we ever find a pair {@code (a, b')} or {@code (a', b)},
     * then we know that the two structures are not structurally equivalent.
     */
    private final HashMap<Object, Object> a2b = new HashMap<>();
    private final HashMap<Object, Object> b2a = new HashMap<>();

    private Verify(boolean isFloatCheckWithRawBits) {
        this.isFloatCheckWithRawBits = isFloatCheckWithRawBits;
    }

    /**
     * Verify the contents of two Objects on a raw bit level, possibly recursively.
     * Different NaN encodings are considered non-equal, since we compare
     * floating numbers by their raw bits.
     *
     * @param a First object to be recursively compared with the second.
     * @param b Second object to be recursively compared with the first.
     * @throws VerifyException If the comparison fails.
     */
    public static void checkEQWithRawBits(Object a, Object b) {
        Verify v = new Verify(true);
        v.checkEQdispatch(a, b, "<root>", null, null);
    }

    /**
     * Verify the contents of two Objects, possibly recursively.
     * Different NaN encodings are considered equal.
     *
     * @param a First object to be recursively compared with the second.
     * @param b Second object to be recursively compared with the first.
     * @throws VerifyException If the comparison fails.
     */
    public static void checkEQ(Object a, Object b) {
        Verify v = new Verify(false);
        v.checkEQdispatch(a, b, "<root>", null, null);
    }

    private void checkEQdispatch(Object a, Object b, String field, Object aParent, Object bParent) {
        // Both null
        if (a == null && b == null) {
            return;
        }

        // Null mismatch
        if (a == null || b == null) {
            System.err.println("ERROR: Equality matching failed: null mismatch");
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Object array null mismatch.");
        }

        // Class mismatch
        Class<?> ca = a.getClass();
        Class<?> cb = b.getClass();
        if (ca != cb) {
            System.err.println("ERROR: Equality matching failed: class mismatch.");
            System.err.println("       " + ca.getName() + " vs " + cb.getName());
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Object class mismatch.");
        }

        // Already visited? This makes sure that we are not stuck in cycles, and that we have
        // a mapping of pairs (a, b) for structurally equivalent Objects.
        if (checkAlreadyVisited(a, b, field, aParent, bParent)) {
            return;
        }

        switch (a) {
            case Object[]  x -> checkEQimpl(x, (Object[])b,                 field, aParent, bParent);
            case Byte      x -> checkEQimpl(x, (Byte)b,                     field, aParent, bParent);
            case Character x -> checkEQimpl(x, (Character)b,                field, aParent, bParent);
            case Short     x -> checkEQimpl(x, (Short)b,                    field, aParent, bParent);
            case Integer   x -> checkEQimpl(x, (Integer)b,                  field, aParent, bParent);
            case Long      x -> checkEQimpl(x, (Long)b,                     field, aParent, bParent);
            case Float     x -> checkEQimpl(x, (Float)b,                    field, aParent, bParent);
            case Double    x -> checkEQimpl(x, (Double)b,                   field, aParent, bParent);
            case Boolean   x -> checkEQimpl(x, (Boolean)b,                  field, aParent, bParent);
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
                if (isVectorAPIClass(ca)) {
                    checkEQForVectorAPIClass(a, b, field, aParent, bParent);
                } else {
                    checkEQArbitraryClasses(a, b);
                }
            }
        }
    }

    /**
     * Verify that two bytes are identical.
     */
    private void checkEQimpl(byte a, byte b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Equality matching failed: value mismatch: " + a + " vs " + b);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two chars are identical.
     */
    private void checkEQimpl(char a, char b, String field, Object aParent, Object bParent) {
        if (a != b) {
            // Note: we need to cast "(int)a", otherwise a char of numerical value "66" is
            //       formatted as character "B".
            System.err.println("ERROR: Equality matching failed: value mismatch: " + (int)a + " vs " + (int)b);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + (int)a + " vs " + (int)b);
        }
    }

    /**
     * Verify that two shorts are identical.
     */
    private void checkEQimpl(short a, short b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Equality matching failed: value mismatch: " + a + " vs " + b);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two ints are identical.
     */
    private void checkEQimpl(int a, int b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Equality matching failed: value mismatch: " + a + " vs " + b);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two longs are identical.
     */
    private void checkEQimpl(long a, long b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Equality matching failed: value mismatch: " + a + " vs " + b);
            print(a, b, field, aParent, bParent);
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
     * Hence, by default, we pick the non-raw comparison: we verify that we have the same bit
     * pattern in all cases, except for NaN we project to the canonical NaN, using Float.floatToIntBits.
     */
    private boolean isFloatEQ(float a, float b) {
        return isFloatCheckWithRawBits ? Float.floatToRawIntBits(a) == Float.floatToRawIntBits(b)
                                       : Float.floatToIntBits(a) == Float.floatToIntBits(b);
    }

    /**
     * See comments for {@link #isFloatEQ}.
     */
    private boolean isDoubleEQ(double a, double b) {
        return isFloatCheckWithRawBits ? Double.doubleToRawLongBits(a) == Double.doubleToRawLongBits(b)
                                       : Double.doubleToLongBits(a) == Double.doubleToLongBits(b);
    }

    /**
     * Check that two floats are equal according to {@link #isFloatEQ}.
     */
    private void checkEQimpl(float a, float b, String field, Object aParent, Object bParent) {
        if (!isFloatEQ(a, b)) {
            System.err.println("ERROR: Equality matching failed: value mismatch. check raw: " + isFloatCheckWithRawBits);
            System.err.println("  Values: " + a + " vs " + b);
            System.err.println("  Raw:    " + Float.floatToRawIntBits(a) + " vs " + Float.floatToRawIntBits(b));
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Check that two doubles are equal according to {@link #isDoubleEQ}.
     */
    private void checkEQimpl(double a, double b, String field, Object aParent, Object bParent) {
        if (!isDoubleEQ(a, b)) {
            System.err.println("ERROR: Equality matching failed: value mismatch. check raw: " + isFloatCheckWithRawBits);
            System.err.println("       Values: " + a + " vs " + b);
            System.err.println("       Raw:    " + Double.doubleToRawLongBits(a) + " vs " + Double.doubleToRawLongBits(b));
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that two booleans are identical.
     */
    private void checkEQimpl(boolean a, boolean b, String field, Object aParent, Object bParent) {
        if (a != b) {
            System.err.println("ERROR: Equality matching failed: value mismatch: " + a + " vs " + b);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Value mismatch: " + a + " vs " + b);
        }
    }

    /**
     * Verify that the contents of two MemorySegments are identical. Note: we do not check the
     * backing type, only the size and content.
     */
    private void checkEQimpl(MemorySegment a, MemorySegment b, String field, Object aParent, Object bParent) {
        long offset = a.mismatch(b);
        if (offset == -1) { return; }

        // Print some general info
        System.err.println("ERROR: Equality matching failed");

        print(a, b, field, aParent, bParent);
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
     * Verify that two Exceptions have the same message. Messages are not always carried,
     * they are often dropped for performance reasons, and that is okay. But if both Exceptions
     * have the message, we should compare them.
     */
    private void checkEQimpl(Exception a, Exception b, String field, Object aParent, Object bParent) {
        String am = a.getMessage();
        String bm = b.getMessage();

        // Missing messages is expected, but if they both have one, they must agree.
        if (am == null || bm == null) { return; }
        if (am.equals(bm)) { return; }

        System.err.println("ERROR: Equality matching failed:");
        System.err.println("a: " + a.getMessage());
        System.err.println("b: " + b.getMessage());
        print(a, b, field, aParent, bParent);
        throw new VerifyException("Exception message mismatch: " + a + " vs " + b);
    }

    /**
     * Verify that the contents of two byte arrays are identical.
     */
    private void checkEQimpl(byte[] a, byte[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the contents of two char arrays are identical.
     */
    private void checkEQimpl(char[] a, char[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the contents of two short arrays are identical.
     */
    private void checkEQimpl(short[] a, short[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the contents of two int arrays are identical.
     */
    private void checkEQimpl(int[] a, int[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Verify that the contents of two long arrays are identical.
     */
    private void checkEQimpl(long[] a, long[] b, String field, Object aParent, Object bParent) {
        checkEQimpl(MemorySegment.ofArray(a), MemorySegment.ofArray(b), field + " -> to MemorySegment", aParent, bParent);
    }

    /**
     * Check that two float arrays are equal according to {@link #isFloatEQ}.
     */
    private void checkEQimpl(float[] a, float[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Equality matching failed: length mismatch: " + a.length + " vs " + b.length);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Float array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (!isFloatEQ(a[i], b[i])) {
                System.err.println("ERROR: Equality matching failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + ". check raw: " + isFloatCheckWithRawBits);
                print(a, b, field, aParent, bParent);
                throw new VerifyException("Float array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Check that two double arrays are equal according to {@link #isDoubleEQ}.
     */
    private void checkEQimpl(double[] a, double[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Equality matching failed: length mismatch: " + a.length + " vs " + b.length);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Double array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (!isDoubleEQ(a[i], b[i])) {
                System.err.println("ERROR: Equality matching failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i] + ". check raw: " + isFloatCheckWithRawBits);
                print(a, b, field, aParent, bParent);
                throw new VerifyException("Double array value mismatch " + a[i] + " vs " + b[i]);
            }
        }
    }

    /**
     * Verify that the contents of two boolean arrays are identical.
     */
    private void checkEQimpl(boolean[] a, boolean[] b, String field, Object aParent, Object bParent) {
        if (a.length != b.length) {
            System.err.println("ERROR: Equality matching failed: length mismatch: " + a.length + " vs " + b.length);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Boolean array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) {
                System.err.println("ERROR: Equality matching failed: value mismatch at " + i + ": " + a[i] + " vs " + b[i]);
                print(a, b, field, aParent, bParent);
                throw new VerifyException("Boolean array value mismatch.");
            }
        }
    }

    /**
     * Verify that the contents of two Object arrays are identical, recursively:
     * every element is compared with checkEQimpl for the corresponding type.
     */
    private void checkEQimpl(Object[] a, Object[] b, String field, Object aParent, Object bParent) {
        // (1) Length mismatch
        if (a.length != b.length) {
            System.err.println("ERROR: Equality matching failed: length mismatch: " + a.length + " vs " + b.length);
            print(a, b, field, aParent, bParent);
            throw new VerifyException("Object array length mismatch.");
        }

        for (int i = 0; i < a.length; i++) {
            // Recursive checkEQ call.
            checkEQdispatch(a[i], b[i], "[" + i + "]", a, b);
        }
    }

    private static boolean isVectorAPIClass(Class<?> c) {
        return c.getName().startsWith("jdk.incubator.vector") &&
               c.getName().contains("Vector");
    }

    /**
     * We do not want to import jdk.incubator.vector explicitly, because it would mean we would also have
     * to add "--add-modules=jdk.incubator.vector" to the command-line of every test that uses the Verify
     * class. So we hack this via reflection.
     */
    private void checkEQForVectorAPIClass(Object a, Object b, String field, Object aParent, Object bParent) {
        Class<?> ca = a.getClass();
        Object va;
        Object vb;
        try {
            Method m = ca.getMethod("toArray");
            m.setAccessible(true);
            va = m.invoke(a);
            vb = m.invoke(b);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Could not invoke toArray on " + ca.getName(), e);
        }
        checkEQdispatch(va, vb, field + ".toArray", aParent, bParent);
    }

    private void checkEQArbitraryClasses(Object a, Object b) {
        Class<?> c = a.getClass();
        while (c != Object.class) {
            for (Field field : c.getDeclaredFields()) {
                Object va;
                Object vb;
                try {
                    field.setAccessible(true);
                    va = field.get(a);
                    vb = field.get(b);
                } catch (IllegalAccessException e) {
                    throw new VerifyException("Failure to access field: " + field + " of " + a);
                }
                checkEQdispatch(va, vb, field.getName(), a, b);
            }
            c = c.getSuperclass();
        }
    }

    private void print(Object a, Object b, String field, Object aParent, Object bParent) {
        System.err.println("  aParent: " + (aParent != null ? aParent : "<none>"));
        System.err.println("  bParent: " + (bParent != null ? bParent : "<none>"));
        System.err.println("  field:   " + field);
        System.err.println("  a:       " + a);
        System.err.println("  b:       " + b);
    }
    /**
     * When comparing arbitrary classes recursively, we need to remember which
     * pairs of objects {@code (a, b)} we have already visited. The maps
     * {@link #a2b} and {@link #b2a} track these edges. Caching which pairs
     * we have already visited means the traversal only needs to visit every
     * pair once. And should we ever find a pair {@code (a, b')} or {@code (a', b)},
     * then we know that the two structures are not structurally equivalent.
     */
    private boolean checkAlreadyVisited(Object a, Object b, String field, Object aParent, Object bParent) {
        // Boxed primitives are not guaranteed to be the same Object for the same primitive value.
        // Hence, we cannot use the mapping below. We test these boxed primitive types by value anyway,
        // and they are no recursive structures, so there is no point in optimizing here anyway.
        switch (a) {
            case Boolean _,
                 Byte _,
                 Short _,
                 Character _,
                 Integer _,
                 Long _,
                 Float _,
                 Double _ -> { return false; }
            default -> {}
        }

        Object bPrevious = a2b.get(a);
        Object aPrevious = b2a.get(b);
        if (aPrevious == null && bPrevious == null) {
            // Record for next time.
            a2b.put(a, b);
            b2a.put(b, a);
            return false;
        } else {
            if (a != aPrevious || b != bPrevious) {
                System.err.println("ERROR: Equality matching failed:");
                print(a, b, field, aParent, bParent);
                System.err.println("  aPrevious: " + aPrevious);
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
        System.err.println();
        for (long i = start; i < end; i++) {
            if (i == offset) {
                System.err.print("^^ ");
            } else {
                System.err.print("   ");
            }
        }
        System.err.println();
    }
}
