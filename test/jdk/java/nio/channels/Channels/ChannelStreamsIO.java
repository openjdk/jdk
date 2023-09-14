/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8316156
 * @summary Ensure Channel{In,Out}putStreams do not overrun MaxDirectMemorySize
 * @run main/othervm -XX:MaxDirectMemorySize=5M ChannelStreamsIO
 */

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static java.nio.file.StandardOpenOption.*;

public class ChannelStreamsIO {
    // this value must exceed MaxDirectMemorySize
    private static final int SIZE = 10*1024*1024;

    public static void main(String[] args) throws IOException {
        byte[] x = new byte[SIZE];
        Random rnd = new Random(System.nanoTime());
        rnd.nextBytes(x);

        Path file = Files.createTempFile("sna", "fu");
        try {
            try (FileChannel fc = FileChannel.open(file, CREATE, WRITE)) {
                try (OutputStream out = Channels.newOutputStream(fc)) {
                    out.write(x);
                }
            }
            try (FileChannel fc = FileChannel.open(file, READ)) {
                try (InputStream in = Channels.newInputStream(fc)) {
                    byte[] y = new byte[SIZE];
                    int n = -1;
                    if ((n = in.read(y)) != SIZE)
                        throw new RuntimeException("n " + n + " != " + SIZE);
                    if (!Arrays.equals(x, y))
                        throw new RuntimeException("Arrays are not equal");
                }
            }
        } finally {
            Files.delete(file);
        }
    }
}
