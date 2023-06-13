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

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.function.Supplier;
import java.util.random.RandomGenerator;

public abstract class AbstractCompressExpandTest {

    static int testCompress(int i, int mask) {
        int result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos;
                rpos++; // conditional increment
            }
            i >>>= 1; // unconditional shift-out
            mask >>>= 1;
        }
        return result;
    }

    static int testExpand(int i, int mask) {
        int result = 0;
        int rpos = 0;
        while (mask != 0) {
            if ((mask & 1) != 0) {
                result |= (i & 1) << rpos;
                i >>>= 1; // conditional shift-out
            }
            rpos++; // unconditional increment
            mask >>>= 1;
        }
        return result;
    }

    static long testCompress(long i, long mask) {
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

    static long testExpand(long i, long mask) {
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

    abstract int actualCompress(int i, int mask);

    abstract int actualExpand(int i, int mask);

    abstract int expectedCompress(int i, int mask);

    abstract int expectedExpand(int i, int mask);

    abstract long actualCompress(long i, long mask);

    abstract long actualExpand(long i, long mask);

    abstract long expectedCompress(long i, long mask);

    abstract long expectedExpand(long i, long mask);

    static int SIZE = 1024;

    <T> Supplier<T> supplierWithToString(Supplier<T> s, String name) {
        return new Supplier<>() {
            @Override
            public T get() {
                return s.get();
            }

            @Override
            public String toString() {
                return name;
            }
        };
    }

    @DataProvider
    Object[][] maskIntProvider() {
        RandomGenerator rg = RandomGenerator.getDefault();

        return new Object[][]{
                {supplierWithToString(() -> rg.ints(SIZE).toArray(), "random masks")},
                {supplierWithToString(this::contiguousMasksInt, "contiguous masks")}
        };
    }

    @DataProvider
    Object[][] maskLongProvider() {
        RandomGenerator rg = RandomGenerator.getDefault();

        return new Object[][]{
                {supplierWithToString(() -> rg.longs(SIZE).toArray(), "random masks")},
                {supplierWithToString(this::contiguousMasksLong, "contiguous masks")}
        };
    }

    int[] contiguousMasksInt() {
        int size = 32 * (32 + 1) / 2 + 1; // 528 + 1
        int[] masks = new int[size];

        int i = 0;
        masks[i++] = 0;
        for (int len = 1; len < 32; len++) {
            for (int pos = 0; pos <= 32 - len; pos++) {
                masks[i++] = ((1 << len) - 1) << pos;
            }
        }
        masks[i++] = -1;

        assert i == masks.length;
        return masks;
    }

    long[] contiguousMasksLong() {
        int size = 64 * (64 + 1) / 2 + 1; // 2080 + 1
        long[] masks = new long[size];


        int i = 0;
        masks[i++] = 0L;
        for (int len = 1; len < 64; len++) {
            for (int pos = 0; pos <= 64 - len; pos++) {
                masks[i++] = ((1L << len) - 1) << pos;
            }
        }
        masks[i++] = -1L;

        assert i == masks.length;
        return masks;
    }


    @Test(dataProvider = "maskIntProvider")
    public void testCompressInt(Supplier<int[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = maskProvider.get();

        for (int i : values) {
            for (int m : masks) {
                int actual = actualCompress(i, m);
                int expected = expectedCompress(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test(dataProvider = "maskIntProvider")
    public void testExpandInt(Supplier<int[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = maskProvider.get();

        for (int i : values) {
            for (int m : masks) {
                int actual = actualExpand(i, m);
                int expected = expectedExpand(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test(dataProvider = "maskIntProvider")
    public void testCompressExpandInt(Supplier<int[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();
        int[] masks = maskProvider.get();

        for (int i : values) {
            for (int m : masks) {
                {
                    int a = actualCompress(actualExpand(i, m), m);
                    Assert.assertEquals(a, normalizeCompressedValue(i, m));

                    int b = actualCompress(actualExpand(i, ~m), ~m);
                    Assert.assertEquals(b, normalizeCompressedValue(i, ~m));
                }

                {
                    int a = actualExpand(actualCompress(i, m), m);
                    // Clear unset mask bits
                    Assert.assertEquals(a, i & m);

                    int b = actualExpand(actualCompress(i, ~m), ~m);
                    Assert.assertEquals(a & b, 0);
                    Assert.assertEquals(a | b, i);
                }
            }
        }
    }

    @Test
    public void testContiguousMasksInt() {
        RandomGenerator rg = RandomGenerator.getDefault();

        int[] values = rg.ints(SIZE).toArray();

        for (int i : values) {
            assertContiguousMask(i, 0, 0L);
            for (int len = 1; len < 32; len++) {
                for (int pos = 0; pos <= 32 - len; pos++) {
                    int mask = ((1 << len) - 1) << pos;

                    assertContiguousMask(i, pos, mask);
                }
            }
            assertContiguousMask(i, 0, -1L);
        }
    }

    void assertContiguousMask(int i, int pos, int mask) {
        Assert.assertEquals(actualCompress(i, mask), (i & mask) >>> pos);
        Assert.assertEquals(actualExpand(i, mask), (i << pos) & mask);
    }

    @Test(dataProvider = "maskLongProvider")
    public void testCompressLong(Supplier<long[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = maskProvider.get();

        for (long i : values) {
            for (long m : masks) {
                long actual = actualCompress(i, m);
                long expected = expectedCompress(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test(dataProvider = "maskLongProvider")
    public void testExpandLong(Supplier<long[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = maskProvider.get();

        for (long i : values) {
            for (long m : masks) {
                long actual = actualExpand(i, m);
                long expected = expectedExpand(i, m);
                if (actual != expected) {
                    print(i, m, actual, expected);
                }
                Assert.assertEquals(actual, expected);
            }
        }
    }

    @Test(dataProvider = "maskLongProvider")
    public void testCompressExpandLong(Supplier<long[]> maskProvider) {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();
        long[] masks = maskProvider.get();

        for (long i : values) {
            for (long m : masks) {
                {
                    long a = actualCompress(actualExpand(i, m), m);
                    Assert.assertEquals(a, normalizeCompressedValue(i, m));

                    long b = actualCompress(actualExpand(i, ~m), ~m);
                    Assert.assertEquals(b, normalizeCompressedValue(i, ~m));
                }

                {
                    long a = actualExpand(actualCompress(i, m), m);
                    // Clear unset mask bits
                    Assert.assertEquals(a, i & m);

                    long b = actualExpand(actualCompress(i, ~m), ~m);
                    Assert.assertEquals(a & b, 0);
                    Assert.assertEquals(a | b, i);
                }
            }
        }
    }

    @Test
    public void testContiguousMasksLong() {
        RandomGenerator rg = RandomGenerator.getDefault();

        long[] values = rg.longs(SIZE).toArray();

        for (long i : values) {
            assertContiguousMask(i, 0, 0L);
            for (int len = 1; len < 64; len++) {
                for (int pos = 0; pos <= 64 - len; pos++) {
                    long mask = ((1L << len) - 1) << pos;

                    assertContiguousMask(i, pos, mask);
                }
            }
            assertContiguousMask(i, 0, -1L);
        }
    }

    void assertContiguousMask(long i, int pos, long mask) {
        Assert.assertEquals(actualCompress(i, mask), (i & mask) >>> pos);
        Assert.assertEquals(actualExpand(i, mask), (i << pos) & mask);
    }

    static int normalizeCompressedValue(int i, int mask) {
        int mbc = Integer.bitCount(mask);
        if (mbc != 32) {
            return i & ((1 << mbc) - 1);
        } else {
            return i;
        }
    }

    static long normalizeCompressedValue(long i, long mask) {
        int mbc = Long.bitCount(mask);
        if (mbc != 64) {
            return i & ((1L << mbc) - 1);
        } else {
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
