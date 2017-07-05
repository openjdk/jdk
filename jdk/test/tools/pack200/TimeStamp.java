/*
 * Copyright (c) 2010, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

/*
 * @test
 * @bug 6966740
 * @summary verify identical timestamps, unpacked in any timezone
 * @compile -XDignore.symbol.file Utils.java TimeStamp.java
 * @run main/othervm TimeStamp
 * @author ksrini
 */

/**
 * First we pack the file in some time zone say India, then we unpack the  file
 * in the current time zone, and ensure the timestamp recorded in the unpacked
 * jar are the same.
 */
public class TimeStamp {
    static final TimeZone tz = TimeZone.getDefault();


    public static void main(String... args) throws IOException {

        // make a local copy of our test file
        File srcFile = Utils.locateJar("golden.jar");
        File goldenFile = new File("golden.jar");
        Utils.copyFile(srcFile, goldenFile);

        JarFile goldenJarFile = new JarFile(goldenFile);
        File packFile = new File("golden.pack");

        // set the test timezone and pack the file
        TimeZone.setDefault(TimeZone.getTimeZone("IST"));
        Utils.pack(goldenJarFile, packFile);
        TimeZone.setDefault(tz);   // reset the timezone

        // unpack in the  test timezone
        File istFile = new File("golden.jar.java.IST");
        unpackJava(packFile, istFile);
        verifyJar(goldenFile, istFile);
        istFile.delete();

        // unpack in some other timezone
        File pstFile = new File("golden.jar.java.PST");
        unpackJava(packFile, pstFile);
        verifyJar(goldenFile, pstFile);
        pstFile.delete();

        // repeat the test for unpack200 tool.
        istFile = new File("golden.jar.native.IST");
        unpackNative(packFile, istFile);
        verifyJar(goldenFile, istFile);
        istFile.delete();

        pstFile = new File("golden.jar.native.PST");
        unpackNative(packFile, pstFile);
        verifyJar(goldenFile, pstFile);
        pstFile.delete();
    }

    static void unpackNative(File packFile, File outFile) {
        String name = outFile.getName();
        String tzname = name.substring(name.lastIndexOf(".") + 1);
        HashMap<String, String> env = new HashMap<>();
        switch(tzname) {
            case "PST":
                env.put("TZ", "US/Pacific");
                break;
            case "IST":
                env.put("TZ", "Asia/Calcutta");
                break;
            default:
                throw new RuntimeException("not implemented: " + tzname);
        }
        List<String> cmdsList = new ArrayList<>();
        cmdsList.add(Utils.getUnpack200Cmd());
        cmdsList.add(packFile.getName());
        cmdsList.add(outFile.getName());
        Utils.runExec(cmdsList, env);
    }

    static void unpackJava(File packFile, File outFile) throws IOException {
        String name = outFile.getName();
        String tzname = name.substring(name.lastIndexOf(".") + 1);
        JarOutputStream jos = null;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(tzname));
            jos = new JarOutputStream(new FileOutputStream(outFile));
            System.out.println("Using timezone: " + TimeZone.getDefault());
            Utils.unpackj(packFile, jos);
        } finally {
            Utils.close(jos);
            TimeZone.setDefault(tz); // always reset
        }
    }

    static void verifyJar(File f1, File f2) throws IOException {
        int errors = 0;
        JarFile jf1 = null;
        JarFile jf2 = null;
        try {
            jf1 = new JarFile(f1);
            jf2 = new JarFile(f2);
            System.out.println("Verifying: " + f1 + " and " + f2);
            for (JarEntry je1 : Collections.list(jf1.entries())) {
                JarEntry je2 = jf2.getJarEntry(je1.getName());
                if (je1.getTime() != je2.getTime()) {
                    System.out.println("Error:");
                    System.out.println("  expected:" + jf1.getName() + ":"
                            + je1.getName() + ":" + je1.getTime());
                    System.out.println("  obtained:" + jf2.getName() + ":"
                            + je2.getName() + ":" + je2.getTime());
                    errors++;
                }
            }
        } finally {
            Utils.close(jf1);
            Utils.close(jf2);
        }
        Utils.cleanup();
        if (errors > 0) {
            throw new RuntimeException("FAIL:" + errors + " error(s) encounted");
        }
    }
}
