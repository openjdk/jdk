/**
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
 * @library /lib/testlibrary
 * @modules jdk.jlink/jdk.tools.jmod
 *          jdk.compiler
 * @build jdk.testlibrary.FileUtils CompilerUtils
 * @run testng JmodTest
 * @summary Basic test for jmod
 */

import java.io.*;
import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.testlibrary.FileUtils;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static java.lang.module.ModuleDescriptor.Version;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.testng.Assert.*;

public class JmodTest {

    static final String TEST_SRC = System.getProperty("test.src", ".");
    static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    static final Path EXPLODED_DIR = Paths.get("build");
    static final Path MODS_DIR = Paths.get("jmods");

    static final String CLASSES_PREFIX = "classes/";
    static final String CMDS_PREFIX = "bin/";
    static final String LIBS_PREFIX = "native/";
    static final String CONFIGS_PREFIX = "conf/";

    @BeforeTest
    public void buildExplodedModules() throws IOException {
        if (Files.exists(EXPLODED_DIR))
            FileUtils.deleteFileTreeWithRetry(EXPLODED_DIR);

        for (String name : new String[] { "foo"/*, "bar", "baz"*/ } ) {
            Path dir = EXPLODED_DIR.resolve(name);
            assertTrue(compileModule(name, dir.resolve("classes")));
            createCmds(dir.resolve("bin"));
            createLibs(dir.resolve("lib"));
            createConfigs(dir.resolve("conf"));
        }

        if (Files.exists(MODS_DIR))
            FileUtils.deleteFileTreeWithRetry(MODS_DIR);
        Files.createDirectories(MODS_DIR);
    }

    @Test
    public void testList() throws IOException {
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes").toString();
        jmod("create",
             "--class-path", cp,
             MODS_DIR.resolve("foo.jmod").toString())
            .assertSuccess();

        jmod("list",
             MODS_DIR.resolve("foo.jmod").toString())
            .assertSuccess()
            .resultChecker(r -> {
                // asserts dependent on the exact contents of foo
                assertContains(r.output, CLASSES_PREFIX + "module-info.class");
                assertContains(r.output, CLASSES_PREFIX + "jdk/test/foo/Foo.class");
                assertContains(r.output, CLASSES_PREFIX + "jdk/test/foo/internal/Message.class");
            });
    }

