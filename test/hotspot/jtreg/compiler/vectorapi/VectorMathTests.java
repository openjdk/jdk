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

package compiler.vectorapi;

import jdk.incubator.vector.VectorMath;
import java.util.stream.IntStream;

/*
 * @test
 * @bug 8338021
 * @summary Support new unsigned and saturating vector operators in VectorAPI
 * @modules jdk.incubator.vector
 * @library /test/lib
 *
 * @run main compiler.vectorapi.VectorMathTests
 */


public class VectorMathTests {
    public static final byte  ZEROB = (byte)0;
    public static final short ZEROS = (short)0;
    public static final int   ZEROI = 0;
    public static final long  ZEROL = 0L;

    public static final byte  TENB = (byte)10;
    public static final int   TENS = (short)10;
    public static final short TENI = 10;
    public static final long  TENL = 10L;

    public static final byte  FIFTYB = (byte)50;
    public static final int   FIFTYS = (short)50;
    public static final short FIFTYI = 50;
    public static final long  FIFTYL = 50L;

    public static final byte  SIXTYB = (byte)60;
    public static final int   SIXTYS = (short)60;
    public static final short SIXTYI = 60;
    public static final long  SIXTYL = 60L;

    public static final byte  UMAXB = (byte)-1;
    public static final short UMAXS = (short)-1;
    public static final int   UMAXI = -1;
    public static final long  UMAXL = -1L;

    public static byte  [] sbinput = {Byte.MIN_VALUE,    (byte)(Byte.MIN_VALUE + TENB),   ZEROB, (byte)(Byte.MAX_VALUE - TENB),   Byte.MAX_VALUE};
    public static short [] ssinput = {Short.MIN_VALUE,   (short)(Short.MIN_VALUE + TENS), ZEROS, (short)(Short.MAX_VALUE - TENS), Short.MAX_VALUE};
    public static int   [] siinput = {Integer.MIN_VALUE, (Integer.MIN_VALUE + TENI),      ZEROI, Integer.MAX_VALUE - TENI,        Integer.MAX_VALUE};
    public static long  [] slinput = {Long.MIN_VALUE,    Long.MIN_VALUE + TENL,           ZEROL, Long.MAX_VALUE - TENL,           Long.MAX_VALUE};

    public static int   [] saddended    = {-FIFTYI,           -FIFTYI,           -FIFTYI, FIFTYI,            FIFTYI};
    public static byte  [] boutput_sadd = {Byte.MIN_VALUE,    Byte.MIN_VALUE,    -FIFTYB, Byte.MAX_VALUE,    Byte.MAX_VALUE};
    public static short [] soutput_sadd = {Short.MIN_VALUE,   Short.MIN_VALUE,   -FIFTYS, Short.MAX_VALUE,   Short.MAX_VALUE};
    public static int   [] ioutput_sadd = {Integer.MIN_VALUE, Integer.MIN_VALUE, -FIFTYI, Integer.MAX_VALUE, Integer.MAX_VALUE};
    public static long  [] loutput_sadd = {Long.MIN_VALUE,    Long.MIN_VALUE,    -FIFTYL, Long.MAX_VALUE,    Long.MAX_VALUE};

    public static int   [] ssubtrahend  = {FIFTYI,            FIFTYI,             FIFTYI, -FIFTYI,           -FIFTYI};
    public static byte  [] boutput_ssub = {Byte.MIN_VALUE,    Byte.MIN_VALUE,    -FIFTYB, Byte.MAX_VALUE,    Byte.MAX_VALUE};
    public static short [] soutput_ssub = {Short.MIN_VALUE,   Short.MIN_VALUE,   -FIFTYS, Short.MAX_VALUE,   Short.MAX_VALUE};
    public static int   [] ioutput_ssub = {Integer.MIN_VALUE, Integer.MIN_VALUE, -FIFTYI, Integer.MAX_VALUE, Integer.MAX_VALUE};
    public static long  [] loutput_ssub = {Long.MIN_VALUE,    Long.MIN_VALUE,    -FIFTYL, Long.MAX_VALUE,    Long.MAX_VALUE};

    public static byte  [] ubinput = {ZEROB, (byte)(ZEROB + TENB),  (byte)(UMAXB - TENB),  UMAXB};
    public static short [] usinput = {ZEROS, (short)(ZEROS + TENS), (short)(UMAXS - TENS), UMAXS};
    public static int   [] uiinput = {ZEROI, ZEROI + TENI,          UMAXI - TENI,          UMAXI};
    public static long  [] ulinput = {ZEROL, ZEROL + TENL,          UMAXL - TENL,          UMAXL};

