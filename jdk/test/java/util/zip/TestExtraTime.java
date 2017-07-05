/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4759491 6303183 7012868 8015666
 * @summary Test ZOS and ZIS timestamp in extra field correctly
 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class TestExtraTime {

    public static void main(String[] args) throws Throwable{

        File src = new File(System.getProperty("test.src", "."), "TestExtraTime.java");
        if (src.exists()) {
            long time = src.lastModified();
            FileTime mtime = FileTime.from(time, TimeUnit.MILLISECONDS);
            FileTime atime = FileTime.from(time + 300000, TimeUnit.MILLISECONDS);
            FileTime ctime = FileTime.from(time - 300000, TimeUnit.MILLISECONDS);
            TimeZone tz = TimeZone.getTimeZone("Asia/Shanghai");

            test(mtime, null, null, null);
            // ms-dos 1980 epoch problem
            test(FileTime.from(10, TimeUnit.MILLISECONDS), null, null, null);
            // non-default tz
            test(mtime, null, null, tz);

            test(mtime, atime, null, null);
            test(mtime, null, ctime, null);
            test(mtime, atime, ctime, null);

            test(mtime, atime, null, tz);
            test(mtime, null, ctime, tz);
            test(mtime, atime, ctime, tz);
        }
    }

    static void test(FileTime mtime, FileTime atime, FileTime ctime,
                     TimeZone tz) throws Throwable {
        System.out.printf("--------------------%nTesting: [%s]/[%s]/[%s]%n",
                          mtime, atime, ctime);
        TimeZone tz0 = TimeZone.getDefault();
        if (tz != null) {
            TimeZone.setDefault(tz);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry ze = new ZipEntry("TestExtreTime.java");

        ze.setLastModifiedTime(mtime);
        if (atime != null)
            ze.setLastAccessTime(atime);
        if (ctime != null)
            ze.setCreationTime(ctime);
        zos.putNextEntry(ze);
        zos.write(new byte[] { 1,2 ,3, 4});
        zos.close();
        if (tz != null) {
            TimeZone.setDefault(tz0);
        }
        // ZipInputStream
        ZipInputStream zis = new ZipInputStream(
                                 new ByteArrayInputStream(baos.toByteArray()));
        ze = zis.getNextEntry();
        zis.close();
        check(mtime, atime, ctime, ze);

        // ZipFile
        Path zpath = Paths.get(System.getProperty("test.dir", "."),
                               "TestExtraTimp.zip");
        Files.copy(new ByteArrayInputStream(baos.toByteArray()), zpath);
        ZipFile zf = new ZipFile(zpath.toFile());
        ze = zf.getEntry("TestExtreTime.java");
        // ZipFile read entry from cen, which does not have a/ctime,
        // for now.
        check(mtime, null, null, ze);
        zf.close();
        Files.delete(zpath);
    }

    static void check(FileTime mtime, FileTime atime, FileTime ctime,
                      ZipEntry ze) {
        /*
        System.out.printf("    mtime [%tc]: [%tc]/[%tc]%n",
                          mtime.to(TimeUnit.MILLISECONDS),
                          ze.getTime(),
                          ze.getLastModifiedTime().to(TimeUnit.MILLISECONDS));
         */
        if (mtime.to(TimeUnit.SECONDS) !=
            ze.getLastModifiedTime().to(TimeUnit.SECONDS))
            throw new RuntimeException("Timestamp: storing mtime failed!");
        if (atime != null &&
            atime.to(TimeUnit.SECONDS) !=
            ze.getLastAccessTime().to(TimeUnit.SECONDS))
            throw new RuntimeException("Timestamp: storing atime failed!");
        if (ctime != null &&
            ctime.to(TimeUnit.SECONDS) !=
            ze.getCreationTime().to(TimeUnit.SECONDS))
            throw new RuntimeException("Timestamp: storing ctime failed!");
    }
}
