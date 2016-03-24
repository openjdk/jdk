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
 * @summary Tests jdeps -genmoduleinfo option
 * @library ..
 * @build CompilerUtils
 * @modules jdk.jdeps/com.sun.tools.jdeps
 * @run testng GenModuleInfo
 */

import java.io.*;
import java.lang.module.ModuleDescriptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;


import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class GenModuleInfo {
    private static final String MODULE_INFO = "module-info.class";
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final String TEST_CLASSES = System.getProperty("test.classes");

    private static final Path SRC_DIR = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR = Paths.get("mods");
    private static final Path LIBS_DIR = Paths.get("libs");
    private static final Path DEST_DIR = Paths.get("moduleinfosrc");
    private static final Path NEW_MODS_DIR = Paths.get("new_mods");

    // the names of the modules in this test
    private static final String UNSUPPORTED = "unsupported";
    private static String[] modules = new String[] {"m1", "m2", "m3", UNSUPPORTED};
    /**
     * Compiles all modules used by the test
     */
    @BeforeTest
    public void compileAll() throws Exception {
        CompilerUtils.cleanDir(MODS_DIR);
        CompilerUtils.cleanDir(LIBS_DIR);
        CompilerUtils.cleanDir(DEST_DIR);
        CompilerUtils.cleanDir(NEW_MODS_DIR);

        assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, UNSUPPORTED,
                                               "-XaddExports:java.base/jdk.internal.perf=" + UNSUPPORTED));
        Arrays.asList("m1", "m2", "m3")
              .forEach(mn -> assertTrue(CompilerUtils.compileModule(SRC_DIR, MODS_DIR, mn)));

        Files.createDirectory(LIBS_DIR);
        Files.createDirectory(DEST_DIR);

        for (String mn : modules) {
            Path root = MODS_DIR.resolve(mn);
            createJar(LIBS_DIR.resolve(mn + ".jar"), root,
                      Files.walk(root, Integer.MAX_VALUE)
                           .filter(f -> {
                                String fn = f.getFileName().toString();
                                return fn.endsWith(".class") && !fn.equals("module-info.class");
                           }));
        }
    }

    @Test
    public void jdeps() throws IOException {
        Stream<String> files = Arrays.stream(modules)
                .map(mn -> LIBS_DIR.resolve(mn + ".jar"))
                .map(Path::toString);
        jdeps(Stream.concat(Stream.of("-cp"), files).toArray(String[]::new));
    }

    @Test
    public void test() throws IOException {
        Stream<String> files = Arrays.stream(modules)
                .map(mn -> LIBS_DIR.resolve(mn + ".jar"))
                .map(Path::toString);

        jdeps(Stream.concat(Stream.of("-genmoduleinfo", DEST_DIR.toString()),
                            files)
                    .toArray(String[]::new));

        // check file exists
        Arrays.stream(modules)
                .map(mn -> DEST_DIR.resolve(mn).resolve("module-info.java"))
                .forEach(f -> assertTrue(Files.exists(f)));

        // copy classes except the original module-info.class
        Files.walk(MODS_DIR, Integer.MAX_VALUE)
                .filter(path -> !path.getFileName().toString().equals(MODULE_INFO) &&
                                path.getFileName().toString().endsWith(".class"))
                .map(path -> MODS_DIR.relativize(path))
                .forEach(path -> {
                    try {
                        Path newFile = NEW_MODS_DIR.resolve(path);
                        Files.createDirectories(newFile.getParent());
                        Files.copy(MODS_DIR.resolve(path), newFile);
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

        // compile new module-info.java
        assertTrue(CompilerUtils.compileModule(DEST_DIR, NEW_MODS_DIR, UNSUPPORTED,
                        "-mp", NEW_MODS_DIR.toString(), "-verbose",
                        "-XaddExports:java.base/jdk.internal.perf=" + UNSUPPORTED));
        Arrays.asList("m1", "m2", "m3")
              .forEach(mn -> assertTrue(CompilerUtils.compileModule(DEST_DIR, NEW_MODS_DIR,
                                        mn, "-mp", NEW_MODS_DIR.toString())));

        for (String mn : modules) {
            Path p1 = NEW_MODS_DIR.resolve(mn).resolve(MODULE_INFO);
            Path p2 = MODS_DIR.resolve(mn).resolve(MODULE_INFO);

            try (InputStream in1 = Files.newInputStream(p1);
                 InputStream in2 = Files.newInputStream(p2)) {
                verify(ModuleDescriptor.read(in1),
                        ModuleDescriptor.read(in2, () -> packages(MODS_DIR.resolve(mn))));
            }
        }
    }

    private void verify(ModuleDescriptor md1, ModuleDescriptor md2) {
        System.out.println("verifying: " + md1.name());
        assertEquals(md1.name(), md2.name());
        assertEquals(md1.requires(), md2.requires());
        // all packages are exported
        assertEquals(md1.exports().stream()
                                  .map(ModuleDescriptor.Exports::source)
                                  .collect(Collectors.toSet()), md2.packages());
    }

    private Set<String> packages(Path dir) {
        try {
            return Files.find(dir, Integer.MAX_VALUE,
                             ((path, attrs) -> attrs.isRegularFile() &&
                                               path.toString().endsWith(".class")))
                        .map(path -> toPackageName(dir.relativize(path)))
                        .filter(pkg -> pkg.length() > 0)   // module-info
                        .distinct()
                        .collect(Collectors.toSet());
        } catch (IOException x) {
            throw new UncheckedIOException(x);
        }
    }

    private String toPackageName(Path path) {
        String name = path.toString();
        assert name.endsWith(".class");
        int index = name.lastIndexOf(File.separatorChar);
        if (index != -1) {
            return name.substring(0, index).replace(File.separatorChar, '.');
        } else {
            return "";
        }
    }

    /*
     * Runs jdeps with the given arguments
     */
    public static String[] jdeps(String... args) {
        String lineSep =     System.getProperty("line.separator");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        System.err.println("jdeps " + Arrays.toString(args));
        int rc = com.sun.tools.jdeps.Main.run(args, pw);
        pw.close();
        String out = sw.toString();
        if (!out.isEmpty())
            System.err.println(out);
        if (rc != 0)
            throw new Error("jdeps failed: rc=" + rc);
        return out.split(lineSep);
    }

    /**
     * Create a jar file using the list of files provided.
     */
    public static void createJar(Path jarfile, Path root, Stream<Path> files)
            throws IOException {
        try (JarOutputStream target = new JarOutputStream(
                Files.newOutputStream(jarfile))) {
           files.forEach(file -> add(root.relativize(file), file, target));
        }
    }

    private static void add(Path path, Path source, JarOutputStream target) {
        try {
            String name = path.toString().replace(File.separatorChar, '/');
            JarEntry entry = new JarEntry(name);
            entry.setTime(source.toFile().lastModified());
            target.putNextEntry(entry);
            Files.copy(source, target);
            target.closeEntry();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
