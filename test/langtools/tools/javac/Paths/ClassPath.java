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
 * @bug 4212732 8293877
 * @summary Test handling of the Class-Path attribute in jar file manifests
 * @library /tools/lib
 * @build toolbox.ToolBox Util ClassPath
 * @run main ClassPath
*/


/*
 * Converted from Class-Path.sh, originally written by Martin Buchholz.
 *
 * For the last version of the original, Class-Path.sh, see
 * https://git.openjdk.org/jdk/blob/jdk-19%2B36/test/langtools/tools/javac/Paths/Class-Path.sh
 *
 * This class primarily tests that the Class-Path attribute in jar files
 * is handled the same way by javac and java. It also has various tests
 * of the jar tool itself.
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public class ClassPath extends Util {
    public static void main(String... args) throws Exception {
        new ClassPath().run(args);
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

        makeManifestWithClassPath("B.zip");
        jar("cmf", "MANIFEST.MF", "A.jar", "pkg/A.class");

        makeManifestWithClassPath("A.jar");
        jar("cmf", "MANIFEST.MF", "B.zip", "pkg/B.class");

        Files.writeString(Path.of("Main.java"), """
                import pkg.*;
                public class Main {
                    public static void main(String[] a) { System.exit(A.f() + B.f()); }
                }
                """);
    }

    void cleanup() throws IOException {
        deleteFiles("pkg", "Main.java", "Main.class", "Main.jar", "jars");
        deleteFiles("MANIFEST.MF", "A.jar", "B.zip");
    }

    void tests() throws Exception {
        expectPass(JAVAC, "-cp A.jar Main.java");
        expectPass(JAVAC, "-cp B.zip Main.java");
        expectPass(JAVA, "-cp A.jar${PS}. Main");
        expectPass(JAVA, "-cp B.zip${PS}. Main");

        /*----------------------------------------------------------------
         * Jar file Class-Path expanded only for jars found on user class path
         *----------------------------------------------------------------*/

        tb.createDirectories("jars");
        moveFiles(List.of("A.jar", "B.zip"), "jars/.");

        expectPass(JAVAC, "-cp jars/A.jar Main.java");
        expectPass(JAVA, "-cp jars/A.jar${PS}. Main");

        expectPass(JAVAC, "-cp jars/B.zip Main.java");
        expectPass(JAVA, "-cp jars/B.zip${PS}. Main");

        expectFail(JAVA, "-Xbootclasspath/p:jars/A.jar -cp .    Main");
        expectFail(JAVA, "-Xbootclasspath/a:jars/B.zip -cp .    Main");
        expectFail(JAVAC, "-Xbootclasspath/p:jars/A.jar -cp None Main.java");
        expectFail(JAVAC, "-Xbootclasspath/a:jars/B.zip -cp None Main.java");
        moveFiles(List.of("jars/A.jar", "jars/B.zip"), ".");

        makeManifestWithClassPath("A.jar");
        Files.writeString(Path.of("MANIFEST.MF"), "Main-Class: Main\n", StandardOpenOption.APPEND);
        jar("cmf", "MANIFEST.MF", "Main.jar", "Main.class");

        expectPass(JAVA, "-jar Main.jar");

        makeManifestWithClassPath(".");
        jar("cmf", "MANIFEST.MF", "A.jar", "pkg/A.class");

        expectPass(JAVAC, "-cp A.jar Main.java");
        expectPass(JAVA, "-jar Main.jar");

        makeManifestWithClassPath("");
        jar("cmf", "MANIFEST.MF", "A.jar", "pkg/A.class");

        expectFail(JAVAC, "-cp A.jar Main.java");
        expectFail(JAVA, "-jar Main.jar");

        /*----------------------------------------------------------------
         * Test new flag -e (application entry point)
         *----------------------------------------------------------------*/

        Files.writeString(Path.of("Hello.java"), """
                import pkg.*;
                public class Hello {
                    public static void main(String[] a) { System.out.println("Hello World!"); }
                }
                """);

        Files.writeString(Path.of("Bye.java"), """
                import pkg.*;
                public class Bye {
                    public static void main(String[] a) { System.out.println("Good Bye!"); }
                }
                """);

        // Set an empty classpath to override any inherited setting of CLASSPATH
        expectPass(classpath(""), JAVAC, "Hello.java Bye.java");

        // test jar creation without manifest
        //
        expectPass(JAR, "cfe Hello.jar Hello Hello.class");
        expectPass(JAVA, "-jar Hello.jar");

        // test for overriding the manifest during jar creation
        //
        Files.writeString(Path.of("MANIFEST.MF"), "Main-Class: Hello\n", StandardOpenOption.APPEND);

        // test for error: " 'e' flag and manifest with the 'Main-Class'
        // attribute cannot be specified together, during creation
        expectFail(JAR, "cmfe MANIFEST.MF Bye.jar Bye Bye.class");

        // test for overriding the manifest when updating the jar
        //
        expectPass(JAR, "cfe greetings.jar Hello Hello.class");
        expectPass(JAR, "ufe greetings.jar Bye Bye.class");
        expectPass(JAVA, "-jar greetings.jar");

        // test for error: " 'e' flag and manifest with the 'Main-Class'
        // attribute cannot be specified together, during update
        expectFail(JAR, "umfe MANIFEST.MF greetings.jar Hello");

        // test jar update when there are no input files
        expectPass(JAR, "ufe Hello.jar Bye");
        expectFail(JAVA, "-jar Hello.jar");
        expectPass(JAR, "umf MANIFEST.MF Hello.jar");

        // test creating jar when the to-be-archived files
        // do not contain the specified main class, there is no check done
        // for the presence of the main class, so the test will pass
        //
        expectPass(JAR, "cfe Hello.jar Hello Bye.class");

        // Jar creation and update when there is no manifest and inputfiles
        // specified
        expectFail(JAR, "cvf A.jar");
        expectFail(JAR, "uvf A.jar");

        // error: no such file or directory
        expectFail(JAR, "cvf A.jar non-existing.file");
        expectFail(JAR, "uvf A.jar non-existing.file");

    }
}
