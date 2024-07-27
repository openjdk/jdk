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

/* @test
 * @bug 8322256
 * @summary Test decompression of streams created by the gzip(1) command
 * @run junit GZIPInputStreamGzipCommand
 */

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.*;
import java.util.zip.*;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class GZIPInputStreamGzipCommand {

    public static Stream<String[]> testScenarios() throws IOException {
        final ArrayList<String[]> scenarios = new ArrayList();

        /*
            (
                for i in 1 2 3 4 5 6 7 8 9; do
                    printf 'this is compression level #%d\n' "$i" | gzip -"${i}"
                done
            ) | hexdump -e '32/1 "%02x" "\n"'
        */
        scenarios.add(new String[] { """
            this is compression level #1
            this is compression level #2
            this is compression level #3
            this is compression level #4
            this is compression level #5
            this is compression level #6
            this is compression level #7
            this is compression level #8
            this is compression level #9
            """, """
            1f8b0800d42ea46604032bc9c82c5600a2e4fcdc82a2d4e2e2ccfc3c859cd4b2
            d41c0565432e0092bb84691d0000001f8b0800d42ea46600032bc9c82c5600a2
            e4fcdc82a2d4e2e2ccfc3c859cd4b2d41c0565232e0051e8a9421d0000001f8b
            0800d42ea46600032bc9c82c5600a2e4fcdc82a2d4e2e2ccfc3c859cd4b2d41c
            0565632e0010d9b25b1d0000001f8b0800d42ea46600032bc9c82c5600a2e4fc
            dc82a2d4e2e2ccfc3c859cd4b2d41c0565132e00d74ff3141d0000001f8b0800
            d42ea46600032bc9c82c5600a2e4fcdc82a2d4e2e2ccfc3c859cd4b2d41c0565
            532e00967ee80d1d0000001f8b0800d42ea46600032bc9c82c5600a2e4fcdc82
            a2d4e2e2ccfc3c859cd4b2d41c0565332e00552dc5261d0000001f8b0800d42e
            a46600032bc9c82c5600a2e4fcdc82a2d4e2e2ccfc3c859cd4b2d41c0565732e
            00141cde3f1d0000001f8b0800d42ea46600032bc9c82c5600a2e4fcdc82a2d4
            e2e2ccfc3c859cd4b2d41c05650b2e00db0046b81d0000001f8b0800d42ea466
            02032bc9c82c5600a2e4fcdc82a2d4e2e2ccfc3c859cd4b2d41c05654b2e009a
            315da11d000000""" });

        /*
            (
                printf 'this one has a name\n' > file1 && gzip file1
                printf 'this one has no name\n' > file2 && gzip --no-name file2
                cat file1.gz file2.gz
                rm file1.gz file2.gz
            ) | hexdump -e '32/1 "%02x" "\n"'
        */
        scenarios.add(new String[] { """
            this one has a name
            this one has no name
            """, """
            1f8b08082230a466000366696c6531002bc9c82c56c8cf4b55c8482c564854c8
            4bcc4de50200d7ccdc5a140000001f8b08000000000000032bc9c82c56c8cf4b
            55c8482c56c8cb57c84bcc4de50200b1effb5015000000""" });

        /*
            (
                i=0
                while [ "${i}" -lt 1000 ]; do
                    printf 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
                    i=`expr "${i}" + 1`
                done
            ) | gzip --best | hexdump -e '32/1 "%02x" "\n"'
        */
        scenarios.add(new String[] {
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".repeat(1000),
            """
            1f8b08002a35a4660203edc18100000000c320d6f94b1ce45501000000000000
            0000000000000000000000000000000000000000000000000000000000000000
            c08f01492d182728a00000""" });

        return scenarios.stream();
    }

    @ParameterizedTest
    @MethodSource("testScenarios")
    public void testScenario(String input, String hexData) throws IOException {

        // Get expected result
        final byte[] expected = input.getBytes(StandardCharsets.UTF_8);

        // Get actual result
        final HexFormat hexFormat = HexFormat.of();
        final byte[] data = hexFormat.parseHex(hexData.replaceAll("\\s", ""));
        final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (GZIPInputStream gunzip = new GZIPInputStream(new ByteArrayInputStream(data))) {
            gunzip.transferTo(buf);
        }
        final byte[] actual = buf.toByteArray();

        // Compare
        System.out.println("  ACTUAL: " + hexFormat.formatHex(actual));
        System.out.println("EXPECTED: " + hexFormat.formatHex(expected));
        assertArrayEquals(actual, expected);
    }
}