    @Test
    public void testMainClass() throws IOException {
        Path jmod = MODS_DIR.resolve("fooMainClass.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes").toString();

        jmod("create",
             "--class-path", cp,
             "--main-class", "jdk.test.foo.Foo",
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                Optional<String> omc = getModuleDescriptor(jmod).mainClass();
                assertTrue(omc.isPresent());
                assertEquals(omc.get(), "jdk.test.foo.Foo");
            });
    }

    @Test
    public void testModuleVersion() throws IOException {
        Path jmod = MODS_DIR.resolve("fooVersion.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes").toString();

        jmod("create",
             "--class-path", cp,
             "--module-version", "5.4.3",
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                Optional<Version> ov = getModuleDescriptor(jmod).version();
                assertTrue(ov.isPresent());
                assertEquals(ov.get().toString(), "5.4.3");
            });
    }

    @Test
    public void testConfig() throws IOException {
        Path jmod = MODS_DIR.resolve("fooConfig.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        Path cp = EXPLODED_DIR.resolve("foo").resolve("classes");
        Path cf = EXPLODED_DIR.resolve("foo").resolve("conf");

        jmod("create",
             "--class-path", cp.toString(),
             "--config", cf.toString(),
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                try (Stream<String> s1 = findFiles(cf).map(p -> CONFIGS_PREFIX + p);
                     Stream<String> s2 = findFiles(cp).map(p -> CLASSES_PREFIX + p)) {
                    Set<String> expectedFilenames = Stream.concat(s1, s2)
                                                          .collect(toSet());
                    assertJmodContent(jmod, expectedFilenames);
                }
            });
    }

    @Test
    public void testCmds() throws IOException {
        Path jmod = MODS_DIR.resolve("fooCmds.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        Path cp = EXPLODED_DIR.resolve("foo").resolve("classes");
        Path bp = EXPLODED_DIR.resolve("foo").resolve("bin");

        jmod("create",
             "--cmds", bp.toString(),
             "--class-path", cp.toString(),
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                try (Stream<String> s1 = findFiles(bp).map(p -> CMDS_PREFIX + p);
                     Stream<String> s2 = findFiles(cp).map(p -> CLASSES_PREFIX + p)) {
                    Set<String> expectedFilenames = Stream.concat(s1,s2)
                                                          .collect(toSet());
                    assertJmodContent(jmod, expectedFilenames);
                }
            });
    }

    @Test
    public void testLibs() throws IOException {
        Path jmod = MODS_DIR.resolve("fooLibs.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        Path cp = EXPLODED_DIR.resolve("foo").resolve("classes");
        Path lp = EXPLODED_DIR.resolve("foo").resolve("lib");

        jmod("create",
             "--libs=", lp.toString(),
             "--class-path", cp.toString(),
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                try (Stream<String> s1 = findFiles(lp).map(p -> LIBS_PREFIX + p);
                     Stream<String> s2 = findFiles(cp).map(p -> CLASSES_PREFIX + p)) {
                    Set<String> expectedFilenames = Stream.concat(s1,s2)
                                                          .collect(toSet());
                    assertJmodContent(jmod, expectedFilenames);
                }
            });
    }

    @Test
    public void testAll() throws IOException {
        Path jmod = MODS_DIR.resolve("fooAll.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        Path cp = EXPLODED_DIR.resolve("foo").resolve("classes");
        Path bp = EXPLODED_DIR.resolve("foo").resolve("bin");
        Path lp = EXPLODED_DIR.resolve("foo").resolve("lib");
        Path cf = EXPLODED_DIR.resolve("foo").resolve("conf");

        jmod("create",
             "--conf", cf.toString(),
             "--cmds=", bp.toString(),
             "--libs=", lp.toString(),
             "--class-path", cp.toString(),
             jmod.toString())
            .assertSuccess()
            .resultChecker(r -> {
                try (Stream<String> s1 = findFiles(lp).map(p -> LIBS_PREFIX + p);
                     Stream<String> s2 = findFiles(cp).map(p -> CLASSES_PREFIX + p);
                     Stream<String> s3 = findFiles(bp).map(p -> CMDS_PREFIX + p);
                     Stream<String> s4 = findFiles(cf).map(p -> CONFIGS_PREFIX + p)) {
                    Set<String> expectedFilenames = Stream.concat(Stream.concat(s1,s2),
                                                                  Stream.concat(s3, s4))
                                                          .collect(toSet());
                    assertJmodContent(jmod, expectedFilenames);
                }
            });
    }

    @Test
    public void testExcludes() throws IOException {
        Path jmod = MODS_DIR.resolve("fooLibs.jmod");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        Path cp = EXPLODED_DIR.resolve("foo").resolve("classes");
        Path lp = EXPLODED_DIR.resolve("foo").resolve("lib");

        jmod("create",
             "--libs=", lp.toString(),
             "--class-path", cp.toString(),
             "--exclude", "**internal**",
             "--exclude", "first.so",
             jmod.toString())
             .assertSuccess()
             .resultChecker(r -> {
                 Set<String> expectedFilenames = new HashSet<>();
                 expectedFilenames.add(CLASSES_PREFIX + "module-info.class");
                 expectedFilenames.add(CLASSES_PREFIX + "jdk/test/foo/Foo.class");
                 expectedFilenames.add(LIBS_PREFIX + "second.so");
                 expectedFilenames.add(LIBS_PREFIX + "third/third.so");
                 assertJmodContent(jmod, expectedFilenames);

                 Set<String> unexpectedFilenames = new HashSet<>();
                 unexpectedFilenames.add(CLASSES_PREFIX + "jdk/test/foo/internal/Message.class");
                 unexpectedFilenames.add(LIBS_PREFIX + "first.so");
                 assertJmodDoesNotContain(jmod, unexpectedFilenames);
             });
    }

    @Test
    public void describe() throws IOException {
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes").toString();
        jmod("create",
             "--class-path", cp,
              MODS_DIR.resolve("describeFoo.jmod").toString())
             .assertSuccess();

        jmod("describe",
             MODS_DIR.resolve("describeFoo.jmod").toString())
             .assertSuccess()
             .resultChecker(r -> {
                 // Expect similar output: "foo,  requires mandated java.base
                 // exports jdk.test.foo,  conceals jdk.test.foo.internal"
                 Pattern p = Pattern.compile("\\s+foo\\s+requires\\s+mandated\\s+java.base");
                 assertTrue(p.matcher(r.output).find(),
                           "Expecting to find \"foo, requires java.base\"" +
                                "in output, but did not: [" + r.output + "]");
                 p = Pattern.compile(
                        "exports\\s+jdk.test.foo\\s+conceals\\s+jdk.test.foo.internal");
                 assertTrue(p.matcher(r.output).find(),
                           "Expecting to find \"exports ..., conceals ...\"" +
                                "in output, but did not: [" + r.output + "]");
             });
    }

    @Test
    public void testVersion() {
        jmod("--version")
            .assertSuccess()
            .resultChecker(r -> {
                assertContains(r.output, System.getProperty("java.version"));
            });
    }

    @Test
    public void testHelp() {
        jmod("--help")
            .assertSuccess()
            .resultChecker(r ->
                assertTrue(r.output.startsWith("Usage: jmod"), "Help not printed")
            );
    }

    @Test
    public void testTmpFileAlreadyExists() throws IOException {
        // Implementation detail: jmod tool creates <jmod-file>.tmp
        // Ensure that there are no problems if existing

        Path jmod = MODS_DIR.resolve("testTmpFileAlreadyExists.jmod");
        Path tmp = MODS_DIR.resolve("testTmpFileAlreadyExists.jmod.tmp");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        FileUtils.deleteFileIfExistsWithRetry(tmp);
        Files.createFile(tmp);
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes").toString();

        jmod("create",
             "--class-path", cp,
             jmod.toString())
            .assertSuccess()
            .resultChecker(r ->
                assertTrue(Files.notExists(tmp), "Unexpected tmp file:" + tmp)
            );
    }

    @Test
    public void testTmpFileRemoved() throws IOException {
        // Implementation detail: jmod tool creates <jmod-file>.tmp
        // Ensure that it is removed in the event of a failure.
        // The failure in this case is a class in the unnamed package.

        Path jmod = MODS_DIR.resolve("testTmpFileRemoved.jmod");
        Path tmp = MODS_DIR.resolve("testTmpFileRemoved.jmod.tmp");
        FileUtils.deleteFileIfExistsWithRetry(jmod);
        FileUtils.deleteFileIfExistsWithRetry(tmp);
        String cp = EXPLODED_DIR.resolve("foo").resolve("classes") + File.pathSeparator +
                    EXPLODED_DIR.resolve("foo").resolve("classes")
                                .resolve("jdk").resolve("test").resolve("foo").toString();

        jmod("create",
             "--class-path", cp,
             jmod.toString())
             .assertFailure()
             .resultChecker(r -> {
                 assertContains(r.output, "unnamed package");
                 assertTrue(Files.notExists(tmp), "Unexpected tmp file:" + tmp);
             });
    }

    // ---

    static boolean compileModule(String name, Path dest) throws IOException {
        return CompilerUtils.compile(SRC_DIR.resolve(name), dest);
    }

    static void assertContains(String output, String subString) {
        if (output.contains(subString))
            assertTrue(true);
        else
            assertTrue(false,"Expected to find [" + subString + "], in output ["
                           + output + "]" + "\n");
    }

    static ModuleDescriptor getModuleDescriptor(Path jmod) {
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        try (FileSystem fs = FileSystems.newFileSystem(jmod, cl)) {
            String p = "/classes/module-info.class";
            try (InputStream is = Files.newInputStream(fs.getPath(p))) {
                return ModuleDescriptor.read(is);
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    static Stream<String> findFiles(Path dir) {
        try {
            return Files.find(dir, Integer.MAX_VALUE, (p, a) -> a.isRegularFile())
                        .map(dir::relativize)
                        .map(Path::toString)
                        .map(p -> p.replace(File.separator, "/"));
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    static Set<String> getJmodContent(Path jmod) {
        JmodResult r = jmod("list", jmod.toString()).assertSuccess();
        return Stream.of(r.output.split("\r?\n")).collect(toSet());
    }

    static void assertJmodContent(Path jmod, Set<String> expected) {
        Set<String> actual = getJmodContent(jmod);
        if (!Objects.equals(actual, expected)) {
            Set<String> unexpected = new HashSet<>(actual);
            unexpected.removeAll(expected);
            Set<String> notFound = new HashSet<>(expected);
            notFound.removeAll(actual);
            StringBuilder sb = new StringBuilder();
            sb.append("Unexpected but found:\n");
            unexpected.forEach(s -> sb.append("\t" + s + "\n"));
            sb.append("Expected but not found:\n");
            notFound.forEach(s -> sb.append("\t" + s + "\n"));
            assertTrue(false, "Jmod content check failed.\n" + sb.toString());
        }
    }

    static void assertJmodDoesNotContain(Path jmod, Set<String> unexpectedNames) {
        Set<String> actual = getJmodContent(jmod);
        Set<String> unexpected = new HashSet<>();
        for (String name : unexpectedNames) {
            if (actual.contains(name))
                unexpected.add(name);
        }
        if (!unexpected.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (String s : unexpected)
                sb.append("Unexpected but found: " + s + "\n");
            sb.append("In :");
            for (String s : actual)
                sb.append("\t" + s + "\n");
            assertTrue(false, "Jmod content check failed.\n" + sb.toString());
        }
    }

    static JmodResult jmod(String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        System.out.println("jmod " + Arrays.asList(args));
        int ec = jdk.tools.jmod.Main.run(args, ps);
        return new JmodResult(ec, new String(baos.toByteArray(), UTF_8));
    }

    static class JmodResult {
        final int exitCode;
        final String output;

        JmodResult(int exitValue, String output) {
            this.exitCode = exitValue;
            this.output = output;
        }
        JmodResult assertSuccess() { assertTrue(exitCode == 0, output); return this; }
        JmodResult assertFailure() { assertTrue(exitCode != 0, output); return this; }
        JmodResult resultChecker(Consumer<JmodResult> r) { r.accept(this); return this; }
    }

    static void createCmds(Path dir) throws IOException {
        List<String> files = Arrays.asList(
                "first", "second", "third" + File.separator + "third");
        createFiles(dir, files);
    }

    static void createLibs(Path dir) throws IOException {
        List<String> files = Arrays.asList(
                "first.so", "second.so", "third" + File.separator + "third.so");
        createFiles(dir, files);
    }

    static void createConfigs(Path dir) throws IOException {
        List<String> files = Arrays.asList(
                "first.cfg", "second.cfg", "third" + File.separator + "third.cfg");
        createFiles(dir, files);
    }

    static void createFiles(Path dir, List<String> filenames) throws IOException {
        for (String name : filenames) {
            Path file = dir.resolve(name);
            Files.createDirectories(file.getParent());
            Files.createFile(file);
            try (OutputStream os  = Files.newOutputStream(file)) {
                os.write("blahblahblah".getBytes(UTF_8));
            }
        }
    }

    // Standalone entry point.
    public static void main(String[] args) throws Throwable {
        JmodTest test = new JmodTest();
        test.buildExplodedModules();
        for (Method m : JmodTest.class.getDeclaredMethods()) {
            if (m.getAnnotation(Test.class) != null) {
                System.out.println("Invoking " + m.getName());
                m.invoke(test);
            }
        }
    }
}
