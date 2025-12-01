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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8372787
 * @summary verify the behaviour of ModuleReader when using --patch-module
 * @comment patch the java.base module with a test specific resource
 * @compile/module=java.base java/lang/PatchedFoo.java
 * @run junit/othervm ${test.main.class}
 */
class PatchedModuleReaderTest {

    /*
     * This test opens a ModuleReader for a patched module, accumulates
     * the Stream of resources from that ModuleReader and then closes that
     * ModuleReader. It then verifies that the closed ModuleReader
     * throws the specified IOException whenever it is used for subsequent
     * operations on the Stream of resources.
     */
    @Test
    void testIOExceptionUponClose() throws Exception {
        final ModuleReference mref = ModuleFinder.ofSystem()
                .find("java.base")
                .orElseThrow();
        final ModuleReader reader;
        final Stream<String> resources;
        final String nonExistentResource = "foo/bar/NonExistent.class";
        // open the ModuleReader
        try (var _ = reader = mref.open()) {
            // verify we are using the patched module
            final String resourceName = "java/lang/PatchedFoo.class";
            final Optional<URI> res = reader.find(resourceName);
            assertTrue(res.isPresent(), resourceName + " is missing in "
                    + mref.descriptor().name() + " module");
            // test a non-existent resource
            assertTrue(reader.find(nonExistentResource).isEmpty(),
                    "unexpected resource " + nonExistentResource + " in "
                            + mref.descriptor().name() + " module");
            // hold on to the available resources, to test them after the
            // ModuleReader is closed
            resources = reader.list();
        } // close the ModuleReader

        // verify IOException is thrown by the closed ModuleReader
        resources.forEach(rn -> {
            assertThrows(IOException.class, () -> reader.read(rn),
                    "ModuleReader.read(String)");
            assertThrows(IOException.class, () -> reader.open(rn),
                    "ModuleReader.open(String)");
            assertThrows(IOException.class, () -> reader.find(rn),
                    "ModuleReader.find(String)");
        });

        // repeat the test for a non-existent resource
        assertThrows(IOException.class, () -> reader.read(nonExistentResource),
                "ModuleReader.read(String)");
        assertThrows(IOException.class, () -> reader.open(nonExistentResource),
                "ModuleReader.open(String)");
        assertThrows(IOException.class, () -> reader.find(nonExistentResource),
                "ModuleReader.find(String)");
    }
}
