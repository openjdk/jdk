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

/**
 * @test
 * @library /lib/testlibrary
 * @modules java.base/jdk.internal.module
 * @build MultiReleaseJarTest JarUtils
 * @run testng MultiReleaseJarTest
 * @run testng/othervm -Djdk.util.jar.enableMultiRelease=false MultiReleaseJarTest
 * @summary Basic test of ModuleReader with a modular JAR that is also a
 *          multi-release JAR
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.net.URLConnection;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;

import jdk.internal.module.ModuleInfoWriter;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * Exercises ModuleReader with a modular JAR containing the following files:
 *
 * <pre>{@code
 *     module-info.class
 *     META-INF/versions/<version>/module.class
 * }</pre>
 *
 * The module-info.class in the top-level directory is the binary form of:
 * <pre>{@code
 *     module jdk.test {
 *         requires java.base;
 *     }
 * }</pre>
 *
 * The module-info.class in the versioned section is the binary form of:
 * <pre>{@code
 *     module jdk.test {
 *         requires java.base;
 *         requires jdk.unsupported;
 *     }
 * }</pre>
 */

@Test
public class MultiReleaseJarTest {

    // Java SE/JDK major release
    private static final int RELEASE = Runtime.version().major();

    // the name of the test module
    private static final String MODULE_NAME = "jdk.test";

    private static final String MODULE_INFO_CLASS = "module-info.class";

    /**
     * Uses the ModuleFinder API to locate the module packaged as a modular
     * and mutli-release JAR and then creates a ModuleReader to access the
     * contents of the module.
     */
    public void testMultiReleaseJar() throws IOException {

        // are multi-release JARs enabled?
        String s = System.getProperty("jdk.util.jar.enableMultiRelease");
        boolean multiRelease = (s == null || Boolean.parseBoolean(s));

        // create the multi-release modular JAR
        Path jarfile = createJarFile();

        // find the module
        ModuleFinder finder = ModuleFinder.of(jarfile);
        Optional<ModuleReference> omref = finder.find(MODULE_NAME);
        assertTrue((omref.isPresent()));
        ModuleReference mref = omref.get();

        // test that correct module-info.class was read
        checkDescriptor(mref.descriptor(), multiRelease);

        // test ModuleReader
        try (ModuleReader reader = mref.open()) {

            // open resource
            Optional<InputStream> oin = reader.open(MODULE_INFO_CLASS);
            assertTrue(oin.isPresent());
            try (InputStream in = oin.get()) {
                checkDescriptor(ModuleDescriptor.read(in), multiRelease);
            }

            // read resource
            Optional<ByteBuffer> obb = reader.read(MODULE_INFO_CLASS);
            assertTrue(obb.isPresent());
            ByteBuffer bb = obb.get();
            try {
                checkDescriptor(ModuleDescriptor.read(bb), multiRelease);
            } finally {
                reader.release(bb);
            }

            // find resource
            Optional<URI> ouri = reader.find(MODULE_INFO_CLASS);
            assertTrue(ouri.isPresent());
            URI uri = ouri.get();

            String expectedTail = "!/";
            if (multiRelease)
                expectedTail += "META-INF/versions/" + RELEASE + "/";
            expectedTail += MODULE_INFO_CLASS;
            assertTrue(uri.toString().endsWith(expectedTail));

            URLConnection uc = uri.toURL().openConnection();
            uc.setUseCaches(false);
            try (InputStream in = uc.getInputStream()) {
                checkDescriptor(ModuleDescriptor.read(in), multiRelease);
            }

        }

    }

    /**
     * Checks that the module descriptor is the expected module descriptor.
     * When the multi release JAR feature is enabled then the module
     * descriptor is expected to have been read from the versioned section
     * of the JAR file.
     */
    private void checkDescriptor(ModuleDescriptor descriptor, boolean multiRelease) {
        Set<String> requires = descriptor.requires().stream()
                .map(ModuleDescriptor.Requires::name)
                .collect(Collectors.toSet());
        assertTrue(requires.contains("java.base"));
        assertTrue(requires.contains("jdk.unsupported") == multiRelease);
    }

    /**
     * Creates the modular JAR for the test, returning the Path to the JAR file.
     */
    private Path createJarFile() throws IOException {

        // module descriptor for top-level directory
        ModuleDescriptor descriptor1
            = new ModuleDescriptor.Builder(MODULE_NAME)
                .requires("java.base")
                .build();

        // module descriptor for versioned section
        ModuleDescriptor descriptor2
            = new ModuleDescriptor.Builder(MODULE_NAME)
                .requires("java.base")
                .requires("jdk.unsupported")
                .build();

        Path top = Paths.get(MODULE_NAME);
        Files.createDirectories(top);

        Path mi1 = Paths.get(MODULE_INFO_CLASS);
        try (OutputStream out = Files.newOutputStream(top.resolve(mi1))) {
            ModuleInfoWriter.write(descriptor1, out);
        }

        Path vdir = Paths.get("META-INF", "versions", Integer.toString(RELEASE));
        Files.createDirectories(top.resolve(vdir));

        Path mi2 = vdir.resolve(MODULE_INFO_CLASS);
        try (OutputStream out = Files.newOutputStream(top.resolve(mi2))) {
            ModuleInfoWriter.write(descriptor2, out);
        }

        Manifest man = new Manifest();
        Attributes attrs = man.getMainAttributes();
        attrs.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        attrs.put(Attributes.Name.MULTI_RELEASE, "true");

        Path jarfile = Paths.get(MODULE_NAME + ".jar");
        JarUtils.createJarFile(jarfile, man, top, mi1, mi2);

        return jarfile;
    }
}
