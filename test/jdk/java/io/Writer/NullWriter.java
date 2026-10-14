/*
 * Copyright (c) 2018, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.Writer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 8196298
 * @run junit NullWriter
 * @summary Check for expected behavior of Writer.nullWriter().
 */
public class NullWriter {
    private static Writer openWriter;
    private static Writer closedWriter;

    @BeforeAll
    public static void setup() throws IOException {
        openWriter = Writer.nullWriter();
        closedWriter = Writer.nullWriter();
        closedWriter.close();
    }

    @AfterAll
    public static void closeStream() throws IOException {
        openWriter.close();
    }

    @Test
    public void testOpen() {
        assertNotNull(openWriter, "Writer.nullWriter() returned null");
    }

    @Test
    public void testAppendChar() throws IOException {
        assertSame(openWriter, openWriter.append('x'));
    }

    @Test
    public void testAppendCharSequence() throws IOException {
        CharSequence cs = "abc";
        assertSame(openWriter, openWriter.append(cs));
    }

    @Test
    public void testAppendCharSequenceNull() throws IOException {
        assertSame(openWriter, openWriter.append(null));
    }

    @Test
    public void testAppendCharSequenceII() throws IOException {
        CharSequence cs = "abc";
        assertSame(openWriter, openWriter.append(cs, 0, 1));
    }

    @Test
    public void testAppendCharSequenceIINull() throws IOException {
        assertSame(openWriter, openWriter.append(null, 2, 1));
    }

    @Test
    public void testFlush() throws IOException {
        openWriter.flush();
    }

    @Test
    public void testWrite() throws IOException {
        openWriter.write(62832);
    }

    @Test
    public void testWriteString() throws IOException {
        openWriter.write("");
    }

    @Test
    public void testWriteStringII() throws IOException {
        openWriter.write("", 0, 0);
    }

    @Test
    public void testWriteBII() throws IOException, Exception {
        openWriter.write(new char[]{(char) 6}, 0, 1);
    }

    @Test
    public void testAppendCharClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.append('x'));
    }

    @Test
    public void testAppendCharSequenceClosed() throws IOException {
        CharSequence cs = "abc";
        assertThrows(IOException.class, () -> closedWriter.append(cs));
    }

    @Test
    public void testAppendCharSequenceNullClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.append(null));
    }

    @Test
    public void testAppendCharSequenceIIClosed() throws IOException {
        CharSequence cs = "abc";
        assertThrows(IOException.class, () -> closedWriter.append(cs, 0, 1));
    }

    @Test
    public void testAppendCharSequenceIINullClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.append(null, 2, 1));
    }

    @Test
    public void testFlushClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.flush());
    }

    @Test
    public void testWriteClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.write(62832));
    }

    @Test
    public void testWriteStringClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.write(""));
    }

    @Test
    public void testWriteStringIIClosed() throws IOException {
        assertThrows(IOException.class, () -> closedWriter.write("", 0, 0));
    }

    @Test
    public void testWriteBIIClosed() throws IOException {
        assertThrows(IOException.class,
                     () -> closedWriter.write(new char[]{(char) 6}, 0, 1));
    }
}
