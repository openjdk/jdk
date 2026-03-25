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
import java.io.OutputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/*
 * @test
 * @bug 4358774
 * @run junit NullOutputStream
 * @summary Check for expected behavior of OutputStream.nullOutputStream().
 */
public class NullOutputStream {
    private static OutputStream openStream;
    private static OutputStream closedStream;

    @BeforeAll
    public static void setup() {
        openStream = OutputStream.nullOutputStream();
        closedStream = OutputStream.nullOutputStream();
        assertDoesNotThrow(() -> closedStream.close());
    }

    @AfterAll
    public static void closeStream() {
        assertDoesNotThrow(() -> openStream.close());
    }

    @Test
    public void testOpen() {
        assertNotNull(openStream,
            "OutputStream.nullOutputStream() returned null");
    }

    @Test
    public void testWrite() throws IOException {
        openStream.write(62832);
    }

    @Test
    public void testWriteBII() {
        assertDoesNotThrow(() -> openStream.write(new byte[] {(byte)6}, 0, 1));
    }

    @Test
    public void testWriteClosed() {
        assertThrows(IOException.class, () -> closedStream.write(62832));
    }

    @Test
    public void testWriteBIIClosed() {
        assertThrows(IOException.class,
                     () -> closedStream.write(new byte[] {(byte)6}, 0, 1));
    }
}
