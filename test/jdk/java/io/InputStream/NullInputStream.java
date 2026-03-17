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

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 4358774 6516099 8139206
 * @run junit NullInputStream
 * @summary Check for expected behavior of InputStream.nullInputStream().
 */
public class NullInputStream {
    private static InputStream openStream;
    private static InputStream closedStream;

    @BeforeAll
    public static void setup() {
        openStream = InputStream.nullInputStream();
        closedStream = InputStream.nullInputStream();
        assertDoesNotThrow(() -> closedStream.close());
    }

    @AfterAll
    public static void closeStream() {
        assertDoesNotThrow(() -> openStream.close());
    }

    @Test
    public void testOpen() {
        assertNotNull(openStream, "InputStream.nullInputStream() returned null");
    }

    @Test
    public void testAvailable() {
        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> value.set(openStream.available()));
        assertEquals(0, value.get(), "available() != 0");
    }

    @Test
    public void testRead() {
        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> value.set(openStream.read()));
        assertEquals(-1, value.get(), "read() != -1");
    }

    @Test
    public void testReadBII() {
        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> value.set(openStream.read(new byte[1], 0, 1)));
        assertEquals(-1, value.get(), "read(byte[],int,int) != -1");
    }

    @Test
    public void testReadAllBytes() {
        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> value.set(openStream.readAllBytes().length));
        assertEquals(0, value.get(), "readAllBytes().length != 0");
    }

    @Test
    public void testReadNBytes() {
        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> value.set(openStream.readNBytes(new byte[1], 0, 1)));
        assertEquals(0, value.get(), "readNBytes(byte[],int,int) != 0");
    }

    @Test
    public void testReadNBytesWithLength() {
        assertThrows(IllegalArgumentException.class,
                     () -> openStream.readNBytes(-1));

        final var value = new AtomicInteger();
        assertDoesNotThrow(() -> {
                byte[] b = openStream.readNBytes(0);
                value.set(b.length);
            });
        assertEquals(0, value.get(), "readNBytes(0, false) != 0");

        assertDoesNotThrow(() -> {
                byte[] b = openStream.readNBytes(1);
                value.set(b.length);
            });
        assertEquals(0, value.get(), "readNBytes(1, false) != 0");
    }

    @Test
    public void testSkip() {
        final var value = new AtomicLong();
        assertDoesNotThrow(() -> value.set(openStream.skip(1)));
        assertEquals(0L, value.get(), "skip() != 0");
    }

    @Test
    public void testSkipNBytes() {
        assertDoesNotThrow(() -> {
                openStream.skipNBytes(-1);
                openStream.skipNBytes(0);
            });
    }

    @Test
    public void testSkipNBytesEOF() throws IOException {
        assertThrows(EOFException.class, () -> openStream.skipNBytes(1));
    }

    @Test
    public void testTransferTo() {
        final var value = new AtomicLong();
        assertDoesNotThrow(() -> value.set(openStream.transferTo(new ByteArrayOutputStream(7))));
        assertEquals(0L, value.get());
    }

    @Test
    public void testAvailableClosed() {
        assertThrows(IOException.class, () -> closedStream.available());
    }

    @Test
    public void testReadClosed() {
        assertThrows(IOException.class, () -> closedStream.read());
    }

    @Test
    public void testReadBIIClosed() {
        assertThrows(IOException.class,
                     () ->closedStream.read(new byte[1], 0, 1));
    }

    @Test
    public void testReadAllBytesClosed() {
        assertThrows(IOException.class, () -> closedStream.readAllBytes());
    }

    @Test
    public void testReadNBytesClosed() {
        assertThrows(IOException.class, () ->
            closedStream.readNBytes(new byte[1], 0, 1));
    }

    @Test
    public void testReadNBytesWithLengthClosed() {
        assertThrows(IOException.class, () -> closedStream.readNBytes(1));
    }

    @Test
    public void testSkipClosed() {
        assertThrows(IOException.class, () -> closedStream.skip(1));
    }

    @Test
    public void testSkipNBytesClosed() {
        assertThrows(IOException.class, () -> closedStream.skipNBytes(1));
    }

    @Test
    public void testTransferToClosed() {
        assertThrows(IOException.class,
            () -> closedStream.transferTo(new ByteArrayOutputStream(7)));
    }
}
