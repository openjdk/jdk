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
 * @summary Test the recording and checking of module hashes
 * @author Andrei Eremeev
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 *          jdk.jlink
 *          jdk.compiler
 * @build CompilerUtils
 * @run testng HashesTest
 */

import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.lang.reflect.Method;
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
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Collectors;

import jdk.internal.module.ConfigurableModuleFinder;
import jdk.internal.module.ModuleHashes;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import static org.testng.Assert.*;

public class HashesTest {
    static final ToolProvider JMOD_TOOL = ToolProvider.findFirst("jmod")
        .orElseThrow(() ->
            new RuntimeException("jmod tool not found")
        );

    private final Path testSrc = Paths.get(System.getProperty("test.src"));
    private final Path modSrc = testSrc.resolve("src");
    private final Path mods = Paths.get("mods");
    private final Path jmods = Paths.get("jmods");
    private final String[] modules = new String[] { "m1", "m2", "m3"};

    private static Method hashesMethod;
    @BeforeTest
    private void setup() throws Exception {
        if (Files.exists(jmods)) {
            deleteDirectory(jmods);
        }
        Files.createDirectories(jmods);

        // build m2, m3 required by m1
        compileModule("m2", modSrc);
        jmod("m2");

        compileModule("m3", modSrc);
        jmod("m3");

        // build m1
        compileModule("m1", modSrc);
        // no hash is recorded since m1 has outgoing edges
        jmod("m1", "--module-path", jmods.toString(), "--hash-modules", ".*");

        // compile org.bar and org.foo
        compileModule("org.bar", modSrc);
        compileModule("org.foo", modSrc);

        try {
            hashesMethod = ModuleDescriptor.class.getDeclaredMethod("hashes");
            hashesMethod.setAccessible(true);
        } catch (ReflectiveOperationException x) {
            throw new InternalError(x);
        }
    }

    @Test
    public void test() throws Exception {
        for (String mn : modules) {
            assertFalse(hashes(mn).isPresent());
        }

        // hash m1 in m2
        jmod("m2", "--module-path", jmods.toString(), "--hash-modules", "m1");
        checkHashes(hashes("m2").get(), "m1");

        // hash m1 in m2
        jmod("m2", "--module-path", jmods.toString(), "--hash-modules", ".*");
        checkHashes(hashes("m2").get(), "m1");

        // create m2.jmod with no hash
        jmod("m2");
        // run jmod hash command to hash m1 in m2 and m3
        runJmod(Arrays.asList("hash", "--module-path", jmods.toString(),
                "--hash-modules", ".*"));
        checkHashes(hashes("m2").get(), "m1");
        checkHashes(hashes("m3").get(), "m1");

        jmod("org.bar");
        jmod("org.foo");

        jmod("org.bar", "--module-path", jmods.toString(), "--hash-modules", "org.*");
        checkHashes(hashes("org.bar").get(), "org.foo");

        jmod("m3", "--module-path", jmods.toString(), "--hash-modules", ".*");
        checkHashes(hashes("m3").get(), "org.foo", "org.bar", "m1");
    }

    private void checkHashes(ModuleHashes hashes, String... hashModules) {
        assertTrue(hashes.names().equals(Set.of(hashModules)));
    }

    private Optional<ModuleHashes> hashes(String name) throws Exception {
        ModuleFinder finder = ModuleFinder.of(jmods.resolve(name + ".jmod"));
        if (finder instanceof ConfigurableModuleFinder) {
            ((ConfigurableModuleFinder) finder)
                .configurePhase(ConfigurableModuleFinder.Phase.LINK_TIME);
        }
        ModuleReference mref = finder.find(name).orElseThrow(RuntimeException::new);
        ModuleReader reader = mref.open();
        try (InputStream in = reader.open("module-info.class").get()) {
            ModuleDescriptor md = ModuleDescriptor.read(in);
            Optional<ModuleHashes> hashes =
                (Optional<ModuleHashes>) hashesMethod.invoke(md);
            System.out.format("hashes in module %s %s%n", name,
                              hashes.isPresent() ? "present" : "absent");
            if (hashes.isPresent()) {
                hashes.get().names().stream()
                    .sorted()
                    .forEach(n -> System.out.format("  %s %s%n", n, hashes.get().hashFor(n)));
            }
            return hashes;
        } finally {
            reader.close();
        }
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

    private void compileModule(String moduleName, Path src) throws IOException {
        Path msrc = src.resolve(moduleName);
        assertTrue(CompilerUtils.compile(msrc, mods, "--module-source-path", src.toString()));
    }

    private void jmod(String moduleName, String... options) throws IOException {
        Path mclasses = mods.resolve(moduleName);
        Path outfile = jmods.resolve(moduleName + ".jmod");
        List<String> args = new ArrayList<>();
        args.add("create");
        Collections.addAll(args, options);
        Collections.addAll(args, "--class-path", mclasses.toString(),
                           outfile.toString());

        if (Files.exists(outfile))
            Files.delete(outfile);

        runJmod(args);
    }

    private void runJmod(List<String> args) {
        int rc = JMOD_TOOL.run(System.out, System.out, args.toArray(new String[args.size()]));
        System.out.println("jmod options: " + args.stream().collect(Collectors.joining(" ")));
        if (rc != 0) {
            throw new AssertionError("Jmod failed: rc = " + rc);
        }
    }
}
