/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4017158 8180410
 * @library /test/lib
 * @build jdk.test.lib.RandomFactory
 * @run junit Write
 * @summary Check for correct implementation of ByteArrayInputStream.write
 * @key randomness
 */

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Random;
import jdk.test.lib.RandomFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Write {
    private static void doBoundsTest(byte[] b, int off, int len,
                                     ByteArrayOutputStream baos)
        throws Exception {
        if (b != null) {
            System.err.println("ByteArrayOutStream.write: b.length = " +
                               b.length + " off = " + off + " len = " + len);
        } else{
            System.err.println("ByteArrayOutStream.write: b is null off = " +
                               off + " len = " + len);
        }

        Class expectedException = (b == null)
            ? NullPointerException.class : IndexOutOfBoundsException.class;
        assertThrows(expectedException, () -> baos.write(b, off, len));

        if (b != null) {
            System.err.println("ByteArrayOutStream.writeBytes: b.length = " +
                               b.length);
        } else{
            System.err.println("ByteArrayOutStream.writeBytes: b is null");
            assertThrows(NullPointerException.class, () -> baos.writeBytes(b));
        }
    }

    @Test
    public void boundsTest() throws Exception {
        byte array1[] = {1 , 2 , 3 , 4 , 5};     // Simple array

        //Create new ByteArrayOutputStream object
        ByteArrayOutputStream y1 = new ByteArrayOutputStream(5);

        doBoundsTest(array1, 0, Integer.MAX_VALUE , y1);
        doBoundsTest(array1, 0, array1.length+100, y1);
        doBoundsTest(array1, -1, 2, y1);
        doBoundsTest(array1, 0, -1, y1);
        doBoundsTest(null, 0, 2, y1);
    }

    @Test
    public void writeTest() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Random rnd = RandomFactory.getRandom();
        final int size = 17 + rnd.nextInt(128);

        byte[] b = new byte[size];
        rnd.nextBytes(b);

        int off1 = rnd.nextInt(size / 4) + 1;
        int len1 = Math.min(rnd.nextInt(size / 4) + 1, size - off1);
        int off2 = rnd.nextInt(size / 2) + 1;
        int len2 = Math.min(rnd.nextInt(size / 2) + 1, size - off2);

        System.err.format("size: %d, off1: %d, len1: %d, off2: %d, len2: %d%n",
            size, off1, len1, off2, len2);

        baos.write(b, off1, len1);
        byte[] b1 = baos.toByteArray();
        assertEquals(len1, b1.length, "Array length test 1 failed.");
        assertArrayEquals(Arrays.copyOfRange(b, off1, off1 + len1), b1,
            "Array equality test 1 failed.");

        baos.write(b, off2, len2);
        byte[] b2 = baos.toByteArray();
        assertEquals(len1 + len2, b2.length, "Array length test 2 failed.");
        assertArrayEquals(Arrays.copyOfRange(b, off1, off1 + len1),
            Arrays.copyOfRange(b2, 0, len1),
            "Array equality test 2A failed.");
        assertArrayEquals(Arrays.copyOfRange(b, off2, off2 + len2),
            Arrays.copyOfRange(b2, len1, len1 + len2),
            "Array equality test 2B failed.");

        baos.writeBytes(b);
        byte[] b3 = baos.toByteArray();
        int len3 = len1 + len2 + b.length;
        assertEquals(len3, b3.length, "Array length test 3 failed.");
        assertArrayEquals(Arrays.copyOfRange(b, off1, off1 + len1),
            Arrays.copyOfRange(b3, 0, len1),
            "Array equality test 3A failed.");
        assertArrayEquals(Arrays.copyOfRange(b, off2, off2 + len2),
            Arrays.copyOfRange(b3, len1, len1 + len2),
            "Array equality test 3B failed.");
        assertArrayEquals(b, Arrays.copyOfRange(b3, len1 + len2, len3),
            "Array equality test 3C failed.");
    }
}
