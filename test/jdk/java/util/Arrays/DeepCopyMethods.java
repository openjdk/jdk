/*
 * Copyright (c) 2005, Oracle and/or its affiliates. All rights reserved.
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
 * @bug     8173962
 * @summary Test for deep array cloning methods.
 * @author  Fabio Romano
 * @key randomness
 */

import java.lang.reflect.Array;
import java.util.*;

public class DeepCopyMethods {

	int[] sizes = {0, 10, 100, 200, 1000};

	void test(String[] args) throws Throwable {
		deepEqual(Arrays.deepCopyOf(new Object[]{}), new Object[]{});
		deepEqual(Arrays.deepCopyOf(new Object[]{null}), new Object[]{null});
		deepEqual(Arrays.deepCopyOf(new Object[]{null, 1}), new Object[]{null, 1});
		deepEqual(Arrays.deepCopyOf(new Object[]{1, null}), new Object[]{1, null});
		deepEqual(Arrays.deepCopyOf(new Object[]{new Object[]{}, null}), new Object[]{new Object[]{}, null});

        {
            // deepEquals method is undefined for self-referenced arrays,
        	// so equal self-referenced arrays can't be recognised
        	Object[] a = {1, null};
            a[1] = a;
            check(!Arrays.equals(Arrays.deepCopyOf(a), a));
            a[0] = a;
            check(!Arrays.equals(Arrays.deepCopyOf(a), a));
            a[0] = a[1] = new Object[]{1, null, a};
            check(!Arrays.equals(Arrays.deepCopyOf(a), a));
        }

        for (int size : sizes) {
            {
                Object[] a = Rnd.flatObjectArray(size);
                deepEqual(Arrays.deepCopyOf(a), a);
            }

            if (size <= 200) {
                Object[] a = Rnd.nestedObjectArray(size);
                Object[] deepCopy = Arrays.deepCopyOf(a);

                
                deepEqual(a, deepCopy);
                deepEqual(deepCopy, a);

                // Make deepCopy != a
                if (size == 0)
                    deepCopy = new Object[] {"foo"};
                else if (deepCopy[deepCopy.length - 1] == null)
                    deepCopy[deepCopy.length - 1] = "baz";
                else
                    deepCopy[deepCopy.length - 1] = null;
                check(!Arrays.deepEquals(a, deepCopy));
                check(!Arrays.deepEquals(deepCopy, a));
            }
        }
    }
	
	void deepEqual(Object[] x, Object[] y) {
    	if (Arrays.deepEquals(x, y))
    		pass();
        else
        	fail(x + " not equal to " + y);
    }
	
	//--------------------- Infrastructure ---------------------------
    volatile int passed = 0, failed = 0;
    void pass() {passed++;}
    void fail() {failed++; Thread.dumpStack();}
    void fail(String msg) {System.err.println(msg); fail();}
    void unexpected(Throwable t) {failed++; t.printStackTrace();}
    void check(boolean cond) {if (cond) pass(); else fail();}
    void equal(Object x, Object y) {
    	if (Objects.equals(x, y)) pass();
        else fail(x + " not equal to " + y);}
    public static void main(String[] args) throws Throwable {
        new DeepCopyMethods().instanceMain(args);}
    void instanceMain(String[] args) throws Throwable {
        try {test(args);} catch (Throwable t) {unexpected(t);}
        System.out.printf("%nPassed = %d, failed = %d%n%n", passed, failed);
        if (failed > 0) throw new AssertionError("Some tests failed");}
}

/**
 * Methods to generate "interesting" random primitives and primitive
 * arrays.  Unlike Random.nextXxx, these methods return small values
 * and boundary values (e.g., 0, -1, NaN) with greater than normal
 * likelihood.
 */

class Rnd {
	
	private static Random rnd = new Random();
	
	public static long nextLong() {
        switch(rnd.nextInt(10)) {
            case 0:  return 0;
            case 1:  return Long.MIN_VALUE;
            case 2:  return Long.MAX_VALUE;
            case 3: case 4: case 5:
                     return (long) (rnd.nextInt(20) - 10);
            default: return rnd.nextLong();
        }
    }

    public static int nextInt() {
        switch(rnd.nextInt(10)) {
            case 0:  return 0;
            case 1:  return Integer.MIN_VALUE;
            case 2:  return Integer.MAX_VALUE;
            case 3: case 4: case 5:
                     return rnd.nextInt(20) - 10;
            default: return rnd.nextInt();
        }
    }

