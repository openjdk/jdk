/*
 * Copyright (c) 2022, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5041655
 * @summary Verify FileLock.overlaps
 * @run junit Overlaps
 */
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.lang.Boolean.*;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Overlaps {
    private static final long POS = 27;
    private static final long SIZE = 42;

    private static FileChannel fc;

    @BeforeAll
    public static void before() throws IOException {
        Path path = Files.createTempFile(Path.of("."), "foo", ".bar");
        fc = FileChannel.open(path, CREATE, WRITE, DELETE_ON_CLOSE);
        fc.position(POS);
        fc.write(ByteBuffer.wrap(new byte[(int)SIZE]));
    }

    @AfterAll
    public static void after() throws IOException {
        fc.close();
    }

    public static Stream<Arguments> ranges() {
        return Stream.of(
            Arguments.of(POS, SIZE, -1,-1, FALSE),
            Arguments.of(POS, SIZE, 0, -1, FALSE),
            Arguments.of(POS, SIZE, POS - 1, -1, FALSE),
            Arguments.of(POS, SIZE, POS + SIZE/2, -1, FALSE),
            Arguments.of(POS, SIZE, POS + SIZE, -1, FALSE),
            Arguments.of(POS, SIZE, -1, POS, FALSE),
            Arguments.of(POS, SIZE, -1, POS + SIZE/2, TRUE),
            Arguments.of(POS, SIZE, POS - 2, 1, FALSE),
            Arguments.of(POS, SIZE, POS + 1, 1, TRUE),
            Arguments.of(POS, SIZE, POS + SIZE/2, 0, TRUE),
            Arguments.of(POS, SIZE, Long.MAX_VALUE, 2, FALSE),
            Arguments.of(POS, SIZE, POS + SIZE / 2, Long.MAX_VALUE, TRUE),
            Arguments.of(POS, SIZE, 0, 0, TRUE),
            Arguments.of(Long.MAX_VALUE - SIZE/2, 0, 0, SIZE, FALSE),
            Arguments.of(Long.MAX_VALUE - SIZE/2, 0, Long.MAX_VALUE - SIZE/4, SIZE, TRUE),
            Arguments.of(Long.MAX_VALUE - SIZE/2, 0, Long.MAX_VALUE - SIZE, 0, TRUE),
            Arguments.of(Long.MAX_VALUE - SIZE, 0, Long.MAX_VALUE - SIZE/2, 0, TRUE));
    }

    @ParameterizedTest
    @MethodSource("ranges")
    public void overlaps(long lockPos, long lockSize, long pos, long size,
        boolean overlaps) throws IOException {
        try (FileLock lock = fc.lock(lockPos, lockSize, false)) {
            assertEquals(overlaps, lock.overlaps(pos, size));
        }
    }
}
