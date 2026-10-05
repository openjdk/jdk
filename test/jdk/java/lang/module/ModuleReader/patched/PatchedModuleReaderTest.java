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
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8372787
 * @summary Test the behaviour of ModuleReader when using --patch-module
 * @comment patch the java.base module with a test specific resource
 * @compile/module=java.base java/lang/PatchedFoo.java
 * @run junit/othervm ${test.main.class}
 */
class PatchedModuleReaderTest {

    private static ModuleReference patchedModuleRef;

    @BeforeAll
    static void beforeAll() {
        patchedModuleRef = ModuleFinder.ofSystem()
                .find("java.base")
                .orElseThrow();
    }

    /*
     * Verifies that the resource that was patched into a module
     * is found by the ModuleReader.
     */
    @Test
    void testResourceFound() throws Exception {
        try (ModuleReader reader = patchedModuleRef.open()) {
            String resourceName = "java/lang/PatchedFoo.class";
            Optional<URI> res = reader.find(resourceName);
            assertTrue(res.isPresent(), resourceName + " is missing in "
                    + patchedModuleRef.descriptor().name() + " module");
            URI uri = res.get();
            assertEquals("file", uri.getScheme(),
                    "unexpected scheme in resource URI " + uri);
            assertTrue(uri.getPath().endsWith(resourceName),
                    "unexpected path component " + uri.getPath()
                            + " in resource URI " + uri);

        }
    }

    /*
     * Verifies the ModuleReader against a resource which isn't
     * expected to be part of the patched module.
     */
    @Test
    void testResourceNotFound() throws Exception {
        try (ModuleReader reader = patchedModuleRef.open()) {
            String nonExistentResource = "foo/bar/NonExistent.class";
            Optional<URI> res = reader.find(nonExistentResource);
            assertTrue(res.isEmpty(), "unexpected resource " + nonExistentResource
                    + " in " + patchedModuleRef.descriptor().name() + " module");
        }
    }

    /*
     * This test opens a ModuleReader for a patched module, accumulates
     * the Stream of resources from that ModuleReader and then closes that
     * ModuleReader. It then verifies that the closed ModuleReader
     * throws the specified IOException whenever it is used for subsequent
     * operations on the Stream of resources.
     */
    @Test
    void testIOExceptionAfterClose() throws Exception {
        ModuleReader reader;
        Stream<String> resources;
        try (var _ = reader = patchedModuleRef.open()) {
            // hold on to the available resources, to test them after the
            // ModuleReader is closed
            resources = reader.list();
        } // close the ModuleReader

        // verify IOException is thrown by the closed ModuleReader

        assertThrows(IOException.class, () -> reader.list(),
                "ModuleReader.list()");

        resources.forEach(rn -> {
            assertThrows(IOException.class, () -> reader.read(rn),
                    "ModuleReader.read(String)");
            assertThrows(IOException.class, () -> reader.open(rn),
                    "ModuleReader.open(String)");
            assertThrows(IOException.class, () -> reader.find(rn),
                    "ModuleReader.find(String)");
        });

        // repeat the test for a non-existent resource
        String nonExistentResource = "foo/bar/NonExistent.class";
        assertThrows(IOException.class, () -> reader.read(nonExistentResource),
                "ModuleReader.read(String)");
        assertThrows(IOException.class, () -> reader.open(nonExistentResource),
                "ModuleReader.open(String)");
        assertThrows(IOException.class, () -> reader.find(nonExistentResource),
                "ModuleReader.find(String)");
    }
}
