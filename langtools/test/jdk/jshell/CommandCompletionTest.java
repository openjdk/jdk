/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8144095 8164825 8169818 8153402
 * @summary Test Command Completion
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.internal.jshell.tool
 * @library /tools/lib
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build ReplToolTesting TestingInputStream Compiler
 * @run testng CommandCompletionTest
 */

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.testng.annotations.Test;

@Test
public class CommandCompletionTest extends ReplToolTesting {

    public void testCommand() {
        assertCompletion("/deb|", false);
        assertCompletion("/re|", false, "/reload ", "/reset ");
        assertCompletion("/h|", false, "/help ", "/history ");
    }

    public void testList() {
        test(false, new String[] {"--no-startup"},
                a -> assertCompletion(a, "/l|", false, "/list "),
                a -> assertCompletion(a, "/list |", false, "-all", "-history", "-start "),
                a -> assertCompletion(a, "/list -h|", false, "-history"),
                a -> assertCompletion(a, "/list q|", false),
                a -> assertVariable(a, "int", "xray"),
                a -> assertCompletion(a, "/list |", false, "-all", "-history", "-start ", "1 ", "xray "),
                a -> assertCompletion(a, "/list x|", false, "xray "),
                a -> assertCompletion(a, "/list xray |", false)
        );
    }

    public void testDrop() {
        test(false, new String[] {"--no-startup"},
                a -> assertCompletion(a, "/d|", false, "/drop "),
                a -> assertClass(a, "class cTest {}", "class", "cTest"),
                a -> assertMethod(a, "int mTest() { return 0; }", "()I", "mTest"),
                a -> assertVariable(a, "int", "fTest"),
                a -> assertCompletion(a, "/drop |", false, "1 ", "2 ", "3 ", "cTest ", "fTest ", "mTest "),
                a -> assertCompletion(a, "/drop f|", false, "fTest ")
        );
    }

    public void testEdit() {
        test(false, new String[]{"--no-startup"},
                a -> assertCompletion(a, "/e|", false, "/edit ", "/exit "),
                a -> assertCompletion(a, "/ed|", false, "/edit "),
                a -> assertClass(a, "class cTest {}", "class", "cTest"),
                a -> assertMethod(a, "int mTest() { return 0; }", "()I", "mTest"),
                a -> assertVariable(a, "int", "fTest"),
                a -> assertCompletion(a, "/edit |", false,
                        "-all" , "-start " , "1 ", "2 ", "3 ", "cTest ", "fTest ", "mTest "),
                a -> assertCompletion(a, "/edit cTest |", false,
                        "2 ", "3 ", "fTest ", "mTest "),
                a -> assertCompletion(a, "/edit 1 fTest |", false,
                        "2 ", "mTest "),
                a -> assertCompletion(a, "/edit f|", false, "fTest "),
                a -> assertCompletion(a, "/edit mTest f|", false, "fTest ")
        );
    }

    public void testHelp() {
        assertCompletion("/help |", false,
                "/! ", "/-<n> ", "/<id> ", "/? ", "/classpath ", "/drop ",
                "/edit ", "/exit ", "/help ", "/history ", "/imports ",
                "/list ", "/methods ", "/open ", "/reload ", "/reset ",
                "/save ", "/set ", "/types ", "/vars ", "intro ", "shortcuts ");
        assertCompletion("/? |", false,
                "/! ", "/-<n> ", "/<id> ", "/? ", "/classpath ", "/drop ",
                "/edit ", "/exit ", "/help ", "/history ", "/imports ",
                "/list ", "/methods ", "/open ", "/reload ", "/reset ",
                "/save ", "/set ", "/types ", "/vars ", "intro ", "shortcuts ");
        assertCompletion("/help /s|", false,
                "/save ", "/set ");
        assertCompletion("/help /set |", false,
                "editor", "feedback", "format", "mode", "prompt", "start", "truncation");
        assertCompletion("/help /edit |", false);
    }

    public void testReload() {
        assertCompletion("/reload |", false, "-quiet ", "-restore ");
        assertCompletion("/reload -restore |", false, "-quiet");
        assertCompletion("/reload -quiet |", false, "-restore");
        assertCompletion("/reload -restore -quiet |", false);
    }

    public void testVarsMethodsTypes() {
        test(false, new String[]{"--no-startup"},
                a -> assertCompletion(a, "/v|", false, "/vars "),
                a -> assertCompletion(a, "/m|", false, "/methods "),
                a -> assertCompletion(a, "/t|", false, "/types "),
                a -> assertClass(a, "class cTest {}", "class", "cTest"),
                a -> assertMethod(a, "int mTest() { return 0; }", "()I", "mTest"),
                a -> assertVariable(a, "int", "fTest"),
                a -> assertCompletion(a, "/vars |", false, "-all", "-start ", "3 ", "fTest "),
                a -> assertCompletion(a, "/meth |", false, "-all", "-start ", "2 ", "mTest "),
                a -> assertCompletion(a, "/typ |", false, "-all", "-start ", "1 ", "cTest "),
                a -> assertCompletion(a, "/var f|", false, "fTest ")
        );
    }

    public void testOpen() throws IOException {
        Compiler compiler = new Compiler();
        assertCompletion("/o|", false, "/open ");
        List<String> p1 = listFiles(Paths.get(""));
        getRootDirectories().forEach(s -> p1.add(s.toString()));
        Collections.sort(p1);
        assertCompletion("/open |", false, p1.toArray(new String[p1.size()]));
        Path classDir = compiler.getClassDir();
        List<String> p2 = listFiles(classDir);
        assertCompletion("/open " + classDir + "/|", false, p2.toArray(new String[p2.size()]));
    }

