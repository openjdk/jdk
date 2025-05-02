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

import java.io.Reader;
import java.io.StringReader;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.ReadOnlyBufferException;

import org.testng.annotations.*;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8341566
 * @summary Check for expected behavior of Reader.of().
 * @run testng Of
 */
public class Of {
    final static String CONTENT = "Some Reader Test";

    /*
     * Readers to be tested.
     */
    @DataProvider
    public static Reader[] readers() {
        return new Reader[] {
            new StringReader(CONTENT),
            Reader.of(CONTENT),
            Reader.of(new StringBuffer(CONTENT)),
            Reader.of(new StringBuilder(CONTENT)),
            Reader.of(ByteBuffer.allocateDirect(CONTENT.length() * 2)
                    .asCharBuffer().put(CONTENT).flip()),
            Reader.of(CharBuffer.wrap(CONTENT.toCharArray())),
            Reader.of(new CharSequence() {
                @Override
                public char charAt(int index) {
                    return CONTENT.charAt(index);
                }

                @Override
                public int length() {
                    return CONTENT.length();
                }

                @Override
                public CharSequence subSequence(int start, int end) {
                    // unused by Reader.Of's result
                    throw new UnsupportedOperationException();
                }

                @Override
                public String toString() {
                    // Reader.Of's result SHALL NOT convert to String
                    throw new UnsupportedOperationException();
                }
            })
        };
    }

    @Test(dataProvider = "readers")
    public void testRead(Reader reader) throws IOException {
        String s = "";
        for (int c; (c = reader.read()) != -1; s += (char) c);
        assertEquals(s, CONTENT, "read() returned wrong value");
    }

    @Test(dataProvider = "readers")
    public void testReadBII(Reader reader) throws IOException {
        char[] c = new char[16];
        assertEquals(reader.read(c, 8, 8), 8,
                "read(char[],int,int) does not respect given start or end");
        assertEquals(reader.read(c, 0, 16), 8,
                "read(char[],int,int) does not respect end of stream");
        assertEquals(new String(c),
                CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                "read(char[],int,int) provides wrong content");
    }

    @Test(dataProvider = "readers")
    public void testReadBIILenZero(Reader reader) throws IOException {
        assertEquals(reader.read(new char[1], 0, 0), 0,
                "read(char[],int,int) != 0");
    }

    @Test(dataProvider = "readers")
    public void testReadDirectCharBuffer(Reader reader) throws IOException {
        CharBuffer charBuffer = ByteBuffer.allocateDirect(32).asCharBuffer();
        charBuffer.position(8);
        assertEquals(reader.read(charBuffer), 8,
                "read(CharBuffer) does not respect position or limit");
        charBuffer.rewind();
        assertEquals(reader.read(charBuffer), 8,
                "read(CharBuffer) does not respect end of stream");
        charBuffer.rewind();
        assertEquals(charBuffer.toString(),
                // last part first proofs that copy loops correctly stopped
                CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                "read(CharBuffer) provides wrong content");
    }

    @Test(dataProvider = "readers")
    public void testReadNonDirectCharBuffer(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(16);
        charBuffer.position(8);
        assertEquals(reader.read(charBuffer), 8,
                "read(CharBuffer) does not respect position or limit");
        charBuffer.rewind();
        assertEquals(reader.read(charBuffer), 8,
                "read(CharBuffer) does not respect end of stream");
        charBuffer.rewind();
        assertEquals(charBuffer.toString(),
                CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                "read(CharBuffer) provides wrong content");
    }

    @Test(dataProvider = "readers")
    public void testReadCharBufferZeroRemaining(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(0);
        assertEquals(reader.read(charBuffer), 0, "read(CharBuffer) != 0");
    }

    @Test(dataProvider = "readers")
    public void testReady(Reader reader) throws IOException {
        assertTrue(reader.ready());
    }

    @Test(dataProvider = "readers")
    public void testSkip(Reader reader) throws IOException {
        assertEquals(reader.skip(8), 8, "skip() does not respect limit");
        assertEquals(reader.skip(9), 8, "skip() does not respect end of stream");
        assertEquals(reader.skip(1), 0, "skip() does not respect empty stream");
    }

    @Test(dataProvider = "readers")
    public void testTransferTo(Reader reader) throws IOException {
        StringWriter sw = new StringWriter(16);
        assertEquals(reader.transferTo(sw), 16, "transferTo() != 16");
        assertEquals(reader.transferTo(sw), 0,
                "transferTo() does not respect empty stream");
        assertEquals(sw.toString(), CONTENT,
                "transferTo() provides wrong content");
    }

    @Test(dataProvider = "readers")
    public void testReadClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> {reader.read();});
    }

    @Test(dataProvider = "readers")
    public void testReadBIIClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.read(new char[1], 0, 1));
    }

    @Test(dataProvider = "readers")
    public void testReadCharBufferClosed(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(1);
        reader.close();
        assertThrows(IOException.class, () -> reader.read(charBuffer));
    }

    @Test(dataProvider = "readers")
    public void testReadCharBufferZeroRemainingClosed(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(0);
        reader.close();
        assertThrows(IOException.class, () -> reader.read(charBuffer));
    }

    @Test(dataProvider = "readers")
    public void testReadyClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.ready());
    }

    @Test(dataProvider = "readers")
    public void testSkipClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.skip(1));
    }

    @Test(dataProvider = "readers")
    public void testTransferToClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.transferTo(new StringWriter(1)));
    }

    @Test(dataProvider = "readers")
    public void testCloseClosed(Reader reader) throws IOException {
        reader.close();
        reader.close();
    }
}
