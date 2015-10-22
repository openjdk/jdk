/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Test Command Completion
 * @library /tools/lib
 * @build ReplToolTesting TestingInputStream Compiler ToolBox
 * @run testng CommandCompletionTest
 */

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.Test;

@Test
public class CommandCompletionTest extends ReplToolTesting {

    public void testCommand() {
        assertCompletion("/f|", false, "/feedback ");
        assertCompletion("/deb|", false);
        assertCompletion("/feedback v|", false, "verbose");
        assertCompletion("/c|", false, "/classes ", "/classpath ");
        assertCompletion("/h|", false, "/help ", "/history ");
        assertCompletion("/feedback |", false,
                "?", "concise", "default", "normal", "off", "verbose");
    }

    public void testList() {
        assertCompletion("/l|", false, "/list ");
        assertCompletion("/list |", false, "all");
        assertCompletion("/list q|", false);
    }

    public void testDrop() {
        assertCompletion("/d|", false, "/drop ");

        test(false, new String[] {"-nostartup"},
                a -> assertClass(a, "class cTest {}", "class", "cTest"),
                a -> assertMethod(a, "int mTest() { return 0; }", "()I", "mTest"),
                a -> assertVariable(a, "int", "fTest"),
                a -> assertCompletion(a, "/drop |", false, "1", "2", "3", "cTest", "fTest", "mTest"),
                a -> assertCompletion(a, "/drop f|", false, "fTest")
        );
    }

    public void testEdit() {
        assertCompletion("/e|", false, "/edit ", "/exit ");
        assertCompletion("/ed|", false, "/edit ");

        test(false, new String[]{"-nostartup"},
                a -> assertClass(a, "class cTest {}", "class", "cTest"),
                a -> assertMethod(a, "int mTest() { return 0; }", "()I", "mTest"),
                a -> assertVariable(a, "int", "fTest"),
                a -> assertCompletion(a, "/edit |", false, "1", "2", "3", "cTest", "fTest", "mTest"),
                a -> assertCompletion(a, "/edit f|", false, "fTest")
        );
    }

    public void testOpen() throws IOException {
        Compiler compiler = new Compiler();
        assertCompletion("/o|", false, "/open ");
        List<String> p1 = listFiles(Paths.get(""));
        FileSystems.getDefault().getRootDirectories().forEach(s -> p1.add(s.toString()));
        Collections.sort(p1);
        assertCompletion("/open |", false, p1.toArray(new String[p1.size()]));
        Path classDir = compiler.getClassDir();
        List<String> p2 = listFiles(classDir);
        assertCompletion("/open " + classDir + "/|", false, p2.toArray(new String[p2.size()]));
    }

    public void testSave() throws IOException {
        Compiler compiler = new Compiler();
        assertCompletion("/s|", false, "/save ", "/savestart ", "/seteditor ", "/setstart ");
        List<String> p1 = listFiles(Paths.get(""));
        Collections.addAll(p1, "all ", "history ");
        FileSystems.getDefault().getRootDirectories().forEach(s -> p1.add(s.toString()));
        Collections.sort(p1);
        assertCompletion("/save |", false, p1.toArray(new String[p1.size()]));
        Path classDir = compiler.getClassDir();
        List<String> p2 = listFiles(classDir);
        assertCompletion("/save " + classDir + "/|",
                false, p2.toArray(new String[p2.size()]));
        assertCompletion("/save all " + classDir + "/|",
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
}
