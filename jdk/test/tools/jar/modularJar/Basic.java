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

import java.io.*;
import java.lang.reflect.Method;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import jdk.testlibrary.FileUtils;
import jdk.testlibrary.JDKToolFinder;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static java.lang.String.format;
import static java.lang.System.out;

/*
 * @test
 * @library /lib/testlibrary
 * @build jdk.testlibrary.FileUtils jdk.testlibrary.JDKToolFinder
 * @compile Basic.java
 * @run testng Basic
 * @summary Basic test for Modular jars
 */

public class Basic {
    static final Path TEST_SRC = Paths.get(System.getProperty("test.src", "."));
    static final Path TEST_CLASSES = Paths.get(System.getProperty("test.classes", "."));
    static final Path MODULE_CLASSES = TEST_CLASSES.resolve("build");

    // Details based on the checked in module source
    static TestModuleData FOO = new TestModuleData("foo",
                                                   "1.123",
                                                   "jdk.test.foo.Foo",
                                                   "Hello World!!!", null,
                                                   "jdk.test.foo.internal");
    static TestModuleData BAR = new TestModuleData("bar",
                                                   "4.5.6.7",
                                                   "jdk.test.bar.Bar",
                                                   "Hello from Bar!", null,
                                                   "jdk.test.bar",
                                                   "jdk.test.bar.internal");

    static class TestModuleData {
        final String moduleName;
        final String mainClass;
        final String version;
        final String message;
        final String hashes;
        final Set<String> conceals;
        TestModuleData(String mn, String v, String mc, String m, String h, String... pkgs) {
            moduleName = mn; mainClass = mc; version = v; message = m; hashes = h;
            conceals = new HashSet<>();
            Stream.of(pkgs).forEach(conceals::add);
        }
        TestModuleData(String mn, String v, String mc, String m, String h, Set<String> pkgs) {
            moduleName = mn; mainClass = mc; version = v; message = m; hashes = h;
            conceals = pkgs;
        }
        static TestModuleData from(String s) {
            try {
                BufferedReader reader = new BufferedReader(new StringReader(s));
                String line;
                String message = null;
                String name = null, version = null, mainClass = null;
                String hashes = null;
                Set<String> conceals = null;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("message:")) {
                        message = line.substring("message:".length());
                    } else if (line.startsWith("nameAndVersion:")) {
                        line = line.substring("nameAndVersion:".length());
                        int i = line.indexOf('@');
                        if (i != -1) {
                            name = line.substring(0, i);
                            version = line.substring(i + 1, line.length());
                        } else {
                            name = line;
                        }
                    } else if (line.startsWith("mainClass:")) {
                        mainClass = line.substring("mainClass:".length());
                    } else if (line.startsWith("hashes:")) {
                        hashes = line.substring("hashes:".length());
                    }  else if (line.startsWith("conceals:")) {
                        line = line.substring("conceals:".length());
                        conceals = new HashSet<>();
                        int i = line.indexOf(',');
                        if (i != -1) {
                            String[] p = line.split(",");
                            Stream.of(p).forEach(conceals::add);
                        } else {
                            conceals.add(line);
                        }
                    } else {
                        throw new AssertionError("Unknown value " + line);
                    }
                }

