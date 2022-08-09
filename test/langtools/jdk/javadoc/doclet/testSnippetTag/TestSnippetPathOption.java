/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8266666
 * @summary Implementation for snippets
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestSnippetPathOption
 */

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestSnippetPathOption extends SnippetTester {

    public static void main(String... args) throws Exception {
        new TestSnippetPathOption().runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    /*
        #   snippet-files   snippet-path   result
       ---+--------------+--------------+---------------------
        1         +              +         snippet-files
        2         +           invalid      snippet-files
        3         -              +         snippet-path
        4         -           invalid      error
     */

    @Test
    public void test1(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        tb.createDirectories(src.resolve("directoryA"), src.resolve("directoryB"));
        tb.writeFile(src.resolve("directoryA/mysnippet.txt"), "Hello, directoryA!");
        tb.writeFile(src.resolve("directoryB/mysnippet.txt"), "Hello, directoryB!");
        tb.writeFile(src.resolve("pkg/snippet-files/mysnippet.txt"), "Hello, snippet-files!");
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet file="mysnippet.txt"} */
                               public class X { }
                               """);
        String snippetPathValue = Stream.of("directoryA", "directoryB")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/X.html", true, "Hello, snippet-files!");
        checkOutput("pkg/X.html", false, "Hello, directoryA!");
        checkOutput("pkg/X.html", false, "Hello, directoryB!");
    }

    @Test
    public void test2(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        tb.createDirectories(src.resolve("directoryA"), src.resolve("directoryB"));
        tb.writeFile(src.resolve("pkg/snippet-files/mysnippet.txt"), "Hello, snippet-files!");
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet file="mysnippet.txt"} */
                               public class X { }
                               """);
        String snippetPathValue = Stream.of("directoryA", "directoryB")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/X.html", true, "Hello, snippet-files!");
    }

    @Test
    public void test3(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        tb.createDirectories(src.resolve("directoryA"), src.resolve("directoryB"));
        tb.writeFile(src.resolve("directoryA/mysnippet.txt"), "Hello, directoryA!");
        tb.writeFile(src.resolve("directoryB/mysnippet.txt"), "Hello, directoryB!");
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet file="mysnippet.txt"} */
                               public class X { }
                               """);
        String snippetPathValue = Stream.of("directoryA", "directoryB")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/X.html", true, "Hello, directoryA!");
        checkOutput("pkg/X.html", false, "Hello, directoryB!");
    }

    @Test
    public void test4(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet file="mysnippet.txt"} */
                               public class X { }
                               """);
        String snippetPathValue = Stream.of("directoryA", "directoryB")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.ERROR);
    }

    /*
     * Tests that the elements of the snippet path are iteratively searched
     * until the file is found. In particular, tests that if the file is not
     * immediately found, the search is not abandoned.
     */
    @Test
    public void testSearchPath(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        tb.createDirectories(src.resolve("directoryA"), src.resolve("directoryB"));
        // do not put snippet in directoryA; only put snippet in directoryB
        tb.writeFile(src.resolve("directoryB/mysnippet.txt"), "Hello, directoryB!");
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet file="mysnippet.txt"} */
                               public class X { }
                               """);
        // directoryA goes first, assuming that paths are searched in
        // the same order they are specified in
        String snippetPathValue = Stream.of("directoryA", "directoryB")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/X.html", true, "Hello, directoryB!");
    }

    /*
     * Tests translation from FQN (the "class" attribute) to file path
     * (the "file" attribute).
     */
    @Test
    public void testClassToFile(Path base) throws Exception {
        Path src = Files.createDirectories(base.resolve("src"));
        Path directoryA = Files.createDirectories(src.resolve("directoryA"));
        tb.writeJavaFiles(directoryA, """
                                      package com.example.snippet;

                                      public interface Y { }
                                      """);
        tb.writeJavaFiles(src, """
                               package pkg;

                               /** {@snippet class="com.example.snippet.Y"} */
                               public class X { }
                               """);
        String snippetPathValue = Stream.of("directoryA")
                .map(src::resolve)
                .map(Path::toAbsolutePath)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
        javadoc("-d", base.resolve("out").toString(),
                "--snippet-path", snippetPathValue,
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);
        checkOutput("pkg/X.html", true, "public interface Y { }");
    }
}