    public static short nextShort() {
        switch(rnd.nextInt(10)) {
            case 0:  return 0;
            case 1:  return Short.MIN_VALUE;
            case 2:  return Short.MAX_VALUE;
            case 3: case 4: case 5:
                     return (short) (rnd.nextInt(20) - 10);
            default: return (short) rnd.nextInt();
        }
    }

    public static char nextChar() {
        switch(rnd.nextInt(10)) {
            case 0:  return 0;
            case 1:  return Character.MIN_VALUE;
            case 2:  return Character.MAX_VALUE;
            case 3: case 4: case 5:
                     return (char) (rnd.nextInt(20) - 10);
            default: return (char) rnd.nextInt();
        }
    }

    public static byte nextByte() {
        switch(rnd.nextInt(10)) {
            case 0:  return 0;
            case 1:  return Byte.MIN_VALUE;
            case 2:  return Byte.MAX_VALUE;
            case 3: case 4: case 5:
                     return (byte) (rnd.nextInt(20) - 10);
            default: return (byte) rnd.nextInt();
        }
    }

    public static double nextDouble() {
        switch(rnd.nextInt(20)) {
            case 0:  return 0;
            case 1:  return -0.0;
            case 2:  return Double.MIN_VALUE;
            case 3:  return Double.MAX_VALUE;
            case 4:  return Double.NaN;
            case 5:  return Double.NEGATIVE_INFINITY;
            case 6:  return Double.POSITIVE_INFINITY;
            case 7: case 8: case 9:
                     return (rnd.nextInt(20) - 10);
            default: return rnd.nextDouble();
        }
    }

    public static float nextFloat() {
        switch(rnd.nextInt(20)) {
            case 0:  return 0;
            case 1:  return -0.0f;
            case 2:  return Float.MIN_VALUE;
            case 3:  return Float.MAX_VALUE;
            case 4:  return Float.NaN;
            case 5:  return Float.NEGATIVE_INFINITY;
            case 6:  return Float.POSITIVE_INFINITY;
            case 7: case 8: case 9:
                     return (rnd.nextInt(20) - 10);
            default: return rnd.nextFloat();
        }
    }
	
	public static Object nextObject() {
        switch(rnd.nextInt(10)) {
            case 0:  return null;
            case 1:  return "foo";
            case 2:  case 3: case 4:
                     return Double.valueOf(nextDouble());
            default: return Integer.valueOf(nextInt());
        }
    }
	
	public static long[] longArray(int length) {
        long[] result = new long[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextLong();
        return result;
    }

    public static int[] intArray(int length) {
        int[] result = new int[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextInt();
        return result;
    }

    public static short[] shortArray(int length) {
        short[] result = new short[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextShort();
        return result;
    }

    public static char[] charArray(int length) {
        char[] result = new char[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextChar();
        return result;
    }

    public static byte[] byteArray(int length) {
        byte[] result = new byte[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextByte();
        return result;
    }
    
    public static double[] doubleArray(int length) {
        double[] result = new double[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextDouble();
        return result;
    }

    public static float[] floatArray(int length) {
        float[] result = new float[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextFloat();
        return result;
    }
	
	public static Object[] flatObjectArray(int length) {
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++)
            result[i] = Rnd.nextObject();
        return result;
    }
	
	// Calling this for length >> 100 is likely to run out of memory!  It
    // should be perhaps be tuned to allow for longer arrays
    public static Object[] nestedObjectArray(int length) {
        Object[] result = new Object[length];
        for (int i = 0; i < length; i++) {
            switch(rnd.nextInt(16)) {
                case 0:  result[i] = nestedObjectArray(length/2);
                         break;
                case 1:  result[i] = longArray(length/2);
                         break;
                case 2:  result[i] = intArray(length/2);
                         break;
                case 3:  result[i] = shortArray(length/2);
                         break;
                case 4:  result[i] = charArray(length/2);
                         break;
                case 5:  result[i] = byteArray(length/2);
                         break;
                case 6:  result[i] = floatArray(length/2);
                         break;
                case 7:  result[i] = doubleArray(length/2);
                         break;
                case 8:  result[i] = longArray(length/2);
                         break;
                default: result[i] = Rnd.nextObject();
            }
        }
        return result;
    }
}
