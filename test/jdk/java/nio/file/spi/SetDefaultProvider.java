/*
 * Copyright (c) 2008, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 4313887 7006126 8142968 8178380 8183320 8210112 8266345 8263940 8331467
 * @modules jdk.jartool
 * @library /test/lib
 * @build SetDefaultProvider TestProvider m/* jdk.test.lib.process.ProcessTools jdk.test.lib.process.OutputAnalyzer foo/*
 * @run testng/othervm SetDefaultProvider
 * @summary Runs tests with -Djava.nio.file.spi.DefaultFileSystemProvider set on
 *          the command line to override the default file system provider
 */

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class SetDefaultProvider {

    private static final String SET_DEFAULT_FSP =
        "-Djava.nio.file.spi.DefaultFileSystemProvider=TestProvider";

    private static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
            .orElseThrow(() -> new RuntimeException("jmod tool not found"));
    private static final ToolProvider JLINK_TOOL = ToolProvider.findFirst("jlink")
            .orElseThrow(() -> new RuntimeException("jlink tool not found"));

    private static Path createTempDirectory(String prefix) throws IOException {
        Path testDir = Paths.get(System.getProperty("test.dir", "."));
        return Files.createTempDirectory(testDir, prefix);
    }

    /**
     * Test override of default FileSystemProvider with the main application
     * on the class path.
     */
    public void testClassPath() throws Exception {
        String moduleClasses = moduleClasses();
        String testClasses = System.getProperty("test.classes");
        String classpath = moduleClasses + File.pathSeparator + testClasses;
        int exitValue = exec(SET_DEFAULT_FSP, "-cp", classpath, "p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Test override of default FileSystemProvider with a
     * FileSystemProvider jar and the main application on the class path.
     */
    public void testClassPathWithFileSystemProviderJar() throws Exception {
        String testClasses = System.getProperty("test.classes");
        Path jar = Path.of("testFileSystemProvider.jar");
        Files.deleteIfExists(jar);
        createFileSystemProviderJar(jar, Path.of(testClasses));
        String classpath = jar + File.pathSeparator + testClasses
                + File.separator + "modules" + File.separator + "m";
        int exitValue = exec(SET_DEFAULT_FSP, "-cp", classpath, "p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Creates a JAR containing the FileSystemProvider used to override the
     * default FileSystemProvider
     */
    private void createFileSystemProviderJar(Path jar, Path dir) throws IOException {

        List<String>  args = new ArrayList<>();
        args.add("--create");
        args.add("--file=" + jar);
        try (Stream<Path> stream = Files.list(dir)) {
            List<String> paths = stream
                    .map(path -> path.getFileName().toString())
                    .filter(f -> f.startsWith("TestProvider"))
                    .toList();
            for(var p : paths) {
                args.add("-C");
                args.add(dir.toString());
                args.add(p);
            }
        }
        int ret = JAR_TOOL.run(System.out, System.out, args.toArray(new String[0]));
        assertEquals(ret, 0);
    }

    /**
     * Test override of default FileSystemProvider with the main application
     * on the module path as an exploded module.
     */
    public void testExplodedModule() throws Exception {
        String modulePath = System.getProperty("jdk.module.path");
        int exitValue = exec(SET_DEFAULT_FSP, "-p", modulePath, "-m", "m/p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Test override of default FileSystemProvider with the main application
     * on the module path as a modular JAR.
     */
    public void testModularJar() throws Exception {
        String jarFile = createModularJar();
        int exitValue = exec(SET_DEFAULT_FSP, "-p", jarFile, "-m", "m/p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Test override of default FileSystemProvider where the main application
     * is a module that is patched by an exploded patch.
     */
    public void testExplodedModuleWithExplodedPatch() throws Exception {
        Path patchdir = createTempDirectory("patch");
        String modulePath = System.getProperty("jdk.module.path");
        int exitValue = exec(SET_DEFAULT_FSP,
                             "--patch-module", "m=" + patchdir,
                             "-p", modulePath,
                             "-m", "m/p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Test override of default FileSystemProvider where the main application
     * is a module that is patched by an exploded patch.
     */
    public void testExplodedModuleWithJarPatch() throws Exception {
        Path patchdir = createTempDirectory("patch");
        Files.createDirectory(patchdir.resolve("m.properties"));
        Path patch = createJarFile(patchdir);
        String modulePath = System.getProperty("jdk.module.path");
        int exitValue = exec(SET_DEFAULT_FSP,
                             "--patch-module", "m=" + patch,
                             "-p", modulePath,
                             "-m", "m/p.Main");
        assertEquals(exitValue, 0);
    }

    /**
     * Returns the directory containing the classes for module "m".
     */
    private String moduleClasses() {
        String mp = System.getProperty("jdk.module.path");
        for (String dir : mp.split(File.pathSeparator)) {
            Path m = Paths.get(dir, "m");
            if (Files.exists(m)) return m.toString();
        }
        fail();
        return null;
    }

    /**
     * Creates a modular JAR containing module "m".
     */
    private String createModularJar() throws Exception {
        Path dir = Paths.get(moduleClasses());
        Path jar = createJarFile(dir);
        return jar.toString();
    }

    /**
     * Creates a JAR file containing the entries in the given file tree.
     */
    private Path createJarFile(Path dir) throws Exception {
        Path jar = createTempDirectory("tmp").resolve("m.jar");
        String[] args = { "--create", "--file=" + jar, "-C", dir.toString(), "." };
        int ret = JAR_TOOL.run(System.out, System.out, args);
        assertEquals(ret, 0);
        return jar;
    }


    /**
     * Test CustomfsImage with --list-modules and main When the default FileSystemProvider was overridden within a custom image
     */
    public void testCustomfsImage() throws Exception {
        Path customfsImage = createCustomfsImage();
        Path javaBin = customfsImage.resolve("bin","java");
        if( !Files.exists(javaBin)) {
            javaBin = customfsImage.resolve("bin","java.exe");
        }
        String[] withListModules = {javaBin.toString(),"--list-modules"};
        String[] withMain = {javaBin.toString(),"-m","foo/customfs.Main"};

        System.out.println("launch with --list-modules");
        OutputAnalyzer oa = ProcessTools.executeCommand(withListModules);
        oa.shouldHaveExitValue(0);

        System.out.println("launch with main");
        oa = ProcessTools.executeCommand(withMain);
        oa.shouldHaveExitValue(0);
    }

    /**
     * creates an image which contains the custom implementation of a FileSystemProvider
     */
    private Path createCustomfsImage() throws Exception {
        String customFSProviderModule = createCustomFSProviderModule().toString();
        Path customfsImageDir = Path.of("8331467-image");
        String[] cmd = {"--module-path",customFSProviderModule,
                "--add-modules","foo",
                "--add-options","-Djava.nio.file.spi.DefaultFileSystemProvider=customfs.CustomFileSystemProvider",
                "--output",customfsImageDir.toString()};
        System.out.println("create image with" + Arrays.toString(cmd));
        int exitCode = JLINK_TOOL.run(System.out, System.err, cmd);
        if ( exitCode != 0 ) {
            throw new AssertionError("Unexpected exit code: " + exitCode + " from jlink command");
        }
        return customfsImageDir;
    }

    /**
     * creates a module which contains the custom implementation of a FileSystemProvider
     */
    private Path createCustomFSProviderModule() throws Exception {
        Path compileDestDir = customfsModuleClasses();
        Path fsProviderJmod = createTempDirectory("8331467-custom-fs").resolve("foo.jmod");
        String[] cmd = {"create", "--class-path", compileDestDir.toString(),
                "--main-class", "customfs.Main",
                fsProviderJmod.toString()};
        System.out.println("creating module for custom FileSystemProvider: "
                + Arrays.toString(cmd));
        int exitCode = JMOD_TOOL.run(System.out, System.err, cmd);
        if ( exitCode != 0 ) {
            throw new AssertionError("Unexpected exit code: " + exitCode + " from jmod command");
        }
        return fsProviderJmod;
    }

    private Path customfsModuleClasses() throws Exception {
        String mp = System.getProperty("jdk.module.path");
        for (String dir : mp.split(File.pathSeparator)) {
            Path foo = Paths.get(dir, "foo");
            if (Files.exists(foo)) return foo;
        }
        throw new RuntimeException("foo dir not found");
    }

    /**
     * Invokes the java launcher with the given arguments, returning the exit code.
     */
    private int exec(String... args) throws Exception {
       return ProcessTools.executeTestJava(args)
                .outputTo(System.out)
                .errorTo(System.out)
                .getExitValue();
    }
}
