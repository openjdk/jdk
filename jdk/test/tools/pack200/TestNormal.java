/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @run main/timeout=600 TestNormal
 * @bug 8020802
 * @summary Need an ability to create jar files that are invariant to the pack200 packing/unpacking
 * @author Alexander Zuev
 */

import java.io.*;
import java.util.Collections;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TestNormal {
    private static String FS = File.separator;

    public static void main(String args[]) throws Exception {
        Properties p = System.getProperties();
        String java_home = p.getProperty("test.jdk");
        String testdir = Utils.TEST_CLS_DIR.getAbsolutePath();

        try {
            execJavaCommand(java_home, "jar cnf normalized.jar -C " + testdir + " .");
            execJavaCommand(java_home, "jar cf original.jar -C " + testdir + " .");
            execJavaCommand(java_home, "pack200 -r repacked.jar original.jar");
            compareJars(new JarFile("normalized.jar"), new JarFile("repacked.jar"));
        } finally {
            String[] cleanupList = {"normalized.jar", "original.jar", "repacked.jar"};
            for (String s : cleanupList) {
                delete(new File(s));
            }
        }
    }

    public static void execJavaCommand(String java_home, String cmd) throws Exception {
        Process proc = Runtime.getRuntime().exec(java_home + FS + "bin" + FS + cmd);
        String s;
        BufferedReader stdInput =
                new BufferedReader(new InputStreamReader(proc.getInputStream()));
        BufferedReader stdError =
                new BufferedReader(new InputStreamReader(proc.getErrorStream()));
        while ((s = stdInput.readLine()) != null) {
            System.out.println(s);
        }
        while ((s = stdError.readLine()) != null) {
            System.err.println(s);
        }
    }

    public static void compareJars(JarFile jf1, JarFile jf2) throws Exception {
        try {
            if (jf1.size() != jf2.size()) {
                throw new Exception("Jars " + jf1.getName() + " and " + jf2.getName()
                        + " have different number of entries");
            }
            for (JarEntry elem1 : Collections.list(jf1.entries())) {
                JarEntry elem2 = jf2.getJarEntry(elem1.getName());
                if (elem2 == null) {
                    throw new Exception("Element " + elem1.getName() + " is missing from " + jf2.getName());
                }
                if (!elem1.isDirectory() && elem1.getCrc() != elem2.getCrc()) {
                    throw new Exception("The crc of " + elem1.getName() + " is different.");
                }
            }
        } finally {
            jf1.close();
            jf2.close();
        }
    }

    static void delete(File f) throws IOException {
        if (!f.exists()) {
            return;
        }
        if (f.isDirectory()) {
            for (File c : f.listFiles()) {
                delete(c);
            }
        }
        if (!f.delete()) {
            throw new FileNotFoundException("Failed to delete file: " + f);
        }
    }
}
