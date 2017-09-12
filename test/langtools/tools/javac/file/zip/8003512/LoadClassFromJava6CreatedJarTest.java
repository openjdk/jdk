/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8003512
 * @summary javac doesn't work with jar files with >64k entries
 * @modules jdk.compiler
 *          jdk.jartool/sun.tools.jar
 * @compile -target 6 -source 6 -XDignore.symbol.file LoadClassFromJava6CreatedJarTest.java ../Utils.java
 * @run main/timeout=360 LoadClassFromJava6CreatedJarTest
 */

/*
 * The test creates a jar file with more than 64K entries. The jar file is
 * created executing the LoadClassFromJava6CreatedJarTest$MakeJar
 * class with a JVM version 6. The test must include Java 6 features only.
 *
 * The aim is to verify classes included in jar files with more than 64K entries
 * created with Java 6 can be loaded by more recent versions of Java.
 *
 * A path to JDK or JRE version 6 is needed. This can be provided
 * by passing this option to jtreg:
 * -javaoption:-Djava6.home="/path/to/jdk_or_jre6"
 */

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class LoadClassFromJava6CreatedJarTest {

    static final String javaHome6 = System.getProperty("java6.home");
    static final String testClasses = System.getProperty("test.classes");

    public static void main(String... args)
            throws IOException, InterruptedException {
        if (javaHome6 != null) {
            new LoadClassFromJava6CreatedJarTest().run();
        } else {
            System.out.println(
                "The test LoadClassFromJava6CreatedJarTest cannot be executed. " +
                "In order to run it you should pass an option with " +
                "this form -javaoption:-Djava6.home=\"/path/to/jdk_or_jre6\" " +
                "to jtreg.");
        }
    }

    void run() throws IOException, InterruptedException {
        File classA = new File("A.java");
        Utils.createJavaFile(classA, null);
        if (!Utils.compile("-target", "6", "-source", "6",
            classA.getAbsolutePath())) {
            throw new AssertionError("Test failed while compiling class A");
        }

        executeCommand(Arrays.asList(javaHome6 + "/bin/java", "-classpath",
            testClasses, "LoadClassFromJava6CreatedJarTest$MakeJar"));

        File classB = new File("B.java");
        Utils.createJavaFile(classB, classA);
        if (!Utils.compile("-cp", "a.jar", classB.getAbsolutePath())) {
            throw new AssertionError("Test failed while compiling class Main");
        }
    }

    void executeCommand(List<String> command)
            throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command).
            redirectErrorStream(true);
        Process p = pb.start();
        BufferedReader r =
            new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while ((line = r.readLine()) != null) {
            System.err.println(line);
        }
        int rc = p.waitFor();
        if (rc != 0) {
            throw new AssertionError("Unexpected exit code: " + rc);
        }
    }

    static class MakeJar {
        public static void main(String[] args) throws Throwable {
            File classFile = new File("A.class");
            ZipOutputStream zos = null;
            FileInputStream fis = null;
            final int MAX = Short.MAX_VALUE * 2 + 10;
            ZipEntry ze = null;
            try {
                zos = new ZipOutputStream(new FileOutputStream("a.jar"));
                zos.setLevel(ZipOutputStream.STORED);
                zos.setMethod(ZipOutputStream.STORED);
                for (int i = 0; i < MAX ; i++) {
                    ze = new ZipEntry("X" + i + ".txt");
                    ze.setSize(0);
                    ze.setCompressedSize(0);
                    ze.setCrc(0);
                    zos.putNextEntry(ze);
                }

                // add a class file
                ze = new ZipEntry("A.class");
                ze.setCompressedSize(classFile.length());
                ze.setSize(classFile.length());
                ze.setCrc(computeCRC(classFile));
                zos.putNextEntry(ze);
                fis = new FileInputStream(classFile);
                for (int c; (c = fis.read()) >= 0;) {
                    zos.write(c);
                }
            } finally {
                zos.close();
                fis.close();
            }
        }

        private static final int BUFFER_LEN = Short.MAX_VALUE * 2;

        static long getCount(long minlength) {
            return (minlength / BUFFER_LEN) + 1;
        }

        static long computeCRC(long minlength) {
            CRC32 crc = new CRC32();
            byte[] buffer = new byte[BUFFER_LEN];
            long count = getCount(minlength);
            for (long i = 0; i < count; i++) {
                crc.update(buffer);
            }
            return crc.getValue();
        }

        static long computeCRC(File inFile) throws IOException {
            byte[] buffer = new byte[8192];
            CRC32 crc = new CRC32();
            FileInputStream fis = null;
            BufferedInputStream bis = null;
            try {
                fis = new FileInputStream(inFile);
                bis = new BufferedInputStream(fis);
                int n = bis.read(buffer);
                while (n > 0) {
                    crc.update(buffer, 0, n);
                    n = bis.read(buffer);
                }
            } finally {
                bis.close();
                fis.close();
            }
            return crc.getValue();
        }
    }
}
