/*
 * Copyright (c) 2021, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4926314
 * @summary Test for CharArrayReader#read(CharBuffer).
 * @run junit ReadCharBuffer
 */

import java.io.CharArrayReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.Arrays;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadCharBuffer {

    private static final int BUFFER_SIZE = 7;

    public static CharBuffer[] buffers() {
        // test both on-heap and off-heap buffers as they may use different code paths
        return new CharBuffer[] {
            CharBuffer.allocate(BUFFER_SIZE),
            ByteBuffer.allocateDirect(BUFFER_SIZE * 2).asCharBuffer()
        };
    }

    @ParameterizedTest
    @MethodSource("buffers")
    public void read(CharBuffer buffer) throws IOException {
        fillBuffer(buffer);

        try (Reader reader = new CharArrayReader("ABCD".toCharArray())) {
            buffer.limit(3);
            buffer.position(1);
            assertEquals(2, reader.read(buffer));
            assertEquals(3, buffer.position());
            assertEquals(3, buffer.limit());

            buffer.limit(7);
            buffer.position(4);
            assertEquals(2, reader.read(buffer));
            assertEquals(6, buffer.position());
            assertEquals(7, buffer.limit());

            assertEquals(-1, reader.read(buffer));
        }

        buffer.clear();
        assertEquals("xABxCDx", buffer.toString());
    }

    private void fillBuffer(CharBuffer buffer) {
        char[] filler = new char[BUFFER_SIZE];
        Arrays.fill(filler, 'x');
        buffer.put(filler);
        buffer.clear();
    }

}
