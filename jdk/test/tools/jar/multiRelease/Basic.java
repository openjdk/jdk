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
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.jartool
 * @build jdk.test.lib.JDKToolFinder jdk.test.lib.Utils
 * @run testng Basic
 */

import static org.testng.Assert.*;

import org.testng.annotations.*;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.*;

import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.Utils;


import static java.lang.String.format;
import static java.lang.System.out;

public class Basic {
    private final String src = System.getProperty("test.src", ".");
    private final String usr = System.getProperty("user.dir", ".");

    @Test
    // create a regular, non-multi-release jar
    public void test00() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, false);

        Map<String,String[]> names = Map.of(
                "version/Main.class",
                new String[] {"base", "version", "Main.class"},

                "version/Version.class",
                new String[] {"base", "version", "Version.class"}
        );

        compare(jarfile, names);

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // create a multi-release jar
    public void test01() throws IOException {
        String jarfile = "test.jar";

        compile("test01");

        Path classes = Paths.get("classes");
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, true);

        Map<String,String[]> names = Map.of(
                "version/Main.class",
                new String[] {"base", "version", "Main.class"},

                "version/Version.class",
                new String[] {"base", "version", "Version.class"},

                "META-INF/versions/9/version/Version.class",
                new String[] {"v9", "version", "Version.class"},

                "META-INF/versions/10/version/Version.class",
                new String[] {"v10", "version", "Version.class"}
        );

        compare(jarfile, names);

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // update a regular jar to a multi-release jar
    public void test02() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, false);

        jar("uf", jarfile, "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, true);

        Map<String,String[]> names = Map.of(
                "version/Main.class",
                new String[] {"base", "version", "Main.class"},

                "version/Version.class",
                new String[] {"base", "version", "Version.class"},

                "META-INF/versions/9/version/Version.class",
                new String[] {"v9", "version", "Version.class"}
        );

        compare(jarfile, names);

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // replace a base entry and a versioned entry
    public void test03() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, true);

        Map<String,String[]> names = Map.of(
                "version/Main.class",
                new String[] {"base", "version", "Main.class"},

                "version/Version.class",
                new String[] {"base", "version", "Version.class"},

                "META-INF/versions/9/version/Version.class",
                new String[] {"v9", "version", "Version.class"}
        );

        compare(jarfile, names);

        // write the v9 version/Version.class entry in base and the v10
        // version/Version.class entry in versions/9 section
        jar("uf", jarfile, "-C", classes.resolve("v9").toString(), "version",
                "--release", "9", "-C", classes.resolve("v10").toString(), ".")
                .assertSuccess();

        checkMultiRelease(jarfile, true);

        names = Map.of(
                "version/Main.class",
                new String[] {"base", "version", "Main.class"},

                "version/Version.class",
                new String[] {"v9", "version", "Version.class"},

                "META-INF/versions/9/version/Version.class",
                new String[] {"v10", "version", "Version.class"}
        );

        compare(jarfile, names);

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    /*
     * The following tests exercise the jar validator
     */

    @Test
    // META-INF/versions/9 class has different api than base class
    public void test04() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // replace the v9 class
        Path source = Paths.get(src, "data", "test04", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Version.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertFailure()
                .resultChecker(r ->
                    assertTrue(r.output.contains("different api from earlier"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // META-INF/versions/9 contains an extra public class
    public void test05() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add the new v9 class
        Path source = Paths.get(src, "data", "test05", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Extra.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertFailure()
                .resultChecker(r ->
                        assertTrue(r.output.contains("contains a new public class"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // META-INF/versions/9 contains an extra package private class -- this is okay
    public void test06() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add the new v9 class
        Path source = Paths.get(src, "data", "test06", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Extra.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess();

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // META-INF/versions/9 contains an identical class to base entry class
    // this is okay but produces warning
    public void test07() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add the new v9 class
        Path source = Paths.get(src, "data", "test01", "base", "version");
        javac(classes.resolve("v9"), source.resolve("Version.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess()
                .resultChecker(r ->
                        assertTrue(r.outputContains("contains a class that is identical"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // resources with same name in different versions
    // this is okay but produces warning
    public void test08() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add a resource to the base
        Path source = Paths.get(src, "data", "test01", "base", "version");
        Files.copy(source.resolve("Version.java"), classes.resolve("base")
                .resolve("version").resolve("Version.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess()
                .resultChecker(r ->
                        assertTrue(r.output.isEmpty(), r.output)
                );

        // now add a different resource with same name to META-INF/version/9
        Files.copy(source.resolve("Main.java"), classes.resolve("v9")
                .resolve("version").resolve("Version.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess()
                .resultChecker(r ->
                        assertTrue(r.output.contains("multiple resources with same name"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // a class with an internal name different from the external name
    public void test09() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        Path base = classes.resolve("base").resolve("version");

        Files.copy(base.resolve("Main.class"), base.resolve("Foo.class"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertFailure()
                .resultChecker(r ->
                        assertTrue(r.output.contains("names do not match"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // assure that basic nested classes are acceptable
    public void test10() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add a base class with a nested class
        Path source = Paths.get(src, "data", "test10", "base", "version");
        javac(classes.resolve("base"), source.resolve("Nested.java"));

        // add a versioned class with a nested class
        source = Paths.get(src, "data", "test10", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Nested.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertSuccess();

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // a base entry contains a nested class that doesn't have a matching top level class
    public void test11() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add a base class with a nested class
        Path source = Paths.get(src, "data", "test10", "base", "version");
        javac(classes.resolve("base"), source.resolve("Nested.java"));

        // remove the top level class, thus isolating the nested class
        Files.delete(classes.resolve("base").resolve("version").resolve("Nested.class"));

        // add a versioned class with a nested class
        source = Paths.get(src, "data", "test10", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Nested.java"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertFailure()
                .resultChecker(r -> {
                    String[] msg = r.output.split("\\R");
                    // There should be 3 error messages, cascading from the first.  Once we
                    // remove the base top level class, the base nested class becomes isolated,
                    // also the versioned top level class becomes a new public class, thus ignored
                    // for subsequent checks, leading to the associated versioned nested class
                    // becoming an isolated nested class
                    assertTrue(msg.length == 4);
                    assertTrue(msg[0].contains("an isolated nested class"), msg[0]);
                    assertTrue(msg[1].contains("contains a new public class"), msg[1]);
                    assertTrue(msg[2].contains("an isolated nested class"), msg[2]);
                    assertTrue(msg[3].contains("invalid multi-release jar file"), msg[3]);
                });

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    @Test
    // a versioned entry contains a nested class that doesn't have a matching top level class
    public void test12() throws IOException {
        String jarfile = "test.jar";

        compile("test01");  //use same data as test01

        Path classes = Paths.get("classes");

        // add a base class with a nested class
        Path source = Paths.get(src, "data", "test10", "base", "version");
        javac(classes.resolve("base"), source.resolve("Nested.java"));

        // add a versioned class with a nested class
        source = Paths.get(src, "data", "test10", "v9", "version");
        javac(classes.resolve("v9"), source.resolve("Nested.java"));

        // remove the top level class, thus isolating the nested class
        Files.delete(classes.resolve("v9").resolve("version").resolve("Nested.class"));

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "9", "-C", classes.resolve("v9").toString(), ".")
                .assertFailure()
                .resultChecker(r ->
                        assertTrue(r.outputContains("an isolated nested class"), r.output)
                );

        delete(jarfile);
        deleteDir(Paths.get(usr, "classes"));
    }

    /*
     *  Test Infrastructure
     */
    private void compile(String test) throws IOException {
        Path classes = Paths.get(usr, "classes", "base");
        Files.createDirectories(classes);
        Path source = Paths.get(src, "data", test, "base", "version");
        javac(classes, source.resolve("Main.java"), source.resolve("Version.java"));

        classes = Paths.get(usr, "classes", "v9");
        Files.createDirectories(classes);
        source = Paths.get(src, "data", test, "v9", "version");
        javac(classes, source.resolve("Version.java"));

        classes = Paths.get(usr, "classes", "v10");
        Files.createDirectories(classes);
        source = Paths.get(src, "data", test, "v10", "version");
        javac(classes, source.resolve("Version.java"));
    }

    private void checkMultiRelease(String jarFile, boolean expected) throws IOException {
        try (JarFile jf = new JarFile(new File(jarFile), true, ZipFile.OPEN_READ,
                JarFile.runtimeVersion())) {
            assertEquals(jf.isMultiRelease(), expected);
        }
    }

    // compares the bytes found in the jar entries with the bytes found in the
    // corresponding data files used to create the entries
    private void compare(String jarfile, Map<String,String[]> names) throws IOException {
        try (JarFile jf = new JarFile(jarfile)) {
            for (String name : names.keySet()) {
                Path path = Paths.get("classes", names.get(name));
                byte[] b1 = Files.readAllBytes(path);
                byte[] b2;
                JarEntry je = jf.getJarEntry(name);
                try (InputStream is = jf.getInputStream(je)) {
                    b2 = is.readAllBytes();
                }
                assertEquals(b1,b2);
            }
        }
    }

    private void delete(String name) throws IOException {
        Files.deleteIfExists(Paths.get(usr, name));
    }

    private void deleteDir(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /*
     * The following methods were taken from modular jar and other jar tests
     */

    void javac(Path dest, Path... sourceFiles) throws IOException {
        String javac = JDKToolFinder.getJDKTool("javac");

        List<String> commands = new ArrayList<>();
        commands.add(javac);
        String opts = System.getProperty("test.compiler.opts");
        if (!opts.isEmpty()) {
            commands.addAll(Arrays.asList(opts.split(" +")));
        }
        commands.add("-d");
        commands.add(dest.toString());
        Stream.of(sourceFiles).map(Object::toString).forEach(x -> commands.add(x));

        quickFail(run(new ProcessBuilder(commands)));
    }

    Result jarWithStdin(File stdinSource, String... args) {
        String jar = JDKToolFinder.getJDKTool("jar");
        List<String> commands = new ArrayList<>();
        commands.add(jar);
        commands.addAll(Utils.getForwardVmOptions());
        Stream.of(args).forEach(x -> commands.add(x));
        ProcessBuilder p = new ProcessBuilder(commands);
        if (stdinSource != null)
            p.redirectInput(stdinSource);
        return run(p);
    }

    Result jar(String... args) {
        return jarWithStdin(null, args);
    }

    void quickFail(Result r) {
        if (r.ec != 0)
            throw new RuntimeException(r.output);
    }

    Result run(ProcessBuilder pb) {
        Process p;
        out.printf("Running: %s%n", pb.command());
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new RuntimeException(
                    format("Couldn't start process '%s'", pb.command()), e);
        }

        String output;
        try {
            output = toString(p.getInputStream(), p.getErrorStream());
        } catch (IOException e) {
            throw new RuntimeException(
                    format("Couldn't read process output '%s'", pb.command()), e);
        }

        try {
            p.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(
                    format("Process hasn't finished '%s'", pb.command()), e);
        }
        return new Result(p.exitValue(), output);
    }

    String toString(InputStream in1, InputStream in2) throws IOException {
        try (ByteArrayOutputStream dst = new ByteArrayOutputStream();
             InputStream concatenated = new SequenceInputStream(in1, in2)) {
            concatenated.transferTo(dst);
            return new String(dst.toByteArray(), "UTF-8");
        }
    }

    static class Result {
        final int ec;
        final String output;

        private Result(int ec, String output) {
            this.ec = ec;
            this.output = output;
        }

        boolean outputContains(String msg) {
            return Arrays.stream(output.split("\\R"))
                         .collect(Collectors.joining(" "))
                         .contains(msg);
        }

        Result assertSuccess() {
            assertTrue(ec == 0, format("ec: %d, output: %s", ec, output));
            return this;
        }
        Result assertFailure() {
            assertTrue(ec != 0, format("ec: %d, output: %s", ec, output));
            return this;
        }
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }
}