                return new TestModuleData(name, version, mainClass, message, hashes, conceals);
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        }
    }

    static void assertModuleData(Result r, TestModuleData expected) {
        //out.printf("%s%n", r.output);
        TestModuleData received = TestModuleData.from(r.output);
        if (expected.message != null)
            assertTrue(expected.message.equals(received.message),
                       "Expected message:", expected.message, ", got:", received.message);
        assertTrue(expected.moduleName.equals(received.moduleName),
                   "Expected moduleName: ", expected.moduleName, ", got:", received.moduleName);
        assertTrue(expected.version.equals(received.version),
                   "Expected version: ", expected.version, ", got:", received.version);
        assertTrue(expected.mainClass.equals(received.mainClass),
                   "Expected mainClass: ", expected.mainClass, ", got:", received.mainClass);
        expected.conceals.forEach(p -> assertTrue(received.conceals.contains(p),
                                                  "Expected ", p, ", in ", received.conceals));
        received.conceals.forEach(p -> assertTrue(expected.conceals.contains(p),
                                                  "Expected ", p, ", in ", expected.conceals));
    }

    @BeforeTest
    public void compileModules() throws Exception {
        compileModule(FOO.moduleName);
        compileModule(BAR.moduleName, MODULE_CLASSES);
        compileModule("baz");  // for service provider consistency checking
    }

    @Test
    public void createFoo() throws IOException {
        Path mp = Paths.get("createFoo");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));

        try (InputStream fis = Files.newInputStream(modularJar);
             JarInputStream jis = new JarInputStream(fis)) {
            assertTrue(!jarContains(jis, "./"),
                       "Unexpected ./ found in ", modularJar.toString());
        }
    }

    @Test
    public void updateFoo() throws IOException {
        Path mp = Paths.get("updateFoo");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        jar("--create",
            "--file=" + modularJar.toString(),
            "--no-manifest",
            "-C", modClasses.toString(), "jdk")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), "module-info.class")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));
    }

    @Test
    public void partialUpdateFooMainClass() throws IOException {
        Path mp = Paths.get("partialUpdateFooMainClass");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        // A "bad" main class in first create ( and no version )
        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + "IAmNotTheEntryPoint",
            "--no-manifest",
            "-C", modClasses.toString(), ".")  // includes module-info.class
           .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));
    }

    @Test
    public void partialUpdateFooVersion() throws IOException {
        Path mp = Paths.get("partialUpdateFooVersion");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        // A "bad" version in first create ( and no main class )
        jar("--create",
            "--file=" + modularJar.toString(),
            "--module-version=" + "100000000",
            "--no-manifest",
            "-C", modClasses.toString(), ".")  // includes module-info.class
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));
    }

    @Test
    public void partialUpdateFooNotAllFiles() throws IOException {
        Path mp = Paths.get("partialUpdateFooNotAllFiles");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        // Not all files, and none from non-exported packages,
        // i.e. no concealed list in first create
        jar("--create",
            "--file=" + modularJar.toString(),
            "--no-manifest",
            "-C", modClasses.toString(), "module-info.class",
            "-C", modClasses.toString(), "jdk/test/foo/Foo.class")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), "jdk/test/foo/internal/Message.class")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));
    }

    @Test
    public void partialUpdateFooAllFilesAndAttributes() throws IOException {
        Path mp = Paths.get("partialUpdateFooAllFilesAndAttributes");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        // all attributes and files
        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();
        java(mp, FOO.moduleName + "/" + FOO.mainClass)
            .assertSuccess()
            .resultChecker(r -> assertModuleData(r, FOO));
    }

    @Test
    public void partialUpdateFooModuleInfo() throws IOException {
        Path mp = Paths.get("partialUpdateFooModuleInfo");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");
        Path barModInfo = MODULE_CLASSES.resolve(BAR.moduleName);

        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "--no-manifest",
            "-C", barModInfo.toString(), "module-info.class")  // stuff in bar's info
            .assertSuccess();
        jar("-p",
            "--file=" + modularJar.toString())
            .assertSuccess()
            .resultChecker(r -> {
                // Expect similar output: "Name:bar,  Requires: foo,...
                // Conceals: jdk.test.foo, jdk.test.foo.internal"
                Pattern p = Pattern.compile("\\s+Name:\\s+bar\\s+Requires:\\s+foo");
                assertTrue(p.matcher(r.output).find(),
                           "Expecting to find \"Name: bar, Requires: foo,...\"",
                           "in output, but did not: [" + r.output + "]");
                p = Pattern.compile(
                        "Conceals:\\s+jdk.test.foo\\s+jdk.test.foo.internal");
                assertTrue(p.matcher(r.output).find(),
                           "Expecting to find \"Conceals: jdk.test.foo,...\"",
                           "in output, but did not: [" + r.output + "]");
            });
    }

    @Test
    public void dependencesFooBar() throws IOException {
        Path mp = Paths.get("dependencesFooBar");
        createTestDir(mp);

        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");
        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();

        modClasses = MODULE_CLASSES.resolve(BAR.moduleName);
        modularJar = mp.resolve(BAR.moduleName + ".jar");
        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + BAR.mainClass,
            "--module-version=" + BAR.version,
            "--modulepath=" + mp.toString(),
            "--hash-dependencies=" + "foo",  // dependency on foo
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();

        java(mp, BAR.moduleName + "/" + BAR.mainClass,
             "-XaddExports:java.base/jdk.internal.module=bar")
            .assertSuccess()
            .resultChecker(r -> {
                assertModuleData(r, BAR);
                TestModuleData received = TestModuleData.from(r.output);
                assertTrue(received.hashes != null, "Expected non-null hashes value.");
            });
    }

    @Test
    public void badDependencyFooBar() throws IOException {
        Path mp = Paths.get("badDependencyFooBar");
        createTestDir(mp);

        Path fooClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path fooJar = mp.resolve(FOO.moduleName + ".jar");
        jar("--create",
            "--file=" + fooJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", fooClasses.toString(), ".").assertSuccess();

        Path barClasses = MODULE_CLASSES.resolve(BAR.moduleName);
        Path barJar = mp.resolve(BAR.moduleName + ".jar");
        jar("--create",
            "--file=" + barJar.toString(),
            "--main-class=" + BAR.mainClass,
            "--module-version=" + BAR.version,
            "--modulepath=" + mp.toString(),
            "--hash-dependencies=" + "foo",  // dependency on foo
            "--no-manifest",
            "-C", barClasses.toString(), ".").assertSuccess();

        // Rebuild foo.jar with a change that will cause its hash to be different
        FileUtils.deleteFileWithRetry(fooJar);
        jar("--create",
            "--file=" + fooJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version + ".1", // a newer version
            "--no-manifest",
            "-C", fooClasses.toString(), ".").assertSuccess();

        java(mp, BAR.moduleName + "/" + BAR.mainClass,
             "-XaddExports:java.base/jdk.internal.module=bar")
            .assertFailure()
            .resultChecker(r -> {
                // Expect similar output: "java.lang.module.ResolutionException: Hash
                // of foo (WdktSIQSkd4+CEacpOZoeDrCosMATNrIuNub9b5yBeo=) differs to
                // expected hash (iepvdv8xTeVrFgMtUhcFnmetSub6qQHCHc92lSaSEg0=)"
                Pattern p = Pattern.compile(".*Hash of foo.*differs to expected hash.*");
                assertTrue(p.matcher(r.output).find(),
                      "Expecting error message containing \"Hash of foo ... differs to"
                              + " expected hash...\" but got: [", r.output + "]");
            });
    }

    @Test
    public void badOptionsFoo() throws IOException {
        Path mp = Paths.get("badOptionsFoo");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        jar("--create",
            "--file=" + modularJar.toString(),
            "--module-version=" + 1.1,   // no module-info.class
            "-C", modClasses.toString(), "jdk")
            .assertFailure();      // TODO: expected failure message

         jar("--create",
             "--file=" + modularJar.toString(),
             "--hash-dependencies=" + ".*",   // no module-info.class
             "-C", modClasses.toString(), "jdk")
             .assertFailure();      // TODO: expected failure message
    }

    @Test
    public void servicesCreateWithoutFailure() throws IOException {
        Path mp = Paths.get("servicesCreateWithoutFailure");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve("baz");
        Path modularJar = mp.resolve("baz" + ".jar");

        // Positive test, create
        jar("--create",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "module-info.class",
            "-C", modClasses.toString(), "jdk/test/baz/BazService.class",
            "-C", modClasses.toString(), "jdk/test/baz/internal/BazServiceImpl.class")
            .assertSuccess();
    }

    @Test
    public void servicesCreateWithoutServiceImpl() throws IOException {
        Path mp = Paths.get("servicesWithoutServiceImpl");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve("baz");
        Path modularJar = mp.resolve("baz" + ".jar");

        // Omit service impl
        jar("--create",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "module-info.class",
            "-C", modClasses.toString(), "jdk/test/baz/BazService.class")
            .assertFailure();
    }

    @Test
    public void servicesUpdateWithoutFailure() throws IOException {
        Path mp = Paths.get("servicesUpdateWithoutFailure");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve("baz");
        Path modularJar = mp.resolve("baz" + ".jar");

        // Positive test, update
        jar("--create",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "jdk/test/baz/BazService.class",
            "-C", modClasses.toString(), "jdk/test/baz/internal/BazServiceImpl.class")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "module-info.class")
            .assertSuccess();
    }

    @Test
    public void servicesUpdateWithoutServiceImpl() throws IOException {
        Path mp = Paths.get("servicesUpdateWithoutServiceImpl");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve("baz");
        Path modularJar = mp.resolve("baz" + ".jar");

        // Omit service impl
        jar("--create",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "jdk/test/baz/BazService.class")
            .assertSuccess();
        jar("--update",
            "--file=" + modularJar.toString(),
            "-C", modClasses.toString(), "module-info.class")
            .assertFailure();
    }

    @Test
    public void printModuleDescriptorFoo() throws IOException {
        Path mp = Paths.get("printModuleDescriptorFoo");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();

        for (String option : new String[]  {"--print-module-descriptor", "-p" }) {
            jar(option,
                "--file=" + modularJar.toString())
                .assertSuccess()
                .resultChecker(r ->
                    assertTrue(r.output.contains(FOO.moduleName + "@" + FOO.version),
                               "Expected to find ", FOO.moduleName + "@" + FOO.version,
                               " in [", r.output, "]")
                );
        }
    }

    @Test
    public void printModuleDescriptorFooFromStdin() throws IOException {
        Path mp = Paths.get("printModuleDescriptorFooFromStdin");
        createTestDir(mp);
        Path modClasses = MODULE_CLASSES.resolve(FOO.moduleName);
        Path modularJar = mp.resolve(FOO.moduleName + ".jar");

        jar("--create",
            "--file=" + modularJar.toString(),
            "--main-class=" + FOO.mainClass,
            "--module-version=" + FOO.version,
            "--no-manifest",
            "-C", modClasses.toString(), ".")
            .assertSuccess();

        for (String option : new String[]  {"--print-module-descriptor", "-p" }) {
            jarWithStdin(modularJar.toFile(),
                         option)
                         .assertSuccess()
                         .resultChecker(r ->
                             assertTrue(r.output.contains(FOO.moduleName + "@" + FOO.version),
                                "Expected to find ", FOO.moduleName + "@" + FOO.version,
                                " in [", r.output, "]")
                );
        }
    }

    // -- Infrastructure

    static Result jarWithStdin(File stdinSource, String... args) {
        String jar = getJDKTool("jar");
        List<String> commands = new ArrayList<>();
        commands.add(jar);
        Stream.of(args).forEach(x -> commands.add(x));
        ProcessBuilder p = new ProcessBuilder(commands);
        if (stdinSource != null)
            p.redirectInput(stdinSource);
        return run(p);
    }

    static Result jar(String... args) {
        return jarWithStdin(null, args);
    }

    static Path compileModule(String mn) throws IOException {
        return compileModule(mn, null);
    }

    static Path compileModule(String mn, Path mp)
        throws IOException
    {
        Path fooSourcePath = TEST_SRC.resolve("src").resolve(mn);
        Path build = Files.createDirectories(MODULE_CLASSES.resolve(mn));
        javac(build, mp, fileList(fooSourcePath));
        return build;
    }

    // Re-enable when there is support in javax.tools for module path
