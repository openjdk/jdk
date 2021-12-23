/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8272746
 * @summary ZipFile can't open big file (NegativeArraySizeException)
 * @requires (sun.arch.data.model == "64" & os.maxMemory > 8g)
 * @run main/othervm/timeout=3600 -Xmx8g TestTooManyEntries
 */

import java.io.File;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.util.UUID;

public class TestTooManyEntries {
    public static void main(String[] args) throws Exception {
        boolean passed = false;
        File hugeZipFile = File.createTempFile("hugeZip", ".zip", new File("."));
        hugeZipFile.deleteOnExit();
        long startTime = System.currentTimeMillis();
        try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(hugeZipFile)))) {
            int dirCount = 25_000;
            long nextLog = System.currentTimeMillis();
            for (int dirN = 0; dirN < dirCount; dirN++) {
                String dirName = UUID.randomUUID() + "/";
                for (int fileN = 0; fileN < 1_000; fileN++) {
                    ZipEntry entry = new ZipEntry(dirName + UUID.randomUUID());
                    zip.putNextEntry(entry);
                    zip.closeEntry(); // all files are empty
                }
                if (System.currentTimeMillis() >= nextLog) {
                    nextLog = 30_000 + System.currentTimeMillis();
                    System.out.printf("Processed %s%% directories (%s), file size is %sMb (%ss)%n",
                            dirN * 100 / dirCount, dirN, hugeZipFile.length() / 1024 / 1024,
                            (System.currentTimeMillis() - startTime) / 1000);
                }
            }
        }
        System.out.printf("File generated in %ss, file size is %sMb%n",
                (System.currentTimeMillis() - startTime) / 1000, hugeZipFile.length() / 1024 / 1024);

        try {
            ZipFile zip = new ZipFile(hugeZipFile);
        } catch (ZipException ze) {
            passed = true;
        }

        if (!passed) {
            throw new RuntimeException("Failed.");
        }
    }
}
