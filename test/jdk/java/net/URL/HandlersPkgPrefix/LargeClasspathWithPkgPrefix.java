/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.JarBuilder;

/*
 * @test
 * @bug 8308184
 * @summary Verify that an application can be launched when the classpath contains large number of
 *          jars and the java.protocol.handler.pkgs system property is set
 * @library /test/lib/
 * @build jdk.test.lib.util.JarBuilder jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.process.ProcessTools
 * @run driver LargeClasspathWithPkgPrefix
 */
public class LargeClasspathWithPkgPrefix {

    private static final Path CWD = Path.of(".");

    private static final String JAVA_MAIN_CONTENT = """
            public class Foo {
                public static void main(String[] args) throws Exception {
                    if (args.length != 0) {
                        System.out.println("unexpected args: " + java.util.Arrays.toString(args));
                        System.exit(1);
                    }
                    System.out.println("Running application on Java version: "
                                + System.getProperty("java.version"));
                    System.out.println("Application launched with java.protocol.handler.pkgs="
                                + System.getProperty("java.protocol.handler.pkgs"));
                    System.out.println("Application launched with classpath: "
                                + System.getProperty("java.class.path"));
                    System.out.println("Hello World");
                }
            }
            """;

    public static void main(final String[] args) throws Exception {
        // dir to which the application main's .class file will be compiled to
        Path classesDir = Files.createTempDirectory(CWD, "8308184-classes").toAbsolutePath();
        // dir contains many jars
        Path libDir = Files.createTempDirectory(CWD, "8308184-libs").toAbsolutePath();
        Files.createDirectories(libDir);

        // trivial jar file
        Path jarPath = Path.of(libDir.toString(), "8308184-dummy.jar");
        createJar(jarPath);

        // create multiple such jar files in the lib dir
        int numCopies = 750;
        long start = System.currentTimeMillis();
        for (int i = 1; i <= numCopies; i++) {
            Path dest = Path.of(libDir.toString(), "8308184-dummy-" + i + ".jar");
            Files.copy(jarPath, dest);
        }
        long end = System.currentTimeMillis();
        System.out.println("Created " + numCopies + " jars under " + libDir
                + ", took " + (end - start) + " milli seconds");

        // create the application's main java file
        Path fooJavaSrcFile = Path.of(classesDir.toString(), "Foo.java");
        Files.writeString(fooJavaSrcFile, JAVA_MAIN_CONTENT);

        // compile this java file
        compile(fooJavaSrcFile, classesDir);

        // Create the classpath string. It is important that the classes directory which contains
        // application's main class, is at the end of the classpath (or too far into the classpath).
        // The initial entries in the classpath should be jar files.
        // constructed classpath is of the form -cp lib/*:classes/
        // (the * in lib/* is parsed/interpreted by the java launcher and includes all jars in that
        // directory)
        String classpath = File.pathSeparator + libDir.toString() + "/*"
                + File.pathSeparator + classesDir.toString();
        // launch the application
        launchApplication(classpath);
        // test passed successfully, we don't need the lib directory which has too many jars,
        // anymore. we let the dir stay only if the test fails, for debug purpose
        libDir.toFile().deleteOnExit();
    }

    // creates a trivial jar file
    private static void createJar(Path p) throws Exception {
        JarBuilder jb = new JarBuilder(p.toString());
        jb.addEntry("foobar.txt", "foobar".getBytes());
        jb.build();
        System.out.println("Created jar at " + p);
    }

    // compile <javaFile> to <destDir>
    private static void compile(Path javaFile, Path destDir) throws Exception {
        boolean compiled = CompilerUtils.compile(javaFile, destDir);
        if (!compiled) {
            // compilation failure log/reason would already be available on System.out/err
            throw new AssertionError("Compilation failed for " + javaFile);
        }
    }

    // java -Djava.protocol.handler.pkgs=foo.bar.some.nonexistent.pkg -cp <classpath> Foo
    private static void launchApplication(String classPath) throws Exception {
        String java = JDKToolFinder.getJDKTool("java");
        ProcessBuilder pb = new ProcessBuilder(java,
                "-Djava.protocol.handler.pkgs=foo.bar.some.nonexistent.pkg",
                "-cp", classPath,
                "Foo");
        pb.directory(CWD.toFile());
        System.out.println("Launching java application: " + pb.command());
        OutputAnalyzer analyzer = ProcessTools.executeProcess(pb);
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("Hello World");
    }
}
