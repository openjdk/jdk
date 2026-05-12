/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8341566
 * @summary Check for expected behavior of Reader.of().
 * @run junit Of
 */
public class Of {
    final static String CONTENT = "Some Reader Test";

    /*
     * Readers to be tested.
     */
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

    @ParameterizedTest
    @MethodSource("readers")
    public void testRead(Reader reader) throws IOException {
        String s = "";
        for (int c; (c = reader.read()) != -1; s += (char) c);
        assertEquals(CONTENT, s, "read() returned wrong value");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadBII(Reader reader) throws IOException {
        char[] c = new char[16];
        assertEquals(8, reader.read(c, 8, 8),
                "read(char[],int,int) does not respect given start or end");
        assertEquals(8, reader.read(c, 0, 16),
                "read(char[],int,int) does not respect end of stream");
        assertEquals(CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                new String(c),
                "read(char[],int,int) provides wrong content");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadBIILenZero(Reader reader) throws IOException {
        assertEquals(0, reader.read(new char[1], 0, 0),
                "read(char[],int,int) != 0");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadDirectCharBuffer(Reader reader) throws IOException {
        CharBuffer charBuffer = ByteBuffer.allocateDirect(32).asCharBuffer();
        charBuffer.position(8);
        assertEquals(8, reader.read(charBuffer),
                "read(CharBuffer) does not respect position or limit");
        charBuffer.rewind();
        assertEquals(8, reader.read(charBuffer),
                "read(CharBuffer) does not respect end of stream");
        charBuffer.rewind();
        // last part first proves that copy loops correctly stopped
        assertEquals(CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                charBuffer.toString(),
                "read(CharBuffer) provides wrong content");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadNonDirectCharBuffer(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(16);
        charBuffer.position(8);
        assertEquals(8, reader.read(charBuffer),
                "read(CharBuffer) does not respect position or limit");
        charBuffer.rewind();
        assertEquals(8, reader.read(charBuffer),
                "read(CharBuffer) does not respect end of stream");
        charBuffer.rewind();
        assertEquals(CONTENT.substring(8, 16) + CONTENT.substring(0, 8),
                charBuffer.toString(),
                "read(CharBuffer) provides wrong content");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadCharBufferZeroRemaining(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(0);
        assertEquals(0, reader.read(charBuffer), "read(CharBuffer) != 0");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReady(Reader reader) throws IOException {
        assertTrue(reader.ready());
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testSkip(Reader reader) throws IOException {
        assertEquals(8, reader.skip(8), "skip() does not respect limit");
        assertEquals(8, reader.skip(9), "skip() does not respect end of stream");
        assertEquals(0, reader.skip(1), "skip() does not respect empty stream");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testTransferTo(Reader reader) throws IOException {
        StringWriter sw = new StringWriter(16);
        assertEquals(16, reader.transferTo(sw), "transferTo() != 16");
        assertEquals(0, reader.transferTo(sw),
                "transferTo() does not respect empty stream");
        assertEquals(CONTENT, sw.toString(),
                "transferTo() provides wrong content");
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> {reader.read();});
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadBIIClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.read(new char[1], 0, 1));
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadCharBufferClosed(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(1);
        reader.close();
        assertThrows(IOException.class, () -> reader.read(charBuffer));
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadCharBufferZeroRemainingClosed(Reader reader) throws IOException {
        CharBuffer charBuffer = CharBuffer.allocate(0);
        reader.close();
        assertThrows(IOException.class, () -> reader.read(charBuffer));
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testReadyClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.ready());
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testSkipClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.skip(1));
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testTransferToClosed(Reader reader) throws IOException {
        reader.close();
        assertThrows(IOException.class, () -> reader.transferTo(new StringWriter(1)));
    }

    @ParameterizedTest
    @MethodSource("readers")
    public void testCloseClosed(Reader reader) throws IOException {
        reader.close();
        reader.close();
    }
}
