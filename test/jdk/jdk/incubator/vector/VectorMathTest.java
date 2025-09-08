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

/*
 * @test
 * @bug 8338021
 * @summary Test unsigned and saturating scalar operators for use with Vector API
 * @modules jdk.incubator.vector
 * @run testng VectorMathTest
 */

import jdk.incubator.vector.VectorMath;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Array;
import java.util.function.BinaryOperator;

public class VectorMathTest {
    // @formatter:off
    public static final byte  ZERO_B = (byte)0;
    public static final short ZERO_S = (short)0;
    public static final int   ZERO_I = 0;
    public static final long  ZERO_L = 0L;

    public static final byte  TEN_B = (byte)10;
    public static final int   TEN_S = (short)10;
    public static final short TEN_I = 10;
    public static final long  TEN_L = 10L;

    public static final byte  FIFTY_B = (byte)50;
    public static final int   FIFTY_S = (short)50;
    public static final short FIFTY_I = 50;
    public static final long  FIFTY_L = 50L;

    public static final byte  SIXTY_B = (byte)60;
    public static final int   SIXTY_S = (short)60;
    public static final short SIXTY_I = 60;
    public static final long  SIXTY_L = 60L;

    public static final byte  UMAX_B = (byte)-1;
    public static final short UMAX_S = (short)-1;
    public static final int   UMAX_I = -1;
    public static final long  UMAX_L = -1L;

    public static byte[]  INPUT_SB = {Byte.MIN_VALUE,    (byte)(Byte.MIN_VALUE + TEN_B),   ZERO_B, (byte)(Byte.MAX_VALUE - TEN_B),   Byte.MAX_VALUE};
    public static short[] INPUT_SS = {Short.MIN_VALUE,   (short)(Short.MIN_VALUE + TEN_S), ZERO_S, (short)(Short.MAX_VALUE - TEN_S), Short.MAX_VALUE};
    public static int[]   INPUT_SI = {Integer.MIN_VALUE, (Integer.MIN_VALUE + TEN_I),      ZERO_I, Integer.MAX_VALUE - TEN_I,        Integer.MAX_VALUE};
    public static long[]  INPUT_SL = {Long.MIN_VALUE,    Long.MIN_VALUE + TEN_L,           ZERO_L, Long.MAX_VALUE - TEN_L,           Long.MAX_VALUE};

    public static int[]   INPUT_SADD_I    = {-FIFTY_I,          -FIFTY_I,          -FIFTY_I, FIFTY_I,           FIFTY_I};
    public static byte[]  EXPECTED_SADD_B = {Byte.MIN_VALUE,    Byte.MIN_VALUE,    -FIFTY_B, Byte.MAX_VALUE,    Byte.MAX_VALUE};
    public static short[] EXPECTED_SADD_S = {Short.MIN_VALUE,   Short.MIN_VALUE,   -FIFTY_S, Short.MAX_VALUE,   Short.MAX_VALUE};
    public static int[]   EXPECTED_SADD_I = {Integer.MIN_VALUE, Integer.MIN_VALUE, -FIFTY_I, Integer.MAX_VALUE, Integer.MAX_VALUE};
    public static long[]  EXPECTED_SADD_L = {Long.MIN_VALUE,    Long.MIN_VALUE,    -FIFTY_L, Long.MAX_VALUE,    Long.MAX_VALUE};

    public static int[]   INPUT_SSUB_I    = {FIFTY_I,           FIFTY_I,            FIFTY_I, -FIFTY_I,          -FIFTY_I};
    public static byte[]  EXPECTED_SSUB_B = {Byte.MIN_VALUE,    Byte.MIN_VALUE,    -FIFTY_B, Byte.MAX_VALUE,    Byte.MAX_VALUE};
    public static short[] EXPECTED_SSUB_S = {Short.MIN_VALUE,   Short.MIN_VALUE,   -FIFTY_S, Short.MAX_VALUE,   Short.MAX_VALUE};
    public static int[]   EXPECTED_SSUB_I = {Integer.MIN_VALUE, Integer.MIN_VALUE, -FIFTY_I, Integer.MAX_VALUE, Integer.MAX_VALUE};
    public static long[]  EXPECTED_SSUB_L = {Long.MIN_VALUE,    Long.MIN_VALUE,    -FIFTY_L, Long.MAX_VALUE,    Long.MAX_VALUE};

    public static byte[]  INPUT_UB = {ZERO_B, (byte)(ZERO_B + TEN_B),  (byte)(UMAX_B - TEN_B),  UMAX_B};
    public static short[] INPUT_US = {ZERO_S, (short)(ZERO_S + TEN_S), (short)(UMAX_S - TEN_S), UMAX_S};
    public static int[]   INPUT_UI = {ZERO_I, ZERO_I + TEN_I,          UMAX_I - TEN_I,          UMAX_I};
    public static long[]  INPUT_UL = {ZERO_L, ZERO_L + TEN_L,          UMAX_L - TEN_L,          UMAX_L};

