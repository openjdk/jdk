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
 * @summary Ensure {BAIS,BIS}.transferTo do not overrun MaxDirectMemorySize
 * @run junit/othervm -XX:MaxDirectMemorySize=5M ChunkedTransferTo
 */

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Random;

import static java.nio.file.StandardCopyOption.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class ChunkedTransferTo {
    // this value must exceed MaxDirectMemorySize
    private static final int SIZE = 10_000_000;

    static byte[] src;
    static Path source;

    @BeforeAll
    public static void before() throws IOException {
        src = new byte[SIZE];
        Random rnd = new Random(System.nanoTime());
        rnd.nextBytes(src);
        source = Files.createTempFile("SNA", "FU");
        Files.write(source, src);
    }

    @AfterAll
    public static void after() throws IOException {
        Files.deleteIfExists(source);
    }

    @Test
    public void bufferedInputStream() throws IOException {
        try (InputStream in = new FileInputStream(source.toFile());
             BufferedInputStream bis = new BufferedInputStream(in)) {
            Path target = Files.createTempFile("BIS", "target");
            Files.copy(bis, target, REPLACE_EXISTING);
            byte[] dst = new byte[SIZE + 1];
            try (FileInputStream fis = new FileInputStream(target.toFile())) {
                int n = -1;
                if ((n = fis.read(dst)) != SIZE)
                    throw new RuntimeException(n + " != " + SIZE);
            }
            Files.delete(target);
            if (!Arrays.equals(src, 0, SIZE, dst, 0, SIZE))
                throw new RuntimeException("Arrays are not equal");
        } catch (OutOfMemoryError oome) {
            throw new RuntimeException(oome);
        }
    }

    @Test
    public void byteArrayInputStream() throws IOException {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(src)) {
            Path target = Files.createTempFile("BAIS", "target");
            Files.copy(bais, target, REPLACE_EXISTING);
            byte[] dst = new byte[SIZE + 1];
            try (FileInputStream fis = new FileInputStream(target.toFile())) {
                int n = -1;
                if ((n = fis.read(dst)) != SIZE)
                    throw new RuntimeException(n + " != " + SIZE);
            }
            Files.delete(target);
            if (!Arrays.equals(src, 0, SIZE, dst, 0, SIZE))
                throw new RuntimeException("Arrays are not equal");
        } catch (OutOfMemoryError oome) {
            throw new RuntimeException(oome);
        }
    }
}
