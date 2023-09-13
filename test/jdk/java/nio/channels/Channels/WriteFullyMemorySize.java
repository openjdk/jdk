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
 * @summary Ensure Channels.newOutputStream.write doe not overrun max memory
 * @run main/othervm -XX:MaxDirectMemorySize=5M WriteFullyMemorySize
 */

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Random;

public class WriteFullyMemorySize {
    // this value must exceed MaxDirectMemorySize
    private static final int SIZE = 10*1024*1024;

    public static void main(String[] args) throws IOException {
        byte[] b = new byte[SIZE];
        Random rnd = new Random(System.nanoTime());
        rnd.nextBytes(b);
        ByteArrayInputStream bais = new ByteArrayInputStream(b);
        Path target = Files.createTempFile("sna", "fu");
        try {
            Files.copy(bais, target, StandardCopyOption.REPLACE_EXISTING);
            byte[] res = Files.readAllBytes(target);
            if (!Arrays.equals(b, res))
                throw new RuntimeException("Arrays are not equal");
        } finally {
            Files.delete(target);
        }
    }
}
