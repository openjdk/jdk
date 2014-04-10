/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8038491
 * @summary Crash in ZipFile.read() when ZipFileInputStream is shared between threads
 * @library /lib/testlibrary
 * @build jdk.testlibrary.FileUtils
 * @run main MultiThreadedReadTest
 */

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Paths;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import jdk.testlibrary.FileUtils;

public class MultiThreadedReadTest extends Thread {

    private static final int NUM_THREADS = 10;
    private static final String ZIPFILE_NAME = "large.zip";
    private static final String ZIPENTRY_NAME = "random.txt";
    private static InputStream is = null;

    public static void main(String args[]) throws Exception {
        createZipFile();
        try (ZipFile zf = new ZipFile(new File(ZIPFILE_NAME))) {
            is = zf.getInputStream(zf.getEntry(ZIPENTRY_NAME));
            Thread[] threadArray = new Thread[NUM_THREADS];
            for (int i = 0; i < threadArray.length; i++) {
                threadArray[i] = new MultiThreadedReadTest();
            }
            for (int i = 0; i < threadArray.length; i++) {
                threadArray[i].start();
            }
            for (int i = 0; i < threadArray.length; i++) {
                threadArray[i].join();
            }
        } finally {
            FileUtils.deleteFileIfExistsWithRetry(Paths.get(ZIPFILE_NAME));
        }
    }

    private static void createZipFile() throws Exception {
        try (ZipOutputStream zos =
            new ZipOutputStream(new FileOutputStream(ZIPFILE_NAME))) {

            zos.putNextEntry(new ZipEntry(ZIPENTRY_NAME));
            StringBuilder sb = new StringBuilder();
            Random rnd = new Random();
            for(int i = 0; i < 1000; i++) {
                // append some random string for ZipEntry
                sb.append(Long.toString(rnd.nextLong()));
            }
            byte[] b = sb.toString().getBytes();
            zos.write(b, 0, b.length);
        }
    }

    @Override
    public void run() {
        try {
            while (is.read() != -1) { }
        } catch (Exception e) {
            // Swallow any Exceptions (which are expected) - we're only interested in the crash
        }
    }
}