    public static int[]   INPUT_SUADD_I    = {FIFTY_I, FIFTY_I, FIFTY_I, FIFTY_I};
    public static byte[]  EXPECTED_SUADD_B = {FIFTY_B, SIXTY_B, UMAX_B,  UMAX_B};
    public static short[] EXPECTED_SUADD_S = {FIFTY_S, SIXTY_S, UMAX_S,  UMAX_S};
    public static int[]   EXPECTED_SUADD_I = {FIFTY_I, SIXTY_I, UMAX_I,  UMAX_I};
    public static long[]  EXPECTED_SUADD_L = {FIFTY_L, SIXTY_L, UMAX_L,  UMAX_L};

    public static int[]   INPUT_SUSUB_I    = {FIFTY_I, FIFTY_I, FIFTY_I,           FIFTY_I};
    public static byte[]  EXPECTED_SUSUB_B = {ZERO_B,  ZERO_B,  UMAX_B - SIXTY_B,  UMAX_B - FIFTY_B};
    public static short[] EXPECTED_SUSUB_S = {ZERO_S,  ZERO_S,  UMAX_S - SIXTY_S,  UMAX_S - FIFTY_S};
    public static int[]   EXPECTED_SUSUB_I = {ZERO_I,  ZERO_I,  UMAX_I - SIXTY_I,  UMAX_I - FIFTY_I};
    public static long[]  EXPECTED_SUSUB_L = {ZERO_L,  ZERO_L,  UMAX_L - SIXTY_L,  UMAX_L - FIFTY_L};

    public static byte[]  EXPECTED_UMIN_B = {ZERO_B, TEN_B, ZERO_B, Byte.MAX_VALUE - TEN_B};
    public static short[] EXPECTED_UMIN_S = {ZERO_S, TEN_S, ZERO_S, Short.MAX_VALUE - TEN_S};
    public static int[]   EXPECTED_UMIN_I = {ZERO_I, TEN_I, ZERO_I, Integer.MAX_VALUE - TEN_I};
    public static long[]  EXPECTED_UMIN_L = {ZERO_L, TEN_L, ZERO_L, Long.MAX_VALUE - TEN_L};

    public static byte[]  EXPECTED_UMAX_B = {Byte.MIN_VALUE,    (byte)(Byte.MIN_VALUE + TEN_B),   (byte)(UMAX_B - TEN_B),  UMAX_B};
    public static short[] EXPECTED_UMAX_S = {Short.MIN_VALUE,   (short)(Short.MIN_VALUE + TEN_S), (short)(UMAX_S - TEN_S), UMAX_S};
    public static int[]   EXPECTED_UMAX_I = {Integer.MIN_VALUE, Integer.MIN_VALUE + TEN_I,        (UMAX_I - TEN_I),        UMAX_I};
    public static long[]  EXPECTED_UMAX_L = {Long.MIN_VALUE,    Long.MIN_VALUE + TEN_L,           (UMAX_L - TEN_L),        UMAX_L};
    // @formatter:on

    static Object conv(Object a, Class<?> ct) {
        Object na = Array.newInstance(ct, Array.getLength(a));
        for (int i = 0; i < Array.getLength(a); i++) {
            Number number = (Number) Array.get(a, i);
            if (ct == byte.class) {
                number = number.byteValue();
            } else if (ct == short.class) {
                number = number.shortValue();
            } else if (ct == int.class) {
                number = number.intValue();
            } else if (ct == long.class) {
                number = number.longValue();
            } else {
                assert false : "should not reach here";
            }
            Array.set(na, i, number);
        }
        return na;
    }

    static <T> BinaryOperator<T> named(String name, BinaryOperator<T> op) {
        return new BinaryOperator<T>() {
            @Override
            public T apply(T a, T b) {
                return op.apply(a, b);
            }

            public String toString() {
                return name;
            }
        };
    }

