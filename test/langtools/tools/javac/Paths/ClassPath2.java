/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4212732 6485027 8293877
 * @summary Test handling of the Class-Path attribute in jar file manifests
 * @library /tools/lib
 * @build toolbox.ToolBox Util ClassPath
 * @run main ClassPath2
 */

/*
 * Converted from Class-Path2.sh, originally written by Martin Buchholz.
 *
 * For the last version of the original, Class-Path2.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/Class-Path2.sh
 *
 * This class provides additional tests for the Class-Path attribute in jar
 * files, when the entries are not in the same directory.
 */


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ClassPath2 extends Util {
    public static void main(String... args) throws Exception {
        new ClassPath2().run(args);
    }

    void run(String... args) throws Exception {
        setup();
        tests();
        cleanup();
        bottomLine();
    }

    void setup() throws Exception {
        cleanup();

        tb.createDirectories("pkg");

        /*----------------------------------------------------------------
         * Create mutually referential jar files
         *----------------------------------------------------------------*/

        Files.writeString(Path.of("pkg/A.java"), """
                package pkg;
                import pkg.B;
                public class A {
                    public static int f() { return B.g(); }
                    public static int g() { return 0; }
                }
                """);
        Files.writeString(Path.of("pkg/B.java"), """
                package pkg;
                import pkg.A;
                public class B {
                    public static int f() { return A.g(); }
                    public static int g() { return 0; }
                }
                """);

        javac("pkg/A.java", "pkg/B.java");

        makeManifestWithClassPath("./sub/B.zip");
        jar("cmf", "MANIFEST.MF", "A.jar", "pkg/A.class");

        makeManifestWithClassPath("../A.jar");
        jar("cmf", "MANIFEST.MF", "B.zip", "pkg/B.class");

        Files.writeString(Path.of("Main.java"), """
                import pkg.*;
                public class Main {
                    public static void main(String[] a) { System.exit(A.f() + B.f()); }
                }
                """);

        deleteFiles("pkg");
        tb.createDirectories("jars");
        tb.createDirectories("jars/sub");
        tb.moveFile("A.jar", "jars/.");
        tb.moveFile("B.zip", "jars/sub/.");
    }

    void cleanup() throws IOException {
        deleteFiles("pkg", "Main.java", "Main.class", "Main.jar", "jars");
        deleteFiles("MANIFEST.MF", "A.jar", "B.zip");
    }

    void tests() throws Exception {

        /*
         * Test 1: Compiling
         */

        expectPass(JAVAC, "-cp jars/A.jar Main.java");
        expectPass(JAVA, "-cp jars/A.jar${PS}. Main");

        expectPass(JAVAC, "-cp jars/sub/B.zip Main.java");
        expectPass(JAVA, "-cp jars/sub/B.zip${PS}. Main");

    }
}
