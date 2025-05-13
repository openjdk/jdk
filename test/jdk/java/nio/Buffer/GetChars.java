/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;

/**
 * @test
 * @bug 8343110
 * @summary Check for expected behavior of CharBuffer.getChars().
 * @run testng GetChars
 * @key randomness
 */
public class GetChars {
    private static CharBuffer CB = CharBuffer.wrap("Test");

    @Test
    public void testExactCopy() {
        var dst = new char[4];
        CB.getChars(0, 4, dst, 0);
        Assert.assertEquals(dst, new char[] {'T', 'e', 's', 't'});
    }

    @Test
    public void testPartialCopy() {
        var dst = new char[2];
        CB.getChars(1, 3, dst, 0);
        Assert.assertEquals(dst, new char[] {'e', 's'});
    }

    @Test
    public void testPositionedCopy() {
        var dst = new char[] {1, 2, 3, 4, 5, 6};
        CB.getChars(0, 4, dst, 1);
        Assert.assertEquals(dst, new char[] {1, 'T', 'e', 's', 't', 6});
    }

    @Test
    public void testSrcBeginIsNegative() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(-1, 3, new char[4], 0));
    }

    @Test
    public void testDstBeginIsNegative() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 4, new char[4], -1));
    }

    @Test
    public void testSrcBeginIsGreaterThanSrcEnd() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(4, 0, new char[4], 0));
    }

    @Test
    public void testSrcEndIsGreaterThanSequenceLength() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 5, new char[4], 0));
    }

    @Test
    public void testRequestedLengthIsGreaterThanDstLength() {
        Assert.assertThrows(IndexOutOfBoundsException.class,
                () -> CB.getChars(0, 4, new char[3], 0));
    }

    @Test
    public void testDstIsNull() {
        Assert.assertThrows(NullPointerException.class,
                () -> CB.getChars(0, 4, null, 0));
    }

    private static final Random RAND = new Random();
    private static final int SIZE = 128 + RAND.nextInt(1024);

    /**
     * Randomize the char buffer's position and limit.
     */
    private static CharBuffer randomizeRange(CharBuffer cb) {
        int mid = cb.capacity() >>> 1;
        int start = RAND.nextInt(mid + 1); // from 0 to mid
        int end = mid + RAND.nextInt(cb.capacity() - mid + 1); // from mid to capacity
        cb.position(start);
        cb.limit(end);
        return cb;
    }

    /**
     * Randomize the char buffer's contents, position and limit.
     */
    private static CharBuffer randomize(CharBuffer cb) {
        while (cb.hasRemaining()) {
            cb.put((char)RAND.nextInt());
        }
        return randomizeRange(cb);
    }

    /**
     * Sums the remaining chars in the char buffer.
     */
    private static int intSum(CharBuffer cb) {
        int sum = 0;
        cb.mark();
        while (cb.hasRemaining()) {
            sum += cb.get();
        }
        cb.reset();
        return sum;
    }

    /**
     * Sums the chars in the char array.
     */
    private static int intSum(char[] ca) {
        int sum = 0;
        for (int i = 0; i < ca.length; i++)
            sum += ca[i];
        return sum;
    }

    /**
     * Creates char buffers to test, adding them to the given list.
     */
    private static void addCases(CharBuffer cb, List<CharBuffer> buffers) {
        randomize(cb);
        buffers.add(cb);

        buffers.add(cb.slice());
        buffers.add(cb.duplicate());
        buffers.add(cb.asReadOnlyBuffer());

        buffers.add(randomizeRange(cb.slice()));
        buffers.add(randomizeRange(cb.duplicate()));
        buffers.add(randomizeRange(cb.asReadOnlyBuffer()));
    }

    @DataProvider(name = "charbuffers")
    public Object[][] createCharBuffers() {
        List<CharBuffer> buffers = new ArrayList<>();

        // heap
        addCases(CharBuffer.allocate(SIZE), buffers);
        addCases(CharBuffer.wrap(new char[SIZE]), buffers);
        addCases(ByteBuffer.allocate(SIZE*2).order(ByteOrder.BIG_ENDIAN).asCharBuffer(),
                buffers);
        addCases(ByteBuffer.allocate(SIZE*2).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer(),
                buffers);

        // direct
        addCases(ByteBuffer.allocateDirect(SIZE*2).order(ByteOrder.BIG_ENDIAN).asCharBuffer(),
                buffers);
        addCases(ByteBuffer.allocateDirect(SIZE*2).order(ByteOrder.LITTLE_ENDIAN).asCharBuffer(),
                buffers);

        // read-only buffer backed by a CharSequence
        buffers.add(CharBuffer.wrap(randomize(CharBuffer.allocate(SIZE))));

        Object[][] params = new Object[buffers.size()][];
        for (int i = 0; i < buffers.size(); i++) {
            CharBuffer cb = buffers.get(i);
            params[i] = new Object[] { cb.getClass().getName(), cb };
        }

        return params;
    }

    @Test(dataProvider = "charbuffers")
    public void testGetChars(String type, CharBuffer cb) {
        System.out.format("%s position=%d, limit=%d%n", type, cb.position(), cb.limit());
        int expected = intSum(cb);
        var dst = new char[cb.remaining()];
        cb.getChars(cb.position(), cb.limit(), dst, 0);
        int actual = intSum(dst);
        assertEquals(actual, expected);
    }
}