    public static int   [] uaddended     = {FIFTYI, FIFTYI, FIFTYI, FIFTYI};
    public static byte  [] boutput_usadd = {FIFTYB, SIXTYB, UMAXB,  UMAXB};
    public static short [] soutput_usadd = {FIFTYS, SIXTYS, UMAXS,  UMAXS};
    public static int   [] ioutput_usadd = {FIFTYI, SIXTYI, UMAXI,  UMAXI};
    public static long  [] loutput_usadd = {FIFTYL, SIXTYL, UMAXL,  UMAXL};

    public static int   [] usubtrahend   = {FIFTYI, FIFTYI, FIFTYI, FIFTYI};
    public static byte  [] boutput_ussub = {ZEROB,  ZEROB,  UMAXB - SIXTYB,  UMAXB - FIFTYB};
    public static short [] soutput_ussub = {ZEROS,  ZEROS,  UMAXS - SIXTYS,  UMAXS - FIFTYS};
    public static int   [] ioutput_ussub = {ZEROI,  ZEROI,  UMAXI - SIXTYI,  UMAXI - FIFTYI};
    public static long  [] loutput_ussub = {ZEROL,  ZEROL,  UMAXL - SIXTYL,  UMAXL - FIFTYL};

    public static byte  [] boutput_umin = {ZEROB, TENB, ZEROB, Byte.MAX_VALUE - TENB};
    public static short [] soutput_umin = {ZEROS, TENS, ZEROS, Short.MAX_VALUE - TENS};
    public static int   [] ioutput_umin = {ZEROI, TENI, ZEROI, Integer.MAX_VALUE - TENI};
    public static long  [] loutput_umin = {ZEROL, TENL, ZEROL, Long.MAX_VALUE - TENL};

    public static byte  [] boutput_umax = {Byte.MIN_VALUE,    (byte)(Byte.MIN_VALUE + TENB),   (byte)(UMAXB - TENB),  UMAXB};
    public static short [] soutput_umax = {Short.MIN_VALUE,   (short)(Short.MIN_VALUE + TENS), (short)(UMAXS - TENS), UMAXS};
    public static int   [] ioutput_umax = {Integer.MIN_VALUE, Integer.MIN_VALUE + TENI,        (UMAXI - TENI),        UMAXI};
    public static long  [] loutput_umax = {Long.MIN_VALUE,    Long.MIN_VALUE + TENL,           (UMAXL - TENL),        UMAXL};

