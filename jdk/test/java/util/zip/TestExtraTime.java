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
 * @bug 4759491 6303183 7012868
 * @summary Test ZOS and ZIS timestamp in extra field correctly
 */

import java.io.*;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public class TestExtraTime {

    public static void main(String[] args) throws Throwable{

        File src = new File(System.getProperty("test.src", "."), "TestExtraTime.java");
        if (src.exists()) {
            long mtime = src.lastModified();
            test(mtime, null);
            test(10, null);  // ms-dos 1980 epoch problem
            test(mtime, TimeZone.getTimeZone("Asia/Shanghai"));
        }
    }

    private static void test(long mtime, TimeZone tz) throws Throwable {
        TimeZone tz0 = TimeZone.getDefault();
        if (tz != null) {
            TimeZone.setDefault(tz);
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        ZipEntry ze = new ZipEntry("TestExtreTime.java");

        ze.setTime(mtime);
        zos.putNextEntry(ze);
        zos.write(new byte[] { 1,2 ,3, 4});
        zos.close();
        if (tz != null) {
            TimeZone.setDefault(tz0);
        }
        ZipInputStream zis = new ZipInputStream(
                                 new ByteArrayInputStream(baos.toByteArray()));
        ze = zis.getNextEntry();
        zis.close();

        System.out.printf("%tc  => %tc%n", mtime, ze.getTime());

        if (TimeUnit.MILLISECONDS.toSeconds(mtime) !=
            TimeUnit.MILLISECONDS.toSeconds(ze.getTime()))
            throw new RuntimeException("Timestamp storing failed!");

    }
}
