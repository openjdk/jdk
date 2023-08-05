/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 8292043
 * @run testng StatefulDecoderNearEOF
 * @summary Check MalformedInputException is thrown with stateful decoders
 *      with malformed input before EOF
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

@Test
public class StatefulDecoderNearEOF {

    @DataProvider
    public Object[][] inputs() {
        return new Object[][] {
            // BOM, followed by High surrogate (in UTF-16LE).
            // First read() should throw an exception.
            {new byte[] {(byte)0xff, (byte)0xfe, 0, (byte)0xd8}, 0},

            // BOM, followed by 'A', 'B', 'C', then by High surrogate (in UTF-16LE).
            // Fourth read() should throw an exception.
            {new byte[] {(byte)0xff, (byte)0xfe, (byte)0x41, 0, (byte)0x42, 0, (byte)0x43, 0, 0, (byte)0xd8}, 3},
        };
    }

    @Test (dataProvider = "inputs")
    public void testStatefulDecoderNearEOF(byte[] ba, int numSucessReads) throws IOException {
        try (var r = new InputStreamReader(
                new ByteArrayInputStream(ba),
                StandardCharsets.UTF_16.newDecoder().onMalformedInput(CodingErrorAction.REPORT))) {
            // Issue read() as many as numSucessReads which should not fail
            IntStream.rangeClosed(1, numSucessReads).forEach(i -> {
                try {
                    assertEquals(r.read(), (int)ba[i * 2]);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });

            // Final dangling high surrogate should throw an exception
            assertThrows(MalformedInputException.class, () -> r.read());
        }
    }
}
