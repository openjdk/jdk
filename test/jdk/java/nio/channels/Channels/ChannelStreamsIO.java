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
 * @run junit/othervm -XX:MaxDirectMemorySize=5M ChannelStreamsIO
 */

import java.io.FileInputStream;
import java.io.FileOutputStream;
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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ChannelStreamsIO {
    // this value must exceed MaxDirectMemorySize
    private static final int SIZE = 10_000_000;

    static byte[] src;
    static Path path;

    @BeforeAll
    public static void before() throws IOException {
        src = new byte[SIZE];
        Random rnd = new Random(System.nanoTime());
        rnd.nextBytes(src);
        path = Files.createTempFile("SNA", "FU");
    }

    @AfterEach
    public void after() throws IOException {
        Files.deleteIfExists(path);
    }

    @Test
    public void write() throws IOException {
        try (FileChannel fc = FileChannel.open(path, CREATE, WRITE);
             OutputStream out = Channels.newOutputStream(fc)) {
            out.write(src);
        } catch (OutOfMemoryError oome) {
            throw new RuntimeException(oome);
        }

        try (InputStream in = new FileInputStream(path.toFile())) {
            byte[] dst = new byte[SIZE];
            int n = -1;
            if ((n = in.read(dst)) != SIZE)
                throw new RuntimeException(n + " != " + SIZE);
            if (!Arrays.equals(src, dst))
                throw new RuntimeException("Arrays are not equal");
        }
    }

    @Test
    public void read() throws IOException {
        try (OutputStream out = new FileOutputStream(path.toFile());
             FileChannel fc = FileChannel.open(path, READ);
             InputStream in = Channels.newInputStream(fc)) {
            out.write(src);
            byte[] dst = new byte[SIZE];
            int nread = 0;
            int n = -1;
            while ((n = in.read(dst, nread, SIZE - nread)) > 0)
                nread += n;
            if (nread != SIZE)
                throw new RuntimeException(nread + " != " + SIZE);
            if (!Arrays.equals(src, dst))
                throw new RuntimeException("Arrays are not equal");
        } catch (OutOfMemoryError oome) {
            throw new RuntimeException(oome);
        }
    }
}
