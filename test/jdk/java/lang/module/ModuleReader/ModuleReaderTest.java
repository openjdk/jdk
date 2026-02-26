/*
 * Copyright (c) 2015, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8142968 8300228
 * @library /test/lib
 * @modules java.base/jdk.internal.module
 *          jdk.compiler
 *          jdk.jlink
 * @build ModuleReaderTest
 *        jdk.test.lib.compiler.CompilerUtils
 *        jdk.test.lib.util.JarUtils
 * @run junit ModuleReaderTest
 * @summary Basic tests for java.lang.module.ModuleReader
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import jdk.internal.module.ModulePath;
import jdk.test.lib.compiler.CompilerUtils;
import jdk.test.lib.util.JarUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ModuleReaderTest {
    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path USER_DIR   = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_DIR    = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR   = Paths.get("mods");

    // the module name of the base module
    private static final String BASE_MODULE = "java.base";

    // the module name of the test module
    private static final String TEST_MODULE = "m";

    // resources in the base module
    private static final String[] BASE_RESOURCES = {
        "java/lang/Object.class"
    };

    // (directory) resources that may be in the base module
    private static final String[] MAYBE_BASE_RESOURCES = {
        "java",
        "java/",
        "java/lang",
        "java/lang/",
    };

    // resource names that should not be found in the base module
    private static final String[] NOT_BASE_RESOURCES = {
        "NotFound",
        "/java",
        "//java",
        "/java/lang",
        "//java/lang",
        "java//lang",
        "/java/lang/Object.class",
        "//java/lang/Object.class",
        "java/lang/Object.class/",
        "java//lang//Object.class",
        "./java/lang/Object.class",
        "java/./lang/Object.class",
        "java/lang/./Object.class",
        "../java/lang/Object.class",
        "java/../lang/Object.class",
        "java/lang/../Object.class",

        // junk resource names
        "java\u0000",
        "C:java",
        "C:\\java",
        "java\\lang\\Object.class"
    };

    // resources in test module (can't use module-info.class as a test
    // resource as it will be modified by the jmod tool)
    private static final String[] TEST_RESOURCES = {
        "p/Main.class"
    };

    // (directory) resources that may be in the test module
    private static final String[] MAYBE_TEST_RESOURCES = {
        "p",
        "p/"
    };

    // resource names that should not be found in the test module
    private static final String[] NOT_TEST_RESOURCES = {
        "NotFound",
        "/p",
        "//p",
        "/p/Main.class",
        "//p/Main.class",
        "p/Main.class/",
        "p//Main.class",
        "./p/Main.class",
        "p/./Main.class",
        "../p/Main.class",
        "p/../p/Main.class",

        // junk resource names
        "p\u0000",
        "C:p",
        "C:\\p",
        "p\\Main.class"
    };

    @BeforeAll
    public static void compileTestModule() throws Exception {
        // javac -d mods/$TESTMODULE src/$TESTMODULE/**
        boolean compiled = CompilerUtils.compile(SRC_DIR.resolve(TEST_MODULE),
                                                 MODS_DIR.resolve(TEST_MODULE));
        assertTrue(compiled, "test module did not compile");
    }

    /**
     * Test ModuleReader with module in runtime image.
     */
    @Test
    public void testImage() throws IOException {
        ModuleFinder finder = ModuleFinder.ofSystem();
        ModuleReference mref = finder.find(BASE_MODULE).get();
        ModuleReader reader = mref.open();

        try (reader) {

            for (String name : BASE_RESOURCES) {
                byte[] expectedBytes;
                Module baseModule = Object.class.getModule();
                try (InputStream in = baseModule.getResourceAsStream(name)) {
                    expectedBytes = in.readAllBytes();
                }

                testFind(reader, name, expectedBytes);
                testOpen(reader, name, expectedBytes);
                testRead(reader, name, expectedBytes);
                testList(reader, name);
            }

            // test resources that may be in the base module
            for (String name : MAYBE_BASE_RESOURCES) {
                Optional<URI> ouri = reader.find(name);
                ouri.ifPresent(uri -> {
                    if (name.endsWith("/"))
                        assertTrue(uri.toString().endsWith("/"),
                                "mismatched directory URI for '" + name + "': " + uri);
                });
            }

            // test "not found" in java.base module
            for (String name : NOT_BASE_RESOURCES) {
                assertFalse(reader.find(name).isPresent(), "Unexpected resource found: " + name);
                assertFalse(reader.open(name).isPresent(), "Unexpected resource opened: " + name);
                assertFalse(reader.read(name).isPresent(), "Unexpected resource read: " + name);
            }

            // test nulls
            assertThrows(NullPointerException.class, () -> reader.find(null));
            assertThrows(NullPointerException.class, () -> reader.open(null));
            assertThrows(NullPointerException.class, () -> reader.read(null));
            assertThrows(NullPointerException.class, () -> reader.release(null));
        }

        // test closed ModuleReader
        assertThrows(IOException.class, () -> reader.open(BASE_RESOURCES[0]));
        assertThrows(IOException.class, () -> reader.read(BASE_RESOURCES[0]));
        assertThrows(IOException.class, reader::list);
    }

    /**
     * Test ModuleReader with exploded module.
     */
    @Test
    public void testExplodedModule() throws IOException {
        test(MODS_DIR);
    }

    /**
     * Test ModuleReader with module in modular JAR.
     */
    @Test
    public void testModularJar() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        // jar cf mlib/${TESTMODULE}.jar -C mods .
        JarUtils.createJarFile(dir.resolve("m.jar"),
                               MODS_DIR.resolve(TEST_MODULE));

        test(dir);
    }

    /**
     * Test ModuleReader with module in a JMOD file.
     */
    @Test
    public void testJMod() throws IOException {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        // jmod create --class-path mods/${TESTMODULE}  mlib/${TESTMODULE}.jmod
        String cp = MODS_DIR.resolve(TEST_MODULE).toString();
        String jmod = dir.resolve("m.jmod").toString();
        String[] args = { "create", "--class-path", cp, jmod };
        ToolProvider jmodTool = ToolProvider.findFirst("jmod")
                .orElseThrow(() ->
                        new RuntimeException("jmod tool not found")
                );
        assertEquals(0, jmodTool.run(System.out, System.out, args), "jmod tool failed");

        test(dir);
    }

    /**
     * The test module is found on the given module path. Open a ModuleReader
     * to the test module and test the reader.
     */
    void test(Path mp) throws IOException {
        ModuleFinder finder = ModulePath.of(Runtime.version(), true, mp);
        ModuleReference mref = finder.find(TEST_MODULE).get();
        ModuleReader reader = mref.open();

        try (reader) {

            // test resources in test module
            for (String name : TEST_RESOURCES) {
                System.out.println("resource: " + name);
                byte[] expectedBytes
                    = Files.readAllBytes(MODS_DIR
                        .resolve(TEST_MODULE)
                        .resolve(name.replace('/', File.separatorChar)));

                testFind(reader, name, expectedBytes);
                testOpen(reader, name, expectedBytes);
                testRead(reader, name, expectedBytes);
                testList(reader, name);
            }

            // test resources that may be in the test module
            for (String name : MAYBE_TEST_RESOURCES) {
                System.out.println("resource: " + name);
                Optional<URI> ouri = reader.find(name);
                ouri.ifPresent(uri -> {
                    if (name.endsWith("/"))
                        assertTrue(uri.toString().endsWith("/"),
                                "mismatched directory URI for '" + name + "': " + uri);
                });
            }

            // test "not found" in test module
            for (String name : NOT_TEST_RESOURCES) {
                System.out.println("resource: " + name);
                assertFalse(reader.find(name).isPresent(), "Unexpected resource found: " + name);
                assertFalse(reader.open(name).isPresent(), "Unexpected resource open: " + name);
                assertFalse(reader.read(name).isPresent(), "Unexpected resource read: " + name);
            }

            // test nulls
            assertThrows(NullPointerException.class, () -> reader.find(null));
            assertThrows(NullPointerException.class, () -> reader.open(null));
            assertThrows(NullPointerException.class, () -> reader.read(null));
            assertThrows(NullPointerException.class, () -> reader.release(null));
        }

        // test closed ModuleReader
        assertThrows(IOException.class, () -> reader.open(BASE_RESOURCES[0]));
        assertThrows(IOException.class, () -> reader.read(BASE_RESOURCES[0]));
        assertThrows(IOException.class, reader::list);
    }

    /**
     * Test ModuleReader#find
     */
    void testFind(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<URI> ouri = reader.find(name);
        assertTrue(ouri.isPresent(), "missing URI for: " + name);

        URL url = ouri.get().toURL();
        if (!url.getProtocol().equalsIgnoreCase("jmod")) {
            URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            try (InputStream in = uc.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                assertArrayEquals(expectedBytes, bytes, "resource bytes differ for: " + name);
            }
        }
    }

    /**
     * Test ModuleReader#open
     */
    void testOpen(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<InputStream> oin = reader.open(name);
        assertTrue(oin.isPresent(), "missing input stream for: " + name);
        try (InputStream in = oin.get()) {
            byte[] bytes = in.readAllBytes();
            assertArrayEquals(expectedBytes, bytes, "resource bytes differ for: " + name);
        }
    }

    /**
     * Test ModuleReader#read
     */
    void testRead(ModuleReader reader, String name, byte[] expectedBytes)
        throws IOException
    {
        Optional<ByteBuffer> obb = reader.read(name);
        assertTrue(obb.isPresent());

        ByteBuffer bb = obb.get();
        try {
            int rem = bb.remaining();
            assertEquals(expectedBytes.length, rem, "resource lengths differ: " + name);
            byte[] bytes = new byte[rem];
            bb.get(bytes);
            assertArrayEquals(expectedBytes, bytes, "resource bytes differ: " + name);
        } finally {
            reader.release(bb);
        }
    }

    /**
     * Test ModuleReader#list
     */
    void testList(ModuleReader reader, String name) throws IOException {
        final List<String> list;
        try (Stream<String> stream = reader.list()) {
            list = stream.toList();
        }
        Set<String> names = new HashSet<>(list);
        assertEquals(names.size(), list.size(), "resource list contains duplicates: " + list);

        assertTrue(names.contains("module-info.class"), "resource list did not contain 'module-info.class': " + list);
        assertTrue(names.contains(name), "resource list did not contain '" + name + "'" + list);

        // all resources should be locatable via find
        for (String e : names) {
            assertTrue(reader.find(e).isPresent(), "resource not found: " + name);
        }
    }

}