//    static void javac(Path dest, Path... sourceFiles) throws IOException {
//        out.printf("Compiling %d source files %s%n", sourceFiles.length,
//                   Arrays.asList(sourceFiles));
//        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
//        try (StandardJavaFileManager fileManager =
//                     compiler.getStandardFileManager(null, null, null)) {
//
//            List<File> files = Stream.of(sourceFiles)
//                                     .map(p -> p.toFile())
//                                     .collect(Collectors.toList());
//            List<File> dests = Stream.of(dest)
//                                     .map(p -> p.toFile())
//                                     .collect(Collectors.toList());
//            Iterable<? extends JavaFileObject> compilationUnits =
//                    fileManager.getJavaFileObjectsFromFiles(files);
//            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, dests);
//            JavaCompiler.CompilationTask task =
//                    compiler.getTask(null, fileManager, null, null, null, compilationUnits);
//            boolean passed = task.call();
//            if (!passed)
//                throw new RuntimeException("Error compiling " + files);
//        }
//    }

    static void javac(Path dest, Path... sourceFiles) throws IOException {
        javac(dest, null, sourceFiles);
    }

    static void javac(Path dest, Path modulePath, Path... sourceFiles)
        throws IOException
    {
        String javac = getJDKTool("javac");

        List<String> commands = new ArrayList<>();
        commands.add(javac);
        commands.add("-d");
        commands.add(dest.toString());
        if (dest.toString().contains("bar"))
            commands.add("-XaddExports:java.base/jdk.internal.module=bar");
        if (modulePath != null) {
            commands.add("-mp");
            commands.add(modulePath.toString());
        }
        Stream.of(sourceFiles).map(Object::toString).forEach(x -> commands.add(x));

        quickFail(run(new ProcessBuilder(commands)));
    }

    static Result java(Path modulePath, String entryPoint, String... args) {
        String java = getJDKTool("java");

        List<String> commands = new ArrayList<>();
        commands.add(java);
        Stream.of(args).forEach(x -> commands.add(x));
        commands.add("-mp");
        commands.add(modulePath.toString());
        commands.add("-m");
        commands.add(entryPoint);

        return run(new ProcessBuilder(commands));
    }

    static Path[] fileList(Path directory) throws IOException {
        final List<Path> filePaths = new ArrayList<>();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                                             BasicFileAttributes attrs) {
                filePaths.add(file);
                return FileVisitResult.CONTINUE;
            }
        });
        return filePaths.toArray(new Path[filePaths.size()]);
    }

    static void createTestDir(Path p) throws IOException{
        if (Files.exists(p))
            FileUtils.deleteFileTreeWithRetry(p);
        Files.createDirectory(p);
    }

    static boolean jarContains(JarInputStream jis, String entryName)
        throws IOException
    {
        JarEntry e;
        while((e = jis.getNextJarEntry()) != null) {
            if (e.getName().equals(entryName))
                return true;
        }
        return false;
    }

    static void quickFail(Result r) {
        if (r.ec != 0)
            throw new RuntimeException(r.output);
    }

    static Result run(ProcessBuilder pb) {
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

    static final String DEFAULT_IMAGE_BIN = System.getProperty("java.home")
            + File.separator + "bin" + File.separator;

    static String getJDKTool(String name) {
        try {
            return JDKToolFinder.getJDKTool(name);
        } catch (Exception x) {
            return DEFAULT_IMAGE_BIN + name;
        }
    }

    static String toString(InputStream in1, InputStream in2) throws IOException {
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
        Result assertSuccess() {
            assertTrue(ec == 0, "Expected ec 0, got: ", ec, " , output [", output, "]");
            return this;
        }
        Result assertFailure() {
            assertTrue(ec != 0, "Expected ec != 0, got:", ec, " , output [", output, "]");
            return this;
        }
        Result resultChecker(Consumer<Result> r) { r.accept(this); return this; }
    }

    static void assertTrue(boolean cond, Object ... failedArgs) {
        if (cond)
            return;
        StringBuilder sb = new StringBuilder();
        for (Object o : failedArgs)
            sb.append(o);
        org.testng.Assert.assertTrue(false, sb.toString());
    }

    // Standalone entry point.
    public static void main(String[] args) throws Throwable {
        Basic test = new Basic();
        test.compileModules();
        for (Method m : Basic.class.getDeclaredMethods()) {
            if (m.getAnnotation(Test.class) != null) {
                System.out.println("Invoking " + m.getName());
                m.invoke(test);
            }
        }
    }
}
