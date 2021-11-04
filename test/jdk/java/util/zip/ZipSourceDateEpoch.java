/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Driven by: TestZipSourceDateEpoch.sh
 */

import java.util.*;
import java.util.zip.*;
import java.util.jar.*;
import java.io.*;

public class ZipSourceDateEpoch {

    public static void main(String[] args) throws Exception {

        String TEST_DATA = "Test data string";

        String env = System.getenv("SOURCE_DATE_EPOCH");

        long sourceDateEpochMillis = -1;
        if (env != null) {
            try {
                long value = Long.parseLong(env);
                // SOURCE_DATE_EPOCH is in seconds
                sourceDateEpochMillis = value*1000;
            } catch(NumberFormatException e) {
                throw new AssertionError("Invalid SOURCE_DATE_EPOCH long value");
            }
        }

        // Write test zip
        File f = new File("epoch.zip");
        f.deleteOnExit();

        OutputStream os = new FileOutputStream(f);
        ZipOutputStream zos = new ZipOutputStream(os);
        try {
            zos.putNextEntry(new ZipEntry("Entry1.txt"));
            zos.write(TEST_DATA.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Entry2.txt"));
            zos.write(TEST_DATA.getBytes());
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("Entry3.txt"));
            zos.write(TEST_DATA.getBytes());
            zos.closeEntry();
        } finally {
            zos.close();
            os.close();
        }

        //----------------------------------------------------------------
        // Verify zip file entries all have SOURCE_DATE_EPOCH time if set
        //----------------------------------------------------------------
        FileInputStream fis = new FileInputStream(f);
        ZipInputStream zis = new ZipInputStream(fis);
        try {
            long now = System.currentTimeMillis();
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (sourceDateEpochMillis != -1) {
                    // SOURCE_DATE_EPOCH set, check time correct
                    if (entry.getTime() != sourceDateEpochMillis) {
                        throw new AssertionError("ZipEntry getTime() "+entry.getTime()+" not equal to SOURCE_DATE_EPOCH "+sourceDateEpochMillis);
                    }
                } else {
                    // SOURCE_DATE_EPOCH not set, check time is current created within the last 60 seconds
                    if (entry.getTime() < (now-60000) || entry.getTime() > now) {
                        throw new AssertionError("ZipEntry getTime() "+entry.getTime()+" is not the current time "+now);
                    }
                }
            }
        } finally {
            zis.close();
            fis.close();
        }

        if (sourceDateEpochMillis != -1) {
            System.out.println("ZipOutputStream SOURCE_DATE_EPOCH test passed");
        } else {
            System.out.println("ZipOutputStream current time test passed");
        }
    }
}
