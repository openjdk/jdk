/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test compress expand methods
 * @key randomness
 * @run testng CompressExpand
 */


import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.random.RandomGenerator;

public class CompressExpand {

    public static int compress(int i, int mask) {
        int result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos++;
            }
            i >>>= 1;
            mask >>>= 1;
        }
        return result;
    }

    public static int expand(int i, int mask) {
        int result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos;
                i >>>= 1;
            }
            rpos++;
            mask >>>= 1;
        }
        return result;
    }

    public static long compress(long i, long mask) {
        long result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos++;
            }
            i >>>= 1;
            mask >>>= 1;
        }
        return result;
    }

    public static long expand(long i, long mask) {
        long result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos;
                i >>>= 1;
            }
            rpos++;
            mask >>>= 1;
        }
        return result;
    }

    static int SIZE = 1024;

    @Test
    public void testCompressInt() {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = rg.ints(SIZE).toArray();

        for (int i : values) {
            for (int m : masks) {
                int actual = Integer.compress(i, m);
                int expected = compress(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testExpandInt() {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = rg.ints(SIZE).toArray();

        for (int i : values) {
            for (int m : masks) {
                int actual = Integer.expand(i, m);
                int expected = expand(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testCompressExpandInt() {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = rg.ints(SIZE).toArray();

        for (int i : values) {
            for (int m : masks) {
                {
                    int a = Integer.compress(Integer.expand(i, m), m);
                    Assert.assertEquals(a, normalizeCompressedValue(i, m));

                    int b = Integer.compress(Integer.expand(i, ~m), ~m);
                    Assert.assertEquals(b, normalizeCompressedValue(i, ~m));
                }

                {
                    int a = Integer.expand(Integer.compress(i, m), m);
                    // Clear unset mask bits
                    Assert.assertEquals(a, i & m);

                    int b = Integer.expand(Integer.compress(i, ~m), ~m);
                    Assert.assertEquals(a & b, 0);
                    Assert.assertEquals(a | b, i);
                }
            }
        }
    }

    @Test
    public void testCompressLong() {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = rg.longs(SIZE).toArray();

        for (long i : values) {
            for (long m : masks) {
                long actual = Long.compress(i, m);
                long expected = compress(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testExpandLong() {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = rg.longs(SIZE).toArray();

        for (long i : values) {
            for (long m : masks) {
                long actual = Long.expand(i, m);
                long expected = expand(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test
    public void testCompressExpandLong() {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = rg.longs(SIZE).toArray();

        for (long i : values) {
            for (long m : masks) {
                {
                    long a = Long.compress(Long.expand(i, m), m);
                    Assert.assertEquals(a, normalizeCompressedValue(i, m));

                    long b = Long.compress(Long.expand(i, ~m), ~m);
                    Assert.assertEquals(b, normalizeCompressedValue(i, ~m));
                }

                {
                    long a = Long.expand(Long.compress(i, m), m);
                    // Clear unset mask bits
                    Assert.assertEquals(a, i & m);

                    long b = Long.expand(Long.compress(i, ~m), ~m);
                    Assert.assertEquals(a & b, 0);
                    Assert.assertEquals(a | b, i);
                }
            }
        }
    }


    static int normalizeCompressedValue(int i, int mask) {
        int mbc = Integer.bitCount(mask);
        if (mbc != 32) {
            return i & ((1 << mbc) - 1);
        }
        else {
            return i;
        }
    }

    static long normalizeCompressedValue(long i, long mask) {
        int mbc = Long.bitCount(mask);
        if (mbc != 64) {
            return i & ((1L << mbc) - 1);
        }
        else {
            return i;
        }
    }

    static void print(int i, int m, int actual, int expected) {
        System.out.println(String.format("i = %s", Integer.toBinaryString(i)));
        System.out.println(String.format("m = %s", Integer.toBinaryString(m)));
        System.out.println(String.format("a = %s", Integer.toBinaryString(actual)));
        System.out.println(String.format("e = %s", Integer.toBinaryString(expected)));
    }

    static void print(long i, long m, long actual, long expected) {
        System.out.println(String.format("i = %s", Long.toBinaryString(i)));
        System.out.println(String.format("m = %s", Long.toBinaryString(m)));
        System.out.println(String.format("a = %s", Long.toBinaryString(actual)));
        System.out.println(String.format("e = %s", Long.toBinaryString(expected)));
    }
}
