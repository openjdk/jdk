/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8159393
 * @summary Test signed jars involved in image creation
 * @modules java.base/jdk.internal.jimage
 *          jdk.jlink/jdk.tools.jlink.internal
 *          jdk.compiler/com.sun.tools.javac
 *          java.base/sun.security.tools.keytool
 *          jdk.jartool/sun.security.tools.jarsigner
 *          jdk.jartool/sun.tools.jar
 * @run main/othervm JLinkSigningTest
 */


import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

public class JLinkSigningTest {
    static final String[] MODULE_INFO = {
        "module test {",
        "}",
    };

    static final String[] TEST_CLASS = {
        "package test;",
        "public class test {",
        "    public static void main(String[] args) {",
        "    }",
        "}",
    };

    static void report(String command, String[] args) {
        System.out.println(command + " " + String.join(" ", Arrays.asList(args)));
    }

    static void javac(String[] args) {
        report("javac", args);
        com.sun.tools.javac.Main javac = new com.sun.tools.javac.Main();

        if (javac.compile(args) != 0) {
            throw new RuntimeException("javac failed");
        }
    }

    static void jar(String[] args) {
        report("jar", args);
        sun.tools.jar.Main jar = new sun.tools.jar.Main(System.out, System.err, "jar");

        if (!jar.run(args)) {
            throw new RuntimeException("jar failed");
        }
    }

    static void keytool(String[] args) {
        report("keytool", args);

        try {
            sun.security.tools.keytool.Main.main(args);
        } catch (Exception ex) {
            throw new RuntimeException("keytool failed");
        }
    }

    static void jarsigner(String[] args) {
        report("jarsigner", args);

        try {
            sun.security.tools.jarsigner.Main.main(args);
        } catch (Exception ex) {
            throw new RuntimeException("jarsigner failed");
        }
    }

    static void jlink(String[] args) {
        report("jlink", args);

        try {
            jdk.tools.jlink.internal.Main.run(new PrintWriter(System.out, true),
                                              new PrintWriter(System.err, true),
                                              args);
        } catch (Exception ex) {
            throw new RuntimeException("jlink failed");
        }
    }

    public static void main(String[] args) {
        final String JAVA_HOME = System.getProperty("java.home");
        Path moduleInfoJavaPath = Paths.get("module-info.java");
        Path moduleInfoClassPath = Paths.get("module-info.class");
        Path testDirectoryPath = Paths.get("test");
        Path testJavaPath = testDirectoryPath.resolve("test.java");
        Path testClassPath = testDirectoryPath.resolve("test.class");
        Path testModsDirectoryPath = Paths.get("testmods");
        Path jmodsPath = Paths.get(JAVA_HOME, "jmods");
        Path testjarPath = testModsDirectoryPath.resolve("test.jar");
        String modulesPath = testjarPath.toString() +
                             File.pathSeparator +
                             jmodsPath.toString();

        try {
            Files.write(moduleInfoJavaPath, Arrays.asList(MODULE_INFO));
            Files.createDirectories(testDirectoryPath);
            Files.write(testJavaPath, Arrays.asList(TEST_CLASS));
            Files.createDirectories(testModsDirectoryPath);
        } catch (IOException ex) {
            throw new RuntimeException("file construction failed");
        }

        javac(new String[] {
            testJavaPath.toString(),
            moduleInfoJavaPath.toString(),
        });

        jar(new String[] {
            "-c",
            "-f", testjarPath.toString(),
            "--module-path", jmodsPath.toString(),
            testClassPath.toString(),
            moduleInfoClassPath.toString(),
        });

        keytool(new String[] {
            "-genkey",
            "-keyalg", "RSA",
            "-dname", "CN=John Doe, OU=JPG, O=Oracle, L=Santa Clara, ST=California, C=US",
            "-alias", "examplekey",
            "-storepass", "password",
            "-keypass", "password",
            "-keystore", "examplekeystore",
            "-validity", "365",
        });

        jarsigner(new String[] {
            "-keystore", "examplekeystore",
            "-verbose", testjarPath.toString(),
            "-storepass", "password",
            "-keypass", "password",
            "examplekey",
        });

        try {
            jlink(new String[] {
                "--module-path", modulesPath,
                "--add-modules", "test",
                "--output", "foo",
            });
        } catch (Throwable ex) {
            System.out.println("Failed as should");
        }

        try {
            jlink(new String[] {
                "--module-path", modulesPath,
                "--add-modules", "test",
                "--ignore-signing-information",
                "--output", "foo",
            });
            System.out.println("Suceeded as should");
        } catch (Throwable ex) {
            System.err.println("Should not have failed");
            throw new RuntimeException(ex);
        }

        System.out.println("Done");
    }
}

