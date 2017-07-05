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

/**
 * @test
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 *          jdk.jlink/jdk.tools.jmod
 *          jdk.compiler
 * @build ModuleReaderTest CompilerUtils JarUtils
 * @run testng ModuleReaderTest
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
import java.util.Arrays;
import java.util.Optional;

import jdk.internal.module.ConfigurableModuleFinder;
import jdk.internal.module.ConfigurableModuleFinder.Phase;

import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class ModuleReaderTest {

    private static final String TEST_SRC = System.getProperty("test.src");

    private static final Path USER_DIR   = Paths.get(System.getProperty("user.dir"));
    private static final Path SRC_DIR    = Paths.get(TEST_SRC, "src");
    private static final Path MODS_DIR   = Paths.get("mods");

    // the module name of the test module
    private static final String TEST_MODULE = "m";

    // resources in test module (can't use module-info.class as a test
    // resource as it will be modified by the jmod tool)
    private static final String[] RESOURCES = {
        "p/Main.class"
    };

    // a resource that is not in the test module
    private static final String NOT_A_RESOURCE = "NotAResource";


    @BeforeTest
    public void compileTestModule() throws Exception {

        // javac -d mods/$TESTMODULE src/$TESTMODULE/**
        boolean compiled
            = CompilerUtils.compile(SRC_DIR.resolve(TEST_MODULE),
                                    MODS_DIR.resolve(TEST_MODULE));
        assertTrue(compiled, "test module did not compile");
    }


    /**
     * Test exploded module
     */
    public void testExplodedModule() throws Exception {
        test(MODS_DIR);
    }


    /**
     * Test modular JAR
     */
    public void testModularJar() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        // jar cf mlib/${TESTMODULE}.jar -C mods .
        JarUtils.createJarFile(dir.resolve("m.jar"),
                               MODS_DIR.resolve(TEST_MODULE));

        test(dir);
    }


    /**
     * Test JMOD
     */
    public void testJMod() throws Exception {
        Path dir = Files.createTempDirectory(USER_DIR, "mlib");

        // jmod create --class-path mods/${TESTMODULE}  mlib/${TESTMODULE}.jmod
        String cp = MODS_DIR.resolve(TEST_MODULE).toString();
        String jmod = dir.resolve("m.jmod").toString();
        String[] args = { "create", "--class-path", cp, jmod };
        jdk.tools.jmod.JmodTask task = new jdk.tools.jmod.JmodTask();
        assertEquals(task.run(args), 0);

        test(dir);
    }


    /**
     * The test module is found on the given module path. Open a ModuleReader
     * to the test module and test the reader.
     */
    void test(Path mp) throws Exception {

        ModuleFinder finder = ModuleFinder.of(mp);
        if (finder instanceof ConfigurableModuleFinder) {
            // need ModuleFinder to be in the phase to find JMOD files
            ((ConfigurableModuleFinder)finder).configurePhase(Phase.LINK_TIME);
        }

        ModuleReference mref = finder.find(TEST_MODULE).get();
        ModuleReader reader = mref.open();

        try (reader) {

            // test each of the known resources in the module
            for (String name : RESOURCES) {
                byte[] expectedBytes
                    = Files.readAllBytes(MODS_DIR
                        .resolve(TEST_MODULE)
                        .resolve(name.replace('/', File.separatorChar)));

                testFind(reader, name, expectedBytes);
                testOpen(reader, name, expectedBytes);
                testRead(reader, name, expectedBytes);
            }

            // test "not found"
            assertFalse(reader.open(NOT_A_RESOURCE).isPresent());
            assertFalse(reader.read(NOT_A_RESOURCE).isPresent());

            // test nulls
            try {
                reader.find(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.open(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            try {
                reader.read(null);
                assertTrue(false);
            } catch (NullPointerException expected) { }

            // should release(null) throw NPE?

        }

        // test closed ModuleReader
        try {
            reader.open(RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }


        try {
            reader.read(RESOURCES[0]);
            assertTrue(false);
        } catch (IOException expected) { }
    }

    /**
     * Test ModuleReader#find
     */
    void testFind(ModuleReader reader, String name, byte[] expectedBytes)
        throws Exception
    {
        Optional<URI> ouri = reader.find(name);
        assertTrue(ouri.isPresent());

        URL url = ouri.get().toURL();
        if (!url.getProtocol().equalsIgnoreCase("jmod")) {
            URLConnection uc = url.openConnection();
            uc.setUseCaches(false);
            try (InputStream in = uc.getInputStream()) {
                byte[] bytes = in.readAllBytes();
                assertTrue(Arrays.equals(bytes, expectedBytes));
            }
        }
    }

    /**
     * Test ModuleReader#open
     */
    void testOpen(ModuleReader reader, String name, byte[] expectedBytes)
        throws Exception
    {
        Optional<InputStream> oin = reader.open(name);
        assertTrue(oin.isPresent());

        InputStream in = oin.get();
        try (in) {
            byte[] bytes = in.readAllBytes();
            assertTrue(Arrays.equals(bytes, expectedBytes));
        }
    }

    /**
     * Test ModuleReader#read
     */
    void testRead(ModuleReader reader, String name, byte[] expectedBytes)
        throws Exception
    {
        Optional<ByteBuffer> obb = reader.read(name);
        assertTrue(obb.isPresent());

        ByteBuffer bb = obb.get();
        try {
            int rem = bb.remaining();
            assertTrue(rem == expectedBytes.length);
            byte[] bytes = new byte[rem];
            bb.get(bytes);
            assertTrue(Arrays.equals(bytes, expectedBytes));
        } finally {
            reader.release(bb);
        }
    }

}
