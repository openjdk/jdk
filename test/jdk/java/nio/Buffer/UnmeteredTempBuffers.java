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

/**
 * @test
 * @bug 8344882
 * @summary Deallocation failure for temporary buffers
 * @run junit/othervm -XX:MaxDirectMemorySize=32768 UnmeteredTempBuffers
 */
import java.io.InputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

public class UnmeteredTempBuffers {
    @ParameterizedTest
    @ValueSource(ints = {16384, 32768, 32769, 65536})
    void testFileChannel(int cap) throws IOException {
        Path file = Files.createTempFile("prefix", "suffix");
        try (FileChannel ch = FileChannel.open(file, WRITE, DELETE_ON_CLOSE)) {
            ByteBuffer buf = ByteBuffer.wrap(new byte[cap]);
            try {
                ch.write(buf);
            } catch (OutOfMemoryError oome) {
                throw new RuntimeException(oome);
            }
        }  finally {
            Files.deleteIfExists(file);
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {16384, 32768, 32769, 65536})
    void testInputStream(int cap) throws IOException {
        Path file = Files.createTempFile("prefix", "suffix");
        try {
            byte[] bytes = new byte[cap];
            Files.write(file, bytes);
            try (InputStream in = Files.newInputStream(file)) {
                in.read(bytes);
            } catch (OutOfMemoryError oome) {
                throw new RuntimeException(oome);
            }
        }  finally {
            Files.delete(file);
        }
    }
}
