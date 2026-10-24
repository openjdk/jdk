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
 * @bug 4926314 8287003
 * @summary Test for InputStreamReader#read(CharBuffer).
 * @run junit ReadCharBuffer
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.util.Arrays;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ReadCharBuffer {

    private static final int BUFFER_SIZE = 24;

    public static Stream<CharBuffer> buffers() {
        // test both on-heap and off-heap buffers as they make use different code paths
        return Stream.of(CharBuffer.allocate(BUFFER_SIZE),
                         ByteBuffer.allocateDirect(BUFFER_SIZE * 2).asCharBuffer());
    }

    private void fillBuffer(CharBuffer buffer) {
        char[] filler = new char[BUFFER_SIZE];
        Arrays.fill(filler, 'x');
        buffer.put(filler);
        buffer.clear();
    }

    @ParameterizedTest
    @MethodSource("buffers")
    public void read(CharBuffer buffer) throws IOException {
        fillBuffer(buffer);

        try (Reader reader = new InputStreamReader(new ByteArrayInputStream("ABCDEFGHIJKLMNOPQRTUVWXYZ".getBytes(US_ASCII)), US_ASCII)) {
            buffer.limit(7);
            buffer.position(1);
            assertEquals(reader.read(buffer), 6);
            assertEquals(buffer.position(), 7);
            assertEquals(buffer.limit(), 7);

            buffer.limit(16);
            buffer.position(8);
            assertEquals(reader.read(buffer), 8);
            assertEquals(buffer.position(), 16);
            assertEquals(buffer.limit(), 16);
        }

        buffer.clear();
        assertEquals(buffer.toString(), "xABCDEFxGHIJKLMNxxxxxxxx");
    }

    @Test
    public void readLeftover() throws IOException {
        byte[] b = new byte[] {'a', 'b', (byte) 0xC2};
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        InputStreamReader r = new InputStreamReader(bais,
            UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE));
        int n = r.read();
        assertEquals((char)n, 'a');
        char[] c = new char[3];
        n = r.read(c, 0, 3);
        assertEquals(n, 1);
        assertEquals((char)c[0], 'b');
        n = r.read();
        assertEquals(n, -1);
    }
}
