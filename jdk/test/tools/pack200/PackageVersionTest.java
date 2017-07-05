
/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
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
  * @bug 6712743
  * @summary verify package versioning
  * @compile -XDignore.symbol.file PackageVersionTest.java
  * @run main PackageVersionTest
  */

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.jar.JarFile;
import java.util.jar.Pack200;
import java.util.jar.Pack200.Packer;

public class PackageVersionTest {
    private static final File  javaHome = new File(System.getProperty("java.home"));

    public final static int JAVA5_PACKAGE_MAJOR_VERSION = 150;
    public final static int JAVA5_PACKAGE_MINOR_VERSION = 7;

    public final static int JAVA6_PACKAGE_MAJOR_VERSION = 160;
    public final static int JAVA6_PACKAGE_MINOR_VERSION = 1;

    public static void main(String... args) {
        if (!javaHome.getName().endsWith("jre")) {
            throw new RuntimeException("Error: requires an SDK to run");
        }

        File out = new File("test.pack");
        createClassFile("Test5");
        createClassFile("Test6");
        createClassFile("Test7");

        verifyPack("Test5.class", JAVA5_PACKAGE_MAJOR_VERSION,
                JAVA5_PACKAGE_MINOR_VERSION);

        verifyPack("Test6.class", JAVA6_PACKAGE_MAJOR_VERSION,
                JAVA6_PACKAGE_MINOR_VERSION);

        // TODO: change this to the java7 package version as needed.
        verifyPack("Test7.class", JAVA6_PACKAGE_MAJOR_VERSION,
                JAVA6_PACKAGE_MINOR_VERSION);

        // test for resource file, ie. no class files
        verifyPack("Test6.java", JAVA5_PACKAGE_MAJOR_VERSION,
                JAVA5_PACKAGE_MINOR_VERSION);
    }

    static void close(Closeable c) {
        if (c == null) {
            return;
        }
        try {
            c.close();
        } catch (IOException ignore) {}
    }

    static void createClassFile(String name) {
        createJavaFile(name);
        String target = name.substring(name.length() - 1);
        String javacCmds[] = {
            "-source",
            "5",
            "-target",
            name.substring(name.length() - 1),
            name + ".java"
        };
        compileJava(javacCmds);
    }

    static void createJavaFile(String name) {
        PrintStream ps = null;
        FileOutputStream fos = null;
        File outputFile = new File(name + ".java");
        outputFile.delete();
        try {
            fos = new FileOutputStream(outputFile);
            ps = new PrintStream(fos);
            ps.format("public class %s {}", name);
        } catch (IOException ioe) {
            throw new RuntimeException("creation of test file failed");
        } finally {
            close(ps);
            close(fos);
        }
    }

    static void compileJava(String... javacCmds) {
        if (com.sun.tools.javac.Main.compile(javacCmds) != 0) {
            throw new RuntimeException("compilation failed");
        }
    }

    static void makeJar(String... jargs) {
        sun.tools.jar.Main jarTool =
                new sun.tools.jar.Main(System.out, System.err, "jartool");
        if (!jarTool.run(jargs)) {
            throw new RuntimeException("jar command failed");
        }
    }

    static void verifyPack(String filename, int expected_major, int expected_minor) {

        File jarFileName = new File("test.jar");
        jarFileName.delete();
        String jargs[] = {
            "cvf",
            jarFileName.getName(),
            filename
        };
        makeJar(jargs);
        JarFile jfin = null;

        try {
            jfin = new JarFile(jarFileName);
            Packer packer = Pack200.newPacker();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            packer.pack(jfin, baos);
            baos.flush();
            baos.close();
            byte[] buf = baos.toByteArray();

            int minor = buf[4] & 0x000000ff;
            int major = buf[5] & 0x000000ff;

            if (major != expected_major || minor != expected_minor) {
                String msg =
                        String.format("test fails: expected:%d.%d but got %d.%d\n",
                        expected_major, expected_minor,
                        major, minor);
                throw new Error(msg);
            }

            System.out.println(filename + ": OK");
        } catch (IOException ioe) {
            throw new RuntimeException(ioe.getMessage());
        } finally {
            close(jfin);
        }
    }
}