    public static void test_saturated_add(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, sbinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturating(sbinput[i], (byte)saddended[i]),
                                                                         boutput_sadd[i],
                                                                         "[addSaturating byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, ssinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturating(ssinput[i], (short)saddended[i]),
                                                                         soutput_sadd[i],
                                                                         "[addSaturating short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, siinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturating(siinput[i], saddended[i]),
                                                                         ioutput_sadd[i],
                                                                         "[addSaturating int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, slinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturating(slinput[i], (long)saddended[i]),
                                                                         loutput_sadd[i],
                                                                         "[addSaturating long] idx = " + i + " : "));
        }
    }

    public static void test_saturated_sub(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, sbinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturating(sbinput[i], (byte)ssubtrahend[i]),
                                                                         boutput_ssub[i],
                                                                         "[subSaturating byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, ssinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturating(ssinput[i], (short)ssubtrahend[i]),
                                                                         soutput_ssub[i],
                                                                         "[subSaturating short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, siinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturating(siinput[i], ssubtrahend[i]),
                                                                         ioutput_ssub[i],
                                                                         "[subSaturating int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, slinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturating(slinput[i], (long)ssubtrahend[i]),
                                                                         loutput_ssub[i],
                                                                         "[subSaturating long] idx = " + i + " : "));
        }
    }

    public static void test_saturated_uadd(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, ubinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturatingUnsigned(ubinput[i], (byte)uaddended[i]),
                                                                                 boutput_usadd[i],
                                                                                 "[addSaturatingUnsigned byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, usinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturatingUnsigned(usinput[i], (short)uaddended[i]),
                                                                                 soutput_usadd[i],
                                                                                 "[addSaturatingUnsigned short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, uiinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturatingUnsigned(uiinput[i], uaddended[i]),
                                                                                 ioutput_usadd[i],
                                                                                 "[addSaturatingUnsigned int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, ulinput.length)
                     .forEach(i -> assertEquals(VectorMath.addSaturatingUnsigned(ulinput[i], (long)uaddended[i]),
                                                                                 loutput_usadd[i],
                                                                                 "[addSaturatingUnsigned long] idx = " + i + " : "));
        }
    }

    public static void test_saturated_usub(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, ubinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturatingUnsigned(ubinput[i], (byte)usubtrahend[i]),
                                                                                 boutput_ussub[i],
                                                                                 "[subSaturatingUnsigned byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, usinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturatingUnsigned(usinput[i], (short)usubtrahend[i]),
                                                                                 soutput_ussub[i],
                                                                                 "[subSaturatingUnsigned short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, uiinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturatingUnsigned(uiinput[i], usubtrahend[i]),
                                                                                 ioutput_ussub[i],
                                                                                 "[subSaturatingUnsigned int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, ulinput.length)
                     .forEach(i -> assertEquals(VectorMath.subSaturatingUnsigned(ulinput[i], (long)usubtrahend[i]),
                                                                                 loutput_ussub[i],
                                                                                 "[subSaturatingUnsigned long] idx = " + i + " : "));
        }
    }

    public static void test_umin(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, ubinput.length)
                     .forEach(i -> assertEquals(VectorMath.minUnsigned(ubinput[i], sbinput[i]), boutput_umin[i], "[minUnsigned byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, usinput.length)
                     .forEach(i -> assertEquals(VectorMath.minUnsigned(usinput[i], ssinput[i]), soutput_umin[i], "[minUnsigned short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, uiinput.length)
                     .forEach(i -> assertEquals(VectorMath.minUnsigned(uiinput[i], siinput[i]), ioutput_umin[i], "[minUnsigned int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, ulinput.length)
                     .forEach(i -> assertEquals(VectorMath.minUnsigned(ulinput[i], slinput[i]), loutput_umin[i], "[minUnsigned long] idx = " + i + " : "));
        }
    }

    public static void test_umax(Class<?> type) {
        if (type == byte.class) {
            IntStream.range(0, ubinput.length)
                     .forEach(i -> assertEquals(VectorMath.maxUnsigned(ubinput[i], sbinput[i]), boutput_umax[i], "[maxUnsigned byte] idx = " + i + " : "));
        } else if (type == short.class) {
            IntStream.range(0, usinput.length)
                     .forEach(i -> assertEquals(VectorMath.maxUnsigned(usinput[i], ssinput[i]), soutput_umax[i], "[maxUnsigned short] idx = " + i + " : "));
        } else if (type == int.class) {
            IntStream.range(0, uiinput.length)
                     .forEach(i -> assertEquals(VectorMath.maxUnsigned(uiinput[i], siinput[i]), ioutput_umax[i], "[maxUnsigned int] idx = " + i + " : "));
        } else if (type == long.class) {
            IntStream.range(0, ulinput.length)
                     .forEach(i -> assertEquals(VectorMath.maxUnsigned(ulinput[i], slinput[i]), loutput_umax[i], "[maxUnsigned long] idx = " + i + " : "));
        }
    }

    public static void assertEquals(Number res, Number ref, String msg) {
        if (!res.equals(ref)) {
            throw new RuntimeException(msg +  ref + "(ref)  != " + res + "(res)");
        }
    }

    public static void main(String [] args) {
        for (int i = 0; i < 1; i++) {
            test_saturated_add(byte.class);
            test_saturated_add(short.class);
            test_saturated_add(int.class);
            test_saturated_add(long.class);

            test_saturated_sub(byte.class);
            test_saturated_sub(short.class);
            test_saturated_sub(int.class);
            test_saturated_sub(long.class);

            test_saturated_uadd(byte.class);
            test_saturated_uadd(short.class);
            test_saturated_uadd(int.class);
            test_saturated_uadd(long.class);

            test_saturated_usub(byte.class);
            test_saturated_usub(short.class);
            test_saturated_usub(int.class);
            test_saturated_usub(long.class);

            test_umin(byte.class);
            test_umin(short.class);
            test_umin(int.class);
            test_umin(long.class);

            test_umax(byte.class);
            test_umax(short.class);
            test_umax(int.class);
            test_umax(long.class);
        }
        System.out.println("PASSED");
    }
}
