/*
 * Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Random;
import jdk.internal.math.FloatingDecimal;

import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
OldFloatingDecimalForTest

public class OldFloatingDecimalForTest {
  public boolean digitsRoundedUp();
  public OldFloatingDecimalForTest(double);
  public OldFloatingDecimalForTest(float);
  public boolean decimalDigitsExact();
  public java.lang.String toString();
  public java.lang.String toJavaFormatString();
  public void appendTo(java.lang.Appendable);
  public static OldFloatingDecimalForTest readJavaFormatString(java.lang.String) throws java.lang.NumberFormatException;
  public strictfp double doubleValue();
  public strictfp float floatValue();
}

jdk.internal.math.FloatingDecimal

public class jdk.internal.math.FloatingDecimal {
  public jdk.internal.math.FloatingDecimal();
  public static java.lang.String toJavaFormatString(double);
  public static java.lang.String toJavaFormatString(float);
  public static void appendTo(double, java.lang.Appendable);
  public static void appendTo(float, java.lang.Appendable);
  public static double parseDouble(java.lang.String) throws java.lang.NumberFormatException;
  public static float parseFloat(java.lang.String) throws java.lang.NumberFormatException;
  public static jdk.internal.math.FloatingDecimal$AbstractD2ABuffer getD2ABuffer(double);
}
*/

/**
 * @test
 * @bug 7032154
 * @summary unit tests of FloatingDecimal (use -Dseed=X to set PRANDOM seed)
 * @modules java.base/jdk.internal.math
 * @library ..
 * @library /test/lib
 * @library /java/lang/Math
 * @build jdk.test.lib.RandomFactory
 * @build DoubleConsts FloatConsts
 * @run junit TestFloatingDecimal
 * @author Brian Burkhalter
 * @key randomness
 */
public class TestFloatingDecimal {
    private static final int NUM_RANDOM_TESTS = 100_000;

    private static final Random RANDOM = RandomFactory.getRandom();

    private static int check(String test, Object expected, Object actual) {
        int failures = 0;
        if(!actual.equals(expected)) {
            failures++;
            System.err.println("Test " + test +
                               " expected " + expected +
                               " but obtained " + actual);
        }
        return failures;
    }

    @Test
    public void testAppendToDouble() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            double[] d = new double[] {
                RANDOM.nextLong(),
                RANDOM.nextGaussian(),
                RANDOM.nextDouble()*Double.MAX_VALUE
            };
            for(int j = 0; j < d.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(d[j]);
                StringBuilder sb = new StringBuilder();
                ofd.appendTo(sb);
                String oldString = sb.toString();
                sb = new StringBuilder();
                FloatingDecimal.appendTo(d[j], sb);
                String newString = sb.toString();
                failures += check("testAppendToDouble", oldString, newString);
            }
        }

        assertEquals(0, failures);
    }

    @Test
    public void testAppendToFloat() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            float[] f = new float[] {
                RANDOM.nextLong(),
                (float)RANDOM.nextGaussian(),
                RANDOM.nextFloat()*Float.MAX_VALUE
            };
            for(int j = 0; j < f.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(f[j]);
                StringBuilder sb = new StringBuilder();
                ofd.appendTo(sb);
                String oldString = sb.toString();
                sb = new StringBuilder();
                FloatingDecimal.appendTo(f[j], sb);
                String newString = sb.toString();
                failures += check("testAppendToFloat", oldString, newString);
            }
        }

        assertEquals(0, failures);
    }

    @Test
    public void testParseDouble() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            double[] d = new double[] {
                RANDOM.nextLong(),
                RANDOM.nextGaussian(),
                RANDOM.nextDouble()*Double.MAX_VALUE
            };
            for(int j = 0; j < d.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(d[j]);
                String javaFormatString = ofd.toJavaFormatString();
                ofd = OldFloatingDecimalForTest.readJavaFormatString(javaFormatString);
                double oldDouble = ofd.doubleValue();
                double newDouble = FloatingDecimal.parseDouble(javaFormatString);
                failures += check("testParseDouble", oldDouble, newDouble);
            }
        }

        assertEquals(0, failures);
    }

    @Test
    public void testParseFloat() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            float[] f = new float[] {
                RANDOM.nextInt(),
                (float)RANDOM.nextGaussian(),
                RANDOM.nextFloat()*Float.MAX_VALUE
            };
            for(int j = 0; j < f.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(f[j]);
                String javaFormatString = ofd.toJavaFormatString();
                ofd = OldFloatingDecimalForTest.readJavaFormatString(javaFormatString);
                float oldFloat = ofd.floatValue();
                float newFloat = FloatingDecimal.parseFloat(javaFormatString);
                failures += check("testParseFloat", oldFloat, newFloat);
            }
        }

        assertEquals(0, failures);
    }

    @Test
    public void testToJavaFormatStringDoubleFixed() {
        int failures = 0;

        double[] d = new double [] {
            -5.9522650387500933e18, // dtoa() fast path
            0.872989018674569,      // dtoa() fast iterative - long
            1.1317400099603851e308  // dtoa() slow iterative
        };

        for(int i = 0; i < d.length; i++) {
            OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(d[i]);
            failures += check("testToJavaFormatStringDoubleFixed", ofd.toJavaFormatString(), FloatingDecimal.toJavaFormatString(d[i]));
        }

        assertEquals(0, failures);
    }

    @Test
    public void testToJavaFormatStringDoubleRandom() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            double[] d = new double[] {
                RANDOM.nextLong(),
                RANDOM.nextGaussian(),
                RANDOM.nextDouble()*Double.MAX_VALUE
            };
            for(int j = 0; j < d.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(d[j]);
                failures += check("testToJavaFormatStringDoubleRandom", ofd.toJavaFormatString(), FloatingDecimal.toJavaFormatString(d[j]));
            }
        }

        assertEquals(0, failures);
    }

    @Test
    public void testToJavaFormatStringFloatFixed() {
        int failures = 0;

        float[] f = new float[] {
            -9.8784166e8f, // dtoa() fast path
            0.70443946f,   // dtoa() fast iterative - int
            1.8254228e37f  // dtoa() slow iterative
        };

        for(int i = 0; i < f.length; i++) {
            OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(f[i]);
            failures += check("testToJavaFormatStringFloatFixed", ofd.toJavaFormatString(), FloatingDecimal.toJavaFormatString(f[i]));
        }

        assertEquals(0, failures);
    }

    @Test
    public void testToJavaFormatStringFloatRandom() {
        int failures = 0;

        for(int i = 0; i < NUM_RANDOM_TESTS; i++) {
            float[] f = new float[] {
                RANDOM.nextInt(),
                (float)RANDOM.nextGaussian(),
                RANDOM.nextFloat()*Float.MAX_VALUE
            };
            for(int j = 0; j < f.length; j++) {
                OldFloatingDecimalForTest ofd = new OldFloatingDecimalForTest(f[j]);
                failures += check("testToJavaFormatStringFloatRandom", ofd.toJavaFormatString(), FloatingDecimal.toJavaFormatString(f[j]));
            }
        }

        assertEquals(0, failures);
    }
}
