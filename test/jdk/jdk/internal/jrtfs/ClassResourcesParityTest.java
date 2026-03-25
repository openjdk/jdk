/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/*
 * @test id=no-enable-preview
 * @requires !java.enablePreview
 * @summary Ensures parity between the JRT file system and Class.getResourceAsStream()
 * @run junit/othervm -esa -ea ClassResourcesParityTest
 */

/*
 * @test id=enable-preview
 * @enablePreview
 * @summary Ensures parity between the JRT file system and Class.getResourceAsStream()
 * @run junit/othervm -esa -ea ClassResourcesParityTest
 */
public class ClassResourcesParityTest {
    private static final String CLASS_SUFFIX = ".class";
    // Testing only 'java.base' is more than enough for what we're testing.
    private static final Path JAVA_BASE =
            FileSystems.getFileSystem(URI.create("jrt:/")).getPath("/modules/java.base");

    @Test
    public void testGetResourceAsStreamParity() throws IOException {
        Path moduleInfo = JAVA_BASE.resolve("module-info.class");
        try (Stream<Path> classFiles = Files.walk(JAVA_BASE)
                .filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().endsWith(".class"))
                .filter(p -> !p.equals(moduleInfo))) {
            classFiles.forEach(ClassResourcesParityTest::testMatchesGetResourceAsStream);
        }
    }

    private static void testMatchesGetResourceAsStream(Path jrtClassFile) {
        try {
            byte[] jrtBytes = Files.readAllBytes(jrtClassFile);
            // Remove /modules/<mod-name>/ leading segments.
            String relPath = jrtClassFile.subpath(2, jrtClassFile.getNameCount()).toString();
            String fqn = relPath.substring(0, relPath.length() - CLASS_SUFFIX.length()).replace('/', '.');
            String baseName = fqn.substring(fqn.lastIndexOf('.') + 1);
            Class<?> cls = assertDoesNotThrow(() -> Class.forName(fqn, false, null), "Failed to load: " + fqn);
            byte[] classBytes;
            try (InputStream is = cls.getResourceAsStream(baseName + CLASS_SUFFIX)) {
                classBytes = Objects.requireNonNull(is).readAllBytes();
            }
            assertArrayEquals(jrtBytes, classBytes, "Class: " + fqn);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
