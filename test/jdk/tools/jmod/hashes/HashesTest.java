/**
 * Copyright (c) 2015, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8160286 8243666
 * @summary Test the recording and checking of module hashes
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.base/jdk.internal.module
 *          jdk.compiler
 *          jdk.jartool
 *          jdk.jlink
 * @build jdk.test.lib.compiler.ModuleInfoMaker
 *        jdk.test.lib.compiler.CompilerUtils
 * @run testng HashesTest
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jdk.internal.module.ModuleInfo;
import jdk.internal.module.ModuleHashes;
import jdk.internal.module.ModulePath;

import jdk.test.lib.compiler.ModuleInfoMaker;

import org.testng.annotations.Test;

import static org.testng.Assert.*;
import static java.lang.module.ModuleDescriptor.Requires.Modifier.*;

public class HashesTest {
    static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
        .orElseThrow(() ->
            new RuntimeException("jmod tool not found")
        );
    static final ToolProvider JAR_TOOL = ToolProvider.findFirst("jar")
        .orElseThrow(() ->
            new RuntimeException("jar tool not found")
        );

    private final Path mods;
    private final Path srcDir;
    private final Path lib;
    private final ModuleInfoMaker builder;

    HashesTest(Path dest) throws IOException {
        if (Files.exists(dest)) {
            deleteDirectory(dest);
        }
        this.mods = dest.resolve("mods");
        this.srcDir = dest.resolve("src");
        this.lib = dest.resolve("lib");
        this.builder = new ModuleInfoMaker(srcDir);

        Files.createDirectories(lib);
        Files.createDirectories(mods);
    }

    @Test
    public static void test() throws IOException {
        Path dest = Paths.get("test");
        HashesTest ht = new HashesTest(dest);

        // create modules for test cases
        ht.makeModule("m2");
        ht.makeModule("m3");
        ht.makeModule("m1", "m2", "m3");

        ht.makeModule("org.bar", TRANSITIVE, "m1");
        ht.makeModule("org.foo", TRANSITIVE, "org.bar");

        // create JMOD for m1, m2, m3
        ht.makeJmod("m2");
        ht.makeJmod("m3");

        // no hash is recorded since m1 has outgoing edges
        ht.jmodHashModules("m1", ".*");

        // no hash is recorded in m1, m2, m3
        assertTrue(ht.hashes("m1") == null);
        assertTrue(ht.hashes("m2") == null);
        assertTrue(ht.hashes("m3") == null);

        // hash m1 in m2
        ht.jmodHashModules("m2",  "m1");
        ht.checkHashes("m2", "m1");

        // hash m1 in m2
        ht.jmodHashModules("m2",  ".*");
        ht.checkHashes("m2", "m1");

        // create m2.jmod with no hash
        ht.makeJmod("m2");
        // run jmod hash command to hash m1 in m2 and m3
        runJmod(List.of("hash", "--module-path", ht.lib.toString(),
                        "--hash-modules", ".*"));
        ht.checkHashes("m2", "m1");
        ht.checkHashes("m3", "m1");

        // check transitive requires
        ht.makeJmod("org.bar");
        ht.makeJmod("org.foo");

        ht.jmodHashModules("org.bar", "org.*");
        ht.checkHashes("org.bar", "org.foo");

        ht.jmodHashModules( "m3", ".*");
        ht.checkHashes("m3", "org.foo", "org.bar", "m1");
    }

    @Test
    public static void multiBaseModules() throws IOException {
        Path dest = Paths.get("test2");
        HashesTest ht = new HashesTest(dest);

        /*
         * y2 -----------> y1
         *    |______
         *    |      |
         *    V      V
         *    z3 -> z2
         *    |      |
         *    |      V
         *    |---> z1
         */

        ht.makeModule("z1");
        ht.makeModule("z2", "z1");
        ht.makeModule("z3", "z1", "z2");

        ht.makeModule("y1");
        ht.makeModule("y2", "y1", "z2", "z3");

        Set<String> ys = Set.of("y1", "y2");
        Set<String> zs = Set.of("z1", "z2", "z3");

        // create JMOD files
        Stream.concat(ys.stream(), zs.stream()).forEach(ht::makeJmod);

        // run jmod hash command
        runJmod(List.of("hash", "--module-path", ht.lib.toString(),
                        "--hash-modules", ".*"));

        /*
         * z1 and y1 are the modules with hashes recorded.
         */
        ht.checkHashes("y1", "y2");
        ht.checkHashes("z1", "z2", "z3", "y2");
        Stream.concat(ys.stream(), zs.stream())
              .filter(mn -> !mn.equals("y1") && !mn.equals("z1"))
              .forEach(mn -> assertTrue(ht.hashes(mn) == null));
    }

    @Test
    public static void mixJmodAndJarFile() throws IOException {
        Path dest = Paths.get("test3");
        HashesTest ht = new HashesTest(dest);

        /*
         * j3 -----------> j2
         *    |______
         *    |      |
         *    V      V
         *    m3 -> m2
         *    |      |
         *    |      V
         *    |---> m1 -> j1 -> jdk.jlink
         */

        ht.makeModule("j1");
        ht.makeModule("j2");
        ht.makeModule("m1", "j1");
        ht.makeModule("m2", "m1");
        ht.makeModule("m3", "m1", "m2");

        ht.makeModule("j3", "j2", "m2", "m3");

        Set<String> jars = Set.of("j1", "j2", "j3");
        Set<String> jmods = Set.of("m1", "m2", "m3");

        // create JMOD and JAR files
        jars.forEach(ht::makeJar);
        jmods.forEach(ht::makeJmod);

        // run jmod hash command
        runJmod(List.of("hash", "--module-path", ht.lib.toString(),
                        "--hash-modules", "^j.*|^m.*"));

        /*
         * j1 and j2 are the modules with hashes recorded.
         */
        ht.checkHashes("j2", "j3");
        ht.checkHashes("j1", "m1", "m2", "m3", "j3");
        Stream.concat(jars.stream(), jmods.stream())
              .filter(mn -> !mn.equals("j1") && !mn.equals("j2"))
              .forEach(mn -> assertTrue(ht.hashes(mn) == null));
    }

    @Test
    public static void upgradeableModule() throws IOException {
        Path mpath = Paths.get(System.getProperty("java.home"), "jmods");
        if (!Files.exists(mpath)) {
            return;
        }

        Path dest = Paths.get("test4");
        HashesTest ht = new HashesTest(dest);
        ht.makeModule("m1");
        ht.makeModule("java.compiler", "m1");
        ht.makeModule("m2", "java.compiler");

        ht.makeJmod("m1");
        ht.makeJmod("m2");
        ht.makeJmod("java.compiler",
                    "--module-path",
                    ht.lib.toString() + File.pathSeparator + mpath,
                    "--hash-modules", "java\\.(?!se)|^m.*");

        ht.checkHashes("java.compiler",  "m2");
    }

    @Test
    public static void testImageJmods() throws IOException {
        Path mpath = Paths.get(System.getProperty("java.home"), "jmods");
        if (!Files.exists(mpath)) {
            return;
        }

        Path dest = Paths.get("test5");
        HashesTest ht = new HashesTest(dest);
        ht.makeModule("m1", "jdk.compiler", "jdk.attach");
        ht.makeModule("m2", "m1");
        ht.makeModule("m3", "java.compiler");

        ht.makeJmod("m1");
        ht.makeJmod("m2");

        runJmod(List.of("hash",
                        "--module-path",
                        mpath.toString() + File.pathSeparator + ht.lib.toString(),
                        "--hash-modules", ".*"));

        validateImageJmodsTest(ht, mpath);
    }

    @Test
    public static void testImageJmods1() throws IOException {
        Path mpath = Paths.get(System.getProperty("java.home"), "jmods");
        if (!Files.exists(mpath)) {
            return;
        }

        Path dest = Paths.get("test6");
        HashesTest ht = new HashesTest(dest);
        ht.makeModule("m1", "jdk.compiler", "jdk.attach");
        ht.makeModule("m2", "m1");
        ht.makeModule("m3", "java.compiler");

        ht.makeJar("m2");
        ht.makeJar("m1",
                    "--module-path",
                    mpath.toString() + File.pathSeparator + ht.lib.toString(),
                    "--hash-modules", ".*");
        validateImageJmodsTest(ht, mpath);
    }

    @Test
    public static void testReproducibibleHash() throws Exception {
        HashesTest ht = new HashesTest(Path.of("repro"));
        ht.makeModule("m4");
        ht.makeModule("m3", "m4");
        ht.makeModule("m2");
        ht.makeModule("m1", "m2", "m3");

        // create JMOD files and run jmod hash
        List.of("m1", "m2", "m3", "m4").forEach(ht::makeJmod);
        Map<String, ModuleHashes> hashes1 = ht.runJmodHash();

        // sleep a bit to be confident that the hashes aren't dependent on timestamps
        Thread.sleep(2000);

        // (re)create JMOD files and run jmod hash
        List.of("m1", "m2", "m3", "m4").forEach(ht::makeJmod);
        Map<String, ModuleHashes> hashes2 = ht.runJmodHash();

        // hashes should be equal
        assertEquals(hashes1, hashes2);
    }

    private static void validateImageJmodsTest(HashesTest ht, Path mpath)
        throws IOException
    {
        // hash is recorded in m1 and not any other packaged modules on module path
        ht.checkHashes("m1", "m2");
        assertTrue(ht.hashes("m2") == null);

        // should not override any JDK packaged modules
        ModuleFinder finder = ModulePath.of(Runtime.version(), true, mpath);
        assertTrue(ht.hashes(finder,"jdk.compiler") == null);
        assertTrue(ht.hashes(finder,"jdk.attach") == null);
    }

    private void checkHashes(String mn, String... hashModules) throws IOException {
        ModuleHashes hashes = hashes(mn);
        assertTrue(hashes.names().equals(Set.of(hashModules)));
    }

    private ModuleHashes hashes(String name) {
        ModuleFinder finder = ModulePath.of(Runtime.version(), true, lib);
        return hashes(finder, name);
    }

    private ModuleHashes hashes(ModuleFinder finder, String name) {
        ModuleReference mref = finder.find(name).orElseThrow(RuntimeException::new);
        try {
            ModuleReader reader = mref.open();
            try (InputStream in = reader.open("module-info.class").get()) {
                ModuleHashes hashes = ModuleInfo.read(in, null).recordedHashes();
                System.out.format("hashes in module %s %s%n", name,
                    (hashes != null) ? "present" : "absent");
                if (hashes != null) {
                    hashes.names().stream().sorted().forEach(n ->
                        System.out.format("  %s %s%n", n, toHex(hashes.hashFor(n)))
                    );
                }
                return hashes;
            } finally {
                reader.close();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private String toHex(byte[] ba) {
        StringBuilder sb = new StringBuilder(ba.length);
        for (byte b: ba) {
            sb.append(String.format("%02x", b & 0xff));
        }
        return sb.toString();
    }

    private void deleteDirectory(Path dir) throws IOException {
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException
            {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                throws IOException
            {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }


    private void makeModule(String mn, String... deps) throws IOException {
        makeModule(mn, null, deps);
    }

    private void makeModule(String mn, ModuleDescriptor.Requires.Modifier mod, String... deps)
        throws IOException
    {
        if (mod != null && mod != TRANSITIVE && mod != STATIC) {
            throw new IllegalArgumentException(mod.toString());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("module ")
          .append(mn)
          .append(" {")
          .append("\n");
        Arrays.stream(deps)
              .forEach(req -> {
                  sb.append("    requires ");
                  if (mod != null) {
                      sb.append(mod.toString().toLowerCase())
                        .append(" ");
                  }
                  sb.append(req)
                    .append(";\n");
              });
        sb.append("}\n");
        builder.writeJavaFiles(mn, sb.toString());
        builder.compile(mn, mods);
    }

    private void jmodHashModules(String moduleName, String hashModulesPattern) {
        makeJmod(moduleName, "--module-path", lib.toString(),
                 "--hash-modules", hashModulesPattern);
    }

    private void makeJmod(String moduleName, String... options) {
        Path mclasses = mods.resolve(moduleName);
        Path outfile = lib.resolve(moduleName + ".jmod");
        List<String> args = new ArrayList<>();
        args.add("create");
        Collections.addAll(args, options);
        Collections.addAll(args, "--class-path", mclasses.toString(),
                           outfile.toString());

        if (Files.exists(outfile)) {
            try {
                Files.delete(outfile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        runJmod(args);
    }

    /**
     * Execute jmod hash on the modules in the lib directory. Returns a map of
     * the modules, with the module name as the key, for the modules that have
     * a ModuleHashes class file attribute.
     */
    private Map<String, ModuleHashes> runJmodHash() {
        runJmod(List.of("hash",
                "--module-path", lib.toString(),
                "--hash-modules", ".*"));
        HashesTest ht = this;
        return ModulePath.of(Runtime.version(), true, lib)
                .findAll()
                .stream()
                .map(ModuleReference::descriptor)
                .map(ModuleDescriptor::name)
                .filter(mn -> ht.hashes(mn) != null)
                .collect(Collectors.toMap(mn -> mn, ht::hashes));
    }

    private static void runJmod(List<String> args) {
        int rc = JMOD_TOOL.run(System.out, System.out, args.toArray(new String[args.size()]));
        System.out.println("jmod " + args.stream().collect(Collectors.joining(" ")));
        if (rc != 0) {
            throw new AssertionError("jmod failed: rc = " + rc);
        }
    }

    private void makeJar(String moduleName, String... options) {
        Path mclasses = mods.resolve(moduleName);
        Path outfile = lib.resolve(moduleName + ".jar");
        List<String> args = new ArrayList<>();
        Stream.concat(Stream.of("--create",
                                "--file=" + outfile.toString()),
                      Arrays.stream(options))
              .forEach(args::add);
        args.add("-C");
        args.add(mclasses.toString());
        args.add(".");

        if (Files.exists(outfile)) {
            try {
                Files.delete(outfile);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        int rc = JAR_TOOL.run(System.out, System.out, args.toArray(new String[args.size()]));
        System.out.println("jar " + args.stream().collect(Collectors.joining(" ")));
        if (rc != 0) {
            throw new AssertionError("jar failed: rc = " + rc);
        }
    }
}