    static final BinaryOperator<Object> OP_UMIN = named("minUnsigned",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.minUnsigned((byte) a, (byte) b);
                case Short _ -> VectorMath.minUnsigned((short) a, (short) b);
                case Integer _ -> VectorMath.minUnsigned((int) a, (int) b);
                case Long _ -> VectorMath.minUnsigned((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    static final BinaryOperator<Object> OP_UMAX = named("maxUnsigned",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.maxUnsigned((byte) a, (byte) b);
                case Short _ -> VectorMath.maxUnsigned((short) a, (short) b);
                case Integer _ -> VectorMath.maxUnsigned((int) a, (int) b);
                case Long _ -> VectorMath.maxUnsigned((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    static final BinaryOperator<Object> OP_SADD = named("addSaturating",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.addSaturating((byte) a, (byte) b);
                case Short _ -> VectorMath.addSaturating((short) a, (short) b);
                case Integer _ -> VectorMath.addSaturating((int) a, (int) b);
                case Long _ -> VectorMath.addSaturating((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    static final BinaryOperator<Object> OP_SSUB = named("subSaturating",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.subSaturating((byte) a, (byte) b);
                case Short _ -> VectorMath.subSaturating((short) a, (short) b);
                case Integer _ -> VectorMath.subSaturating((int) a, (int) b);
                case Long _ -> VectorMath.subSaturating((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    static final BinaryOperator<Object> OP_SUADD = named("addSaturatingUnsigned",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.addSaturatingUnsigned((byte) a, (byte) b);
                case Short _ -> VectorMath.addSaturatingUnsigned((short) a, (short) b);
                case Integer _ -> VectorMath.addSaturatingUnsigned((int) a, (int) b);
                case Long _ -> VectorMath.addSaturatingUnsigned((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    static final BinaryOperator<Object> OP_SUSUB = named("subSaturatingUnsigned",
            (a, b) -> switch (a) {
                case Byte _ -> VectorMath.subSaturatingUnsigned((byte) a, (byte) b);
                case Short _ -> VectorMath.subSaturatingUnsigned((short) a, (short) b);
                case Integer _ -> VectorMath.subSaturatingUnsigned((int) a, (int) b);
                case Long _ -> VectorMath.subSaturatingUnsigned((long) a, (long) b);
                default -> throw new UnsupportedOperationException("should not reach here");
            });

    @DataProvider
    public static Object[][] opProvider() {
        return new Object[][] {
                {OP_UMIN, byte.class, INPUT_UB, INPUT_SB, EXPECTED_UMIN_B, },
                {OP_UMIN, short.class, INPUT_US, INPUT_SS, EXPECTED_UMIN_S, },
                {OP_UMIN, int.class, INPUT_UI, INPUT_SI, EXPECTED_UMIN_I, },
                {OP_UMIN, long.class, INPUT_UL, INPUT_SL, EXPECTED_UMIN_L, },

                {OP_UMAX, byte.class, INPUT_UB, INPUT_SB, EXPECTED_UMAX_B, },
                {OP_UMAX, short.class, INPUT_US, INPUT_SS, EXPECTED_UMAX_S, },
                {OP_UMAX, int.class, INPUT_UI, INPUT_SI, EXPECTED_UMAX_I, },
                {OP_UMAX, long.class, INPUT_UL, INPUT_SL, EXPECTED_UMAX_L, },

                {OP_SADD, byte.class, INPUT_SB, conv(INPUT_SADD_I, byte.class), EXPECTED_SADD_B, },
                {OP_SADD, short.class, INPUT_SS, conv(INPUT_SADD_I, short.class), EXPECTED_SADD_S, },
                {OP_SADD, int.class, INPUT_SI, INPUT_SADD_I, EXPECTED_SADD_I, },
                {OP_SADD, long.class, INPUT_SL, conv(INPUT_SADD_I, long.class), EXPECTED_SADD_L, },

                {OP_SSUB, byte.class, INPUT_SB, conv(INPUT_SSUB_I, byte.class), EXPECTED_SSUB_B, },
                {OP_SSUB, short.class, INPUT_SS, conv(INPUT_SSUB_I, short.class), EXPECTED_SSUB_S, },
                {OP_SSUB, int.class, INPUT_SI, INPUT_SSUB_I, EXPECTED_SSUB_I, },
                {OP_SSUB, long.class, INPUT_SL, conv(INPUT_SSUB_I, long.class), EXPECTED_SSUB_L, },

                {OP_SUADD, byte.class, INPUT_UB, conv(INPUT_SUADD_I, byte.class), EXPECTED_SUADD_B, },
                {OP_SUADD, short.class, INPUT_US, conv(INPUT_SUADD_I, short.class), EXPECTED_SUADD_S, },
                {OP_SUADD, int.class, INPUT_UI, INPUT_SUADD_I, EXPECTED_SUADD_I, },
                {OP_SUADD, long.class, INPUT_UL, conv(INPUT_SUADD_I, long.class), EXPECTED_SUADD_L, },

                {OP_SUSUB, byte.class, INPUT_UB, conv(INPUT_SUSUB_I, byte.class), EXPECTED_SUSUB_B, },
                {OP_SUSUB, short.class, INPUT_US, conv(INPUT_SUSUB_I, short.class), EXPECTED_SUSUB_S, },
                {OP_SUSUB, int.class, INPUT_UI, INPUT_SUSUB_I, EXPECTED_SUSUB_I, },
                {OP_SUSUB, long.class, INPUT_UL, conv(INPUT_SUSUB_I, long.class), EXPECTED_SUSUB_L, },
        };
    }

    @Test(dataProvider = "opProvider")
    public void test(BinaryOperator<Object> op, Class<?> type, Object a, Object b, Object expected) {
        assert Array.getLength(a) <= Array.getLength(b) && Array.getLength(a) <= Array.getLength(expected);

        Object actual = Array.newInstance(type, Array.getLength(a));
        for (int i = 0; i < Array.getLength(a); i++) {
            Object e = op.apply(Array.get(a, i), Array.get(b, i));
            Array.set(actual, i, e);
        }
        Assert.assertEquals(actual, expected);
    }
}
