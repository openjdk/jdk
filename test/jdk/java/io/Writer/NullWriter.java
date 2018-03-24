/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import org.testng.annotations.AfterGroups;
import org.testng.annotations.BeforeGroups;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

/*
 * @test
 * @bug 8196298
 * @run testng NullWriter
 * @summary Check for expected behavior of Writer.nullWriter().
 */
public class NullWriter {
    private static Writer openWriter;
    private static Writer closedWriter;

    @BeforeGroups(groups = "open")
    public static void openStream() {
        openWriter = Writer.nullWriter();
    }

    @BeforeGroups(groups = "closed")
    public static void openAndCloseStream() throws IOException {
        closedWriter = Writer.nullWriter();
        closedWriter.close();
    }

    @AfterGroups(groups = "open")
    public static void closeStream() throws IOException {
        openWriter.close();
    }

    @Test(groups = "open")
    public static void testOpen() {
        assertNotNull(openWriter, "Writer.nullWriter() returned null");
    }

    @Test(groups = "open")
    public static void testAppendChar() throws IOException {
        assertSame(openWriter, openWriter.append('x'));
    }

    @Test(groups = "open")
    public static void testAppendCharSequence() throws IOException {
        CharSequence cs = "abc";
        assertSame(openWriter, openWriter.append(cs));
    }

    @Test(groups = "open")
    public static void testAppendCharSequenceNull() throws IOException {
        assertSame(openWriter, openWriter.append(null));
    }

    @Test(groups = "open")
    public static void testAppendCharSequenceII() throws IOException {
        CharSequence cs = "abc";
        assertSame(openWriter, openWriter.append(cs, 0, 1));
    }

    @Test(groups = "open")
    public static void testAppendCharSequenceIINull() throws IOException {
        assertSame(openWriter, openWriter.append(null, 2, 1));
    }

    @Test(groups = "open")
    public static void testFlush() throws IOException {
        openWriter.flush();
    }

    @Test(groups = "open")
    public static void testWrite() throws IOException {
        openWriter.write(62832);
    }

    @Test(groups = "open")
    public static void testWriteString() throws IOException {
        openWriter.write("");
    }

    @Test(groups = "open")
    public static void testWriteStringII() throws IOException {
        openWriter.write("", 0, 0);
    }

    @Test(groups = "open")
    public static void testWriteBII() throws IOException, Exception {
        openWriter.write(new char[]{(char) 6}, 0, 1);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testAppendCharClosed() throws IOException {
        closedWriter.append('x');
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testAppendCharSequenceClosed() throws IOException {
        CharSequence cs = "abc";
        closedWriter.append(cs);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testAppendCharSequenceNullClosed() throws IOException {
        closedWriter.append(null);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testAppendCharSequenceIIClosed() throws IOException {
        CharSequence cs = "abc";
        closedWriter.append(cs, 0, 1);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testAppendCharSequenceIINullClosed() throws IOException {
        closedWriter.append(null, 2, 1);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testFlushClosed() throws IOException {
        closedWriter.flush();
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testWriteClosed() throws IOException {
        closedWriter.write(62832);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testWriteStringClosed() throws IOException {
        closedWriter.write("");
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testWriteStringIIClosed() throws IOException {
        closedWriter.write("", 0, 0);
    }

    @Test(groups = "closed", expectedExceptions = IOException.class)
    public static void testWriteBIIClosed() throws IOException {
        closedWriter.write(new char[]{(char) 6}, 0, 1);
    }
}
