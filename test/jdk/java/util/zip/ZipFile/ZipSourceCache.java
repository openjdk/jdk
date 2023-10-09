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
 * @bug 8317678
 * @modules java.base/java.util.zip:open
 * @summary Fix up hashCode() for ZipFile.Source.Key
 * @run testng/othervm ZipSourceCache
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

import static org.testng.Assert.*;

public class ZipSourceCache {

    private static final String ZIPFILE_NAME =
            System.currentTimeMillis() + "-bug8317678.zip";
    private static final String ZIPENTRY_NAME = "random.txt";

    private static File relativeFile = new File(ZIPFILE_NAME);
    private static File absoluteFile = new File(ZIPFILE_NAME).getAbsoluteFile();

    @BeforeTest
    public void setup() throws Exception {
        createZipFile();
    }

    @AfterTest
    public void cleanup() throws IOException {
        Files.deleteIfExists(Path.of(ZIPENTRY_NAME));
    }

    @Test
    public static void test() throws Exception {
        ZipFile absoluteZipFile;
        try (ZipFile zipFile = new ZipFile(ZIPFILE_NAME)) {
            Class source = Class.forName("java.util.zip.ZipFile$Source");
            Field filesMap = source.getDeclaredField("files");
            filesMap.setAccessible(true);
            HashMap internalMap = (HashMap) filesMap.get(zipFile);
            int numSources = internalMap.size();
            // opening of same zip file shouldn't cause new Source to be constructed
            absoluteZipFile = new ZipFile(absoluteFile);
            assertEquals(numSources, internalMap.size());
        }
        if (absoluteZipFile != null) {
            absoluteZipFile.close();
        }
    }

    private static void createZipFile() throws Exception {
        CRC32 crc32 = new CRC32();
        long t = System.currentTimeMillis();
        File zipFile = new File(ZIPFILE_NAME);
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             BufferedOutputStream bos = new BufferedOutputStream(fos);
             ZipOutputStream zos = new ZipOutputStream(bos)) {
            ZipEntry e = new ZipEntry(ZIPENTRY_NAME);
            e.setMethod(ZipEntry.STORED);
            byte[] toWrite = "BLAH".getBytes();
            e.setTime(t);
            e.setSize(toWrite.length);
            crc32.reset();
            crc32.update(toWrite);
            e.setCrc(crc32.getValue());
            zos.putNextEntry(e);
            zos.write(toWrite);
        }
    }
}
