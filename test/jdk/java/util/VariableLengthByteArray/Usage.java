/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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

package java.io;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

public class Usage {

    @Test
    public void testWriteSingleByte() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        out.write(65); // ASCII 'A'
        assertArrayEquals(new byte[] { 65 }, out.toByteArray());
    }

    @Test
    public void testWriteManySingleBytes() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        final int numPrimitiveWrites = 100000;
        for (int i = 0; i < numPrimitiveWrites; i++) {
            out.write(65); // ASCII 'A'
            assertEquals(i + 1, out.toByteArray().length);
        }
        assertEquals(numPrimitiveWrites, out.size());
    }

    @Test
    public void testWriteByteArray() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 }; // ASCII 'ABC'
        out.write(data, 0, data.length);
        assertArrayEquals(data, out.toByteArray());
    }

    @Test
    public void testWritePartialByteArray() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67, 68, 69 }; // ASCII 'ABCDE'
        out.write(data, 1, 3); // Write 'BCD'
        assertArrayEquals(new byte[] { 66, 67, 68 }, out.toByteArray());
    }

    @Test
    public void testSize() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 };
        out.write(data, 0, data.length);
        assertEquals(3, out.size());
    }

    @Test
    public void testReset() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        out.write(65);
        out.reset();
        assertEquals(0, out.size());
        assertArrayEquals(new byte[] {}, out.toByteArray());
    }

    @Test
    public void testToString() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = "Hello, World!".getBytes();
        out.write(data, 0, data.length);
        assertEquals("Hello, World!", out.toString());
    }

    @Test(expected = IllegalStateException.class)
    public void testHugeToByteArray() {
        ByteArrayOutputStream out = populateHugeOutputStream();
        out.toByteArray();

    }

    @Test
    public void testToStringWithCharset() throws IOException {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = "Hello, World!".getBytes("UTF-8");
        out.write(data, 0, data.length);
        assertEquals("Hello, World!", out.toString("UTF-8"));
    }

    @Test
    public void testWriteToAnotherStream() throws IOException {
        ByteArrayOutputStream out1 = ByteArrayOutputStream.unsynchronized();
        ByteArrayOutputStream out2 = ByteArrayOutputStream.unsynchronized();

        byte[] data = { 65, 66, 67 };
        out1.write(data, 0, data.length);
        out1.writeTo(out2);

        assertArrayEquals(data, out2.toByteArray());
    }

    @Test
    public void testGrowCapacity() {
        ByteArrayOutputStream out = new ByteArrayOutputStream(2);
        byte[] data = new byte[1000];
        Arrays.fill(data, (byte) 65);
        out.write(data, 0, data.length);
        assertEquals(1000, out.size());
        assertArrayEquals(data, out.toByteArray());
    }

    @Test(expected = NullPointerException.class)
    public void testWriteNullByteArray() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        out.write(null, 0, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testWriteNegativeOffset() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 };
        out.write(data, -1, 1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testWriteNegativeLength() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 };
        out.write(data, 0, -1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void testWriteExceedingLength() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 };
        out.write(data, 0, 4);
    }

    @Test
    public void testMultipleWritesRequiringGrowth() {
        ByteArrayOutputStream out = populateMultiSegmentOutputStream();
        assertEquals(30000, out.size());
    }

    private ByteArrayOutputStream populateMultiSegmentOutputStream() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67, 68, 69, 70 };
        for (int i = 0; i < 5000; i++) {
            out.write(data, 0, 6);
            assertEquals(i * 6 + 6, out.size());
        }
        return out;
    }

    @Test
    public void testMultiSegmentWriteTo() throws IOException {
        ByteArrayOutputStream out = populateMultiSegmentOutputStream();
        ByteArrayOutputStream baseOut = new ByteArrayOutputStream();
        out.writeTo(baseOut);
        assertArrayEquals(out.toByteArray(), baseOut.toByteArray());
    }

    @Test
    public void testEmptyStream() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        assertEquals(0, out.size());
        assertArrayEquals(new byte[] {}, out.toByteArray());
        assertEquals("", out.toString());
    }

    @Test
    public void testMultipleWrites() throws IOException {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        out.write(65);
        out.write(new byte[] { 66, 67 });
        out.write(68);
        assertArrayEquals(new byte[] { 65, 66, 67, 68 }, out.toByteArray());
    }

    @Test
    public void testWriteAfterReset() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        out.write(65);
        out.reset();
        out.write(66);
        assertArrayEquals(new byte[] { 66 }, out.toByteArray());
    }

    @Test
    public void testWriteByteArrayOfMultipleSegments() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        byte[] data = { 65, 66, 67 }; // ASCII 'ABC'
        out.write(data, 0, data.length);
        assertArrayEquals(data, out.toByteArray());
    }

    private ByteArrayOutputStream populateHugeOutputStream() {
        ByteArrayOutputStream out = ByteArrayOutputStream.unsynchronized();
        long totalSize = 0;
        byte[] data = { 65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75 };
        while (totalSize < Integer.MAX_VALUE) {
            out.write(data, 0, data.length);
            totalSize += data.length;
        }
        return out;
    }

    @Test(expected = IllegalStateException.class)
    public void testHugeSize() {
        ByteArrayOutputStream out = populateHugeOutputStream();
        out.size();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testInvalidInitialSize() {
        ByteArrayOutputStream.unsynchronized(-2);
    }

    @Test
    public void testInitialSizeBelowEnforcedMinimum() {
        ByteArrayOutputStream.unsynchronized(2);
    }
}
