/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8137121
 * @summary (fc) Infinite loop FileChannel.truncate
 * @run main/othervm LoopingTruncate
 */

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import static java.nio.file.StandardOpenOption.*;

public class LoopingTruncate {

    // (int)FATEFUL_SIZE == -3 == IOStatus.INTERRUPTED
    static long FATEFUL_SIZE = 0x1FFFFFFFDL;

    static long TIMEOUT = 10_000; // 10 seconds

    public static void main(String[] args) throws Throwable {
        Path path = Files.createTempFile("LoopingTruncate.tmp", null);
        try {
            Thread th = new Thread(() -> {
                try (FileChannel fc = FileChannel.open(path, CREATE, WRITE)) {
                    fc.position(FATEFUL_SIZE + 1L);
                    fc.write(ByteBuffer.wrap(new byte[] {0}));
                    fc.truncate(FATEFUL_SIZE);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }});
            th.start();
            th.join(TIMEOUT);

            if (th.isAlive()) {
                th.interrupt();
                throw new RuntimeException("Failed to complete on time");
            }
        } finally {
            Files.deleteIfExists(path);
        }
    }
}
