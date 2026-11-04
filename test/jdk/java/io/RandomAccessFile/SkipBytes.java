/*
 * Copyright (c) 1997, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import static jdk.test.lib.Utils.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/*
 * @test
 * @bug 4027717 8396040
 * @summary Check for correct implementation of RandomAccessFile.skipBytes
 * @library /test/lib
 * @run junit ${test.main.class}
 */

class SkipBytes {

    static List<Arguments> args() throws IOException {
        List<Arguments> args = new ArrayList<>();
        for (long size : List.of(0L, 1L, 100L)) {

            // Start positions
            long[] positions = {
                    0,
                    1,
                    2,
                    size - 1,
                    size,
                    size + 1,
                    Integer.MAX_VALUE - 1,
                    Integer.MAX_VALUE
            };

            // Skip lengths
            long[] skips = {
                    Integer.MIN_VALUE,
                    Integer.MIN_VALUE + 1L,
                    -size - 1,
                    -size,
                    -size + 1,
                    -2,
                    -1,
                    0,
                    1,
                    2,
                    size - 1,
                    size,
                    size + 1,
                    Integer.MAX_VALUE - 1L,
                    Integer.MAX_VALUE
            };

            // Generate test cases
            for (long position : positions) {
                if (position >= 0) {
                    for (long skip : skips) {
                        if (skip <= Integer.MAX_VALUE) {
                            args.add(Arguments.of(size, position, (int) skip));
                        }
                    }
                }
            }

        }
        return args;
    }

    @ParameterizedTest
    @MethodSource("args")
    void test(long size, long initialPosition, int requestedSkip) throws IOException {
        var path = createTempFileOfSize("RAF-SkipBytes-%s-".formatted(size), ".dat", size);
        try (var raf = new RandomAccessFile(path.toFile(), "rw")) {

            // Seek to the requested start position
            raf.seek(initialPosition);
            long position0 = raf.getFilePointer();
            assertEquals(initialPosition, position0);

            // Skip requested bytes
            int skip = raf.skipBytes(requestedSkip);
            final int expectedSkip;
            if (requestedSkip <= 0 || position0 >= size) {
                expectedSkip = 0;
            } else if (size - position0 > requestedSkip) {
                expectedSkip = requestedSkip;
            } else {
                expectedSkip = Math.toIntExact(size - position0);
            }
            assertEquals(expectedSkip, skip);

            // Verify the new position
            long position1 = raf.getFilePointer();
            final long expectedPosition1;
            if (position0 >= size || requestedSkip <= 0) {
                expectedPosition1 = position0;
            } else if (size - position0 > requestedSkip) {
                expectedPosition1 = position0 + requestedSkip;
            } else {
                expectedPosition1 = size;
            }
            assertEquals(expectedPosition1, position1);

            // Verify the read
            int read = raf.read();
            if (position1 >= size) {
                assertEquals(-1, read, "Expected EOF");
            } else {
                assertNotEquals(-1, read, "Expected a byte");
            }

        }
    }

}
