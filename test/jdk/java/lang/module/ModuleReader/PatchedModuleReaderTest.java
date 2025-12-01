/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import jdk.internal.module.ModulePatcher;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @summary verify the implementation of jdk.internal.module.ModulePatcher$PatchedModuleReader
 * @modules java.base/jdk.internal.module
 * @run junit ${test.main.class}
 */
class PatchedModuleReaderTest {

    /*
     * The test creates a jdk.internal.module.ModulePatcher$PatchedModuleReader and verifies
     * that after closing that PatchedModuleReader, subsequent operations throw the specified
     * IOException.
     */
    @Test
    void testIOExceptionUponClose() throws IOException {
        // create a JAR file with an arbitrary resource
        final String resourceName = "testresource";
        final Path tmpJarFile = Files.createTempFile(Path.of("."), "test-", ".jar");
        try (JarOutputStream jarout = new JarOutputStream(Files.newOutputStream(tmpJarFile))) {
            jarout.putNextEntry(new JarEntry(resourceName));
            jarout.write(new byte[]{0x01});
            jarout.closeEntry();
        }

        // create a test module/module reference
        final String moduleName = "foo.bar";
        final ModuleDescriptor md = ModuleDescriptor.newModule(moduleName).build();
        final ModuleReference dummyModuleRef = new ModuleReference(md, tmpJarFile.toUri()) {
            @Override
            public ModuleReader open() {
                return new NoOpModuleReader();
            }
        };
        // create a ModulePatcher pointing to the test module and the test JAR file
        final ModulePatcher patcher = new ModulePatcher(Map.of(moduleName, List.of(tmpJarFile.toString())));
        final ModuleReference maybePatched = patcher.patchIfNeeded(dummyModuleRef);
        final ModuleReader reader;
        // open a ModuleReader and verify that the test resource exists
        try (var _ = reader = maybePatched.open()) {
            System.err.println("using ModuleReader: " + reader);
            final Optional<URI> res = reader.find(resourceName);
            assertTrue(res.isPresent(), "missing resource");
        }
        // now that the ModuleReader is closed, verify that an IOException gets thrown
        // when using that closed ModuleReader
        assertThrows(IOException.class, () -> reader.find(resourceName));
        assertThrows(IOException.class, () -> reader.read(resourceName));
        assertThrows(IOException.class, () -> reader.open(resourceName));
        assertThrows(IOException.class, () -> reader.list());
    }

    private static final class NoOpModuleReader implements ModuleReader {

        @Override
        public Optional<URI> find(String name) {
            return Optional.empty();
        }

        @Override
        public Stream<String> list() {
            return Stream.empty();
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