    public void testSave() throws IOException {
        Compiler compiler = new Compiler();
        assertCompletion("/s|", false, "/save ", "/set ");
        List<String> p1 = listFiles(Paths.get(""));
        Collections.addAll(p1, "-all ", "-history ", "-start ");
        getRootDirectories().forEach(s -> p1.add(s.toString()));
        Collections.sort(p1);
        assertCompletion("/save |", false, p1.toArray(new String[p1.size()]));
        Path classDir = compiler.getClassDir();
        List<String> p2 = listFiles(classDir);
        assertCompletion("/save " + classDir + "/|",
                false, p2.toArray(new String[p2.size()]));
        assertCompletion("/save -all " + classDir + "/|",
                false, p2.toArray(new String[p2.size()]));
    }

    public void testClassPath() throws IOException {
        assertCompletion("/classp|", false, "/classpath ");
        Compiler compiler = new Compiler();
        Path outDir = compiler.getPath("testClasspathCompletion");
        Files.createDirectories(outDir);
        Files.createDirectories(outDir.resolve("dir"));
        createIfNeeded(outDir.resolve("test.jar"));
        createIfNeeded(outDir.resolve("test.zip"));
        compiler.compile(outDir, "package pkg; public class A { public String toString() { return \"A\"; } }");
        String jarName = "test.jar";
        compiler.jar(outDir, jarName, "pkg/A.class");
        compiler.getPath(outDir).resolve(jarName);
        List<String> paths = listFiles(outDir, CLASSPATH_FILTER);
        assertCompletion("/classpath " + outDir + "/|", false, paths.toArray(new String[paths.size()]));
    }

    public void testUserHome() throws IOException {
        List<String> completions;
        Path home = Paths.get(System.getProperty("user.home"));
        try (Stream<Path> content = Files.list(home)) {
            completions = content.filter(CLASSPATH_FILTER)
                                 .map(file -> file.getFileName().toString() + (Files.isDirectory(file) ? "/" : ""))
                                 .sorted()
                                 .collect(Collectors.toList());
        }
        assertCompletion("/classpath ~/|", false, completions.toArray(new String[completions.size()]));
    }

    public void testSet() throws IOException {
        List<String> p1 = listFiles(Paths.get(""));
        getRootDirectories().forEach(s -> p1.add(s.toString()));
        Collections.sort(p1);

        String[] modes = {"concise ", "normal ", "silent ", "verbose "};
        String[] options = {"-command", "-delete", "-quiet"};
        String[] modesWithOptions = Stream.concat(Arrays.stream(options), Arrays.stream(modes)).sorted().toArray(String[]::new);
        test(false, new String[] {"--no-startup"},
                a -> assertCompletion(a, "/se|", false, "/set "),
                a -> assertCompletion(a, "/set |", false, "editor ", "feedback ", "format ", "mode ", "prompt ", "start ", "truncation "),

                // /set editor
                a -> assertCompletion(a, "/set e|", false, "editor "),
                a -> assertCompletion(a, "/set editor |", false, p1.toArray(new String[p1.size()])),

                // /set feedback
                a -> assertCompletion(a, "/set fe|", false, "feedback "),
                a -> assertCompletion(a, "/set fe |", false, modes),

                // /set format
                a -> assertCompletion(a, "/set fo|", false, "format "),
                a -> assertCompletion(a, "/set fo |", false, modes),

                // /set mode
                a -> assertCompletion(a, "/set mo|", false, "mode "),
                a -> assertCompletion(a, "/set mo |", false),
                a -> assertCompletion(a, "/set mo newmode |", false, modesWithOptions),
                a -> assertCompletion(a, "/set mo newmode -|", false, options),
                a -> assertCompletion(a, "/set mo newmode -command |", false),
                a -> assertCompletion(a, "/set mo newmode normal |", false, options),

                // /set prompt
                a -> assertCompletion(a, "/set pro|", false, "prompt "),
                a -> assertCompletion(a, "/set pro |", false, modes),

                // /set start
                a -> assertCompletion(a, "/set st|", false, "start "),
                a -> assertCompletion(a, "/set st |", false, p1.toArray(new String[p1.size()])),

                // /set truncation
                a -> assertCompletion(a, "/set tr|", false, "truncation "),
                a -> assertCompletion(a, "/set tr |", false, modes)
        );
    }

    private void createIfNeeded(Path file) throws IOException {
        if (!Files.exists(file))
            Files.createFile(file);
    }
    private List<String> listFiles(Path path) throws IOException {
        return listFiles(path, ACCEPT_ALL);
    }

    private List<String> listFiles(Path path, Predicate<? super Path> filter) throws IOException {
        try (Stream<Path> stream = Files.list(path)) {
            return stream.filter(filter)
                         .map(p -> p.getFileName().toString() + (Files.isDirectory(p) ? "/" : ""))
                         .sorted()
                         .collect(Collectors.toList());
        }
    }

    private static final Predicate<? super Path> ACCEPT_ALL =
            (file) -> !file.endsWith(".") && !file.endsWith("..");

    private static final Predicate<? super Path> CLASSPATH_FILTER =
            (file) -> ACCEPT_ALL.test(file) &&
                    (Files.isDirectory(file) ||
                     file.getFileName().toString().endsWith(".jar") ||
                     file.getFileName().toString().endsWith(".zip"));

    private static Iterable<? extends Path> getRootDirectories() {
        return StreamSupport.stream(FileSystems.getDefault()
                                               .getRootDirectories()
                                               .spliterator(),
                                    false)
                            .filter(p -> Files.exists(p))
                            .collect(Collectors.toList());
    }
}
