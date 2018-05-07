/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Test extended ArrayIndexOutOfBoundsException message. The
 *   message lists information about the array and the indexes involved.
 * @compile ArrayIndexOutOfBoundsExceptionTest.java
 * @run testng ArrayIndexOutOfBoundsExceptionTest
 * @run testng/othervm -Xcomp -XX:-TieredCompilation  ArrayIndexOutOfBoundsExceptionTest
 * @run testng/othervm -Xcomp -XX:TieredStopAtLevel=1 ArrayIndexOutOfBoundsExceptionTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

/**
 * Tests the detailed messages of the ArrayIndexOutOfBoundsException.
 */
public class ArrayIndexOutOfBoundsExceptionTest {

    // Some fields used in the test.
    static int[] staticArray = new int[0];
    static long[][] staticLongArray = new long[0][0];
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> curr;

    public static void main(String[] args) {
        ArrayIndexOutOfBoundsExceptionTest t = new ArrayIndexOutOfBoundsExceptionTest();
        try {
            t.testAIOOBMessages();
        } catch (Exception e) {}
    }

    /**
     *
     */
    public static class ArrayGenerator {

        /**
         * @param dummy1
         * @return Object Array
         */
        public static Object[] arrayReturner(boolean dummy1) {
            return new Object[0];
        }

        /**
         * @param dummy1
         * @param dummy2
         * @param dummy3
         * @return Object Array
         */
        public Object[] returnMyArray(double dummy1, long dummy2, short dummy3) {
            return new Object[0];
        }
    }

    /**
     *
     */
    @Test
    public void testAIOOBMessages() {
        boolean[] za1 = new boolean[0];
        byte[]    ba1 = new byte[0];
        short[]   sa1 = new short[0];
        char[]    ca1 = new char[0];
        int[]     ia1 = new int[0];
        long[]    la1 = new long[0];
        float[]   fa1 = new float[0];
        double[]  da1 = new double[0];
        Object[]  oa1 = new Object[10];
        Object[]  oa2 = new Object[5];

        boolean[] za2 = new boolean[10];
        boolean[] za3 = new boolean[5];
        byte[]    ba2 = new byte[10];
        byte[]    ba3 = new byte[5];
        short[]   sa2 = new short[10];
        short[]   sa3 = new short[5];
        char[]    ca2 = new char[10];
        char[]    ca3 = new char[5];
        int[]     ia2 = new int[10];
        int[]     ia3 = new int[5];
        long[]    la2 = new long[10];
        long[]    la3 = new long[5];
        float[]   fa2 = new float[10];
        float[]   fa3 = new float[5];
        double[]  da2 = new double[10];
        double[]  da3 = new double[5];

        try {
            System.out.println(za1[-5]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index -5 out of bounds for length 0");
        }

        try {
            System.out.println(ba1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(sa1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(ca1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(ia1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(la1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(fa1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(da1[0]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(oa1[12]);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 12 out of bounds for length 10");
        }

        try {
            System.out.println(za1[0] = false);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(ba1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(sa1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(ca1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(ia1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(la1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(fa1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(da1[0] = 0);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        try {
            System.out.println(oa1[-2] = null);
            fail();
        }
        catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index -2 out of bounds for length 10");
        }

        try {
            assertTrue((ArrayGenerator.arrayReturner(false))[0] == null);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }
        try {
            staticArray[0] = 2;
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "Index 0 out of bounds for length 0");
        }

        // Test all five possible messages of arraycopy exceptions thrown in ObjArrayKlass::copy_array().

        try {
            System.arraycopy(oa1, -17, oa2, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: source index -17 out of bounds for object array[10]");
        }

        try {
            System.arraycopy(oa1, 2, oa2, -18, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: destination index -18 out of bounds for object array[5]");
        }

        try {
            System.arraycopy(oa1, 2, oa2, 0, -19);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: length -19 is negative");
        }

        try {
            System.arraycopy(oa1, 8, oa2, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last source index 13 out of bounds for object array[10]");
        }

        try {
            System.arraycopy(oa1, 1, oa2, 0, 7);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last destination index 7 out of bounds for object array[5]");
        }

        // Test all five possible messages of arraycopy exceptions thrown in TypeArrayKlass::copy_array().

        try {
            System.arraycopy(da2, -17, da3, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: source index -17 out of bounds for double[10]");
        }

        try {
            System.arraycopy(da2, 2, da3, -18, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: destination index -18 out of bounds for double[5]");
        }

        try {
            System.arraycopy(da2, 2, da3, 0, -19);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: length -19 is negative");
        }

        try {
            System.arraycopy(da2, 8, da3, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last source index 13 out of bounds for double[10]");
        }

        try {
            System.arraycopy(da2, 1, da3, 0, 7);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last destination index 7 out of bounds for double[5]");
        }

        // Test all possible basic types in the messages of arraycopy exceptions thrown in TypeArrayKlass::copy_array().

        try {
            System.arraycopy(za2, -17, za3, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: source index -17 out of bounds for boolean[10]");
        }

        try {
            System.arraycopy(ba2, 2, ba3, -18, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: destination index -18 out of bounds for byte[5]");
        }

        try {
            System.arraycopy(sa2, 2, sa3, 0, -19);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: length -19 is negative");
        }

        try {
            System.arraycopy(ca2, 8, ca3, 0, 5);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last source index 13 out of bounds for char[10]");
        }

        try {
            System.arraycopy(ia2, 2, ia3, 0, -19);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: length -19 is negative");
        }

        try {
            System.arraycopy(la2, 1, la3, 0, 7);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last destination index 7 out of bounds for long[5]");
        }

        try {
            System.arraycopy(fa2, 1, fa3, 0, 7);
            fail();
        } catch (ArrayIndexOutOfBoundsException e) {
            assertEquals(e.getMessage(),
                "arraycopy: last destination index 7 out of bounds for float[5]");
        }
    }
}
