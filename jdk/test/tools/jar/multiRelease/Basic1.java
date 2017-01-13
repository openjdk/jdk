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
 * @run testng Basic1
 */

import static org.testng.Assert.*;

import org.testng.annotations.*;

import java.io.*;
import java.nio.file.*;
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

public class Basic1 {
    private final String src = System.getProperty("test.src", ".");

    @BeforeTest
    public void setup() throws IOException {
        String test = "test01";
        Path classes = Paths.get("classes", "base");
        Files.createDirectories(classes);
        Path source = Paths.get(src, "data", test, "base", "version");
        javac(classes, source.resolve("Main.java"), source.resolve("Version.java"));

        Path v9 = Paths.get("v9");
        Files.createDirectories(v9);
        source = Paths.get(src, "data", test, "v9", "version");
        javac(v9, source.resolve("Version.java"));

        Path v10 = Paths.get("v10");
        Files.createDirectories(v10);
        source = Paths.get(src, "data", test, "v10", "version");
        javac(v10, source.resolve("Version.java"));

        Path v10_1 = Paths.get("v10_1").resolve("META-INF").resolve("versions").resolve("v10");
        Files.createDirectories(v10_1);
        source = Paths.get(src, "data", test, "v10", "version");
        javac(v10_1, source.resolve("Version.java"));
    }

    @Test
    public void test() throws IOException {
        String jarfile = "test.jar";
        Path classes = Paths.get("classes");
        Path v9 = Paths.get("v9");
        Path v10 = Paths.get("v10");

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
            "--release", "9", "-C", v9.toString(), ".",
            "--release", "10", "-C", v10.toString(), ".")
            .assertSuccess();

        checkMultiRelease(jarfile, true);

        Map<String,String[]> names = Map.of(
            "version/Main.class",
            new String[] {"classes", "base", "version", "Main.class"},

            "version/Version.class",
            new String[] {"classes", "base", "version", "Version.class"},

            "META-INF/versions/9/version/Version.class",
            new String[] {"v9", "version", "Version.class"},

            "META-INF/versions/10/version/Version.class",
            new String[] {"v10", "version", "Version.class"}
        );

        compare(jarfile, names);
    }


    @Test
    public void testFail() throws IOException {
        String jarfile = "test.jar";
        Path classes = Paths.get("classes");
        Path v10 = Paths.get("v10_1");

        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
            "--release", "10", "-C", v10.toString(), ".")
            .assertFailure()
            .outputContains("unexpected versioned entry META-INF/versions/");
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
                Path path = Paths.get("", names.get(name));
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
