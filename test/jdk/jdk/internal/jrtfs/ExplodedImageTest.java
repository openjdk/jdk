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

import jdk.internal.jrtfs.ExplodedImageHelper;
import jdk.internal.jrtfs.ExplodedImageHelper.Node;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8352750
 * @summary Tests ExplodedImage creation from local files/directories.
 * @compile/module=java.base jdk/internal/jrtfs/ExplodedImageHelper.java
 * @modules java.base/jdk.internal.jrtfs
 * @run junit/othervm ExplodedImageTest
 */
public class ExplodedImageTest {
    @ParameterizedTest
    @ValueSource(strings =
            {       // Basic paths.
                    """
                    abc/foo/bar/baz.txt
                    java.base/java/lang/Integer.class
                    """,
                    // Packages appearing in several modules.
                    """
                    one/foo/bar/Bar1.class
                    two/foo/bar/Bar2.class
                    two/foo/baz/Baz1.class
                    three/foo/baz/Baz2.class
                    """})
    public void testBasicNodes(String fileTree, @TempDir Path rootDir) throws IOException {
        String separator = rootDir.getFileSystem().getSeparator();
        List<String> resourceNames = buildFileHierarchy(rootDir, fileTree);
        ExplodedImageHelper image = new ExplodedImageHelper(rootDir);

        // All names here start with "/modules/...".
        for (String name : resourceNames) {
            assertTrue(name.startsWith("/modules/"), "Unexpected resource name: " + name);
            Node node = image.findNode(name);
            assertNotNull(node, "Expected node for resource name: " + name);
            String content = new String(node.getResource(), UTF_8);

            Path expectedPath = toRelativePath(name.substring("/modules/".length()), separator);
            assertEquals("Path: " + expectedPath, content);
        }
    }

    @Test
    public void testSharedPackages(@TempDir Path rootDir) throws IOException {
        String fileTree = """
                          one/foo/bar/Bar1.class
                          two/foo/bar/Bar2.class
                          two/foo/baz/Baz1.class
                          three/foo/baz/Baz2.class
                          """;
        buildFileHierarchy(rootDir, fileTree);
        ExplodedImageHelper image = new ExplodedImageHelper(rootDir);

        Node fooBar = image.findNode("/packages/foo.bar");
        assertNotNull(fooBar, "Expected node for name: " + fooBar);
        Node fooBaz = image.findNode("/packages/foo.baz");
        assertNotNull(fooBaz, "Expected node for name: " + fooBaz);

        List<String> fooBarMods = fooBar.getChildNames();
        assertEquals(2, fooBarMods.size());
        assertTrue(fooBarMods.contains("/packages/foo.bar/one"));
        assertTrue(fooBarMods.contains("/packages/foo.bar/two"));

        List<String> fooBazMods = fooBaz.getChildNames();
        assertEquals(2, fooBazMods.size());
        assertTrue(fooBazMods.contains("/packages/foo.baz/two"));
        assertTrue(fooBazMods.contains("/packages/foo.baz/three"));
    }

    // Assumes newline or command separated list of files. Directories are
    // created implicitly, files contain "Path: <relative path>".
    static List<String> buildFileHierarchy(Path rootDir, String fileTree) {
        String separator = rootDir.getFileSystem().getSeparator();
        List<Path> relativeModulePaths = Arrays.stream(fileTree.split("[\\s\n]+"))
                .peek(ExplodedImageTest::assertPath)
                .map(s -> toRelativePath(s, separator))
                .toList();
        relativeModulePaths.forEach(p -> createDummyFile(p, rootDir));
        return relativeModulePaths.stream()
                .map(ExplodedImageTest::toName)
                .toList();
    }

    static void createDummyFile(Path p, Path root) {
        try {
            Path abs = root.resolve(p);
            Files.createDirectories(abs.getParent());
            Files.writeString(abs, "Path: " + p, UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static void assertPath(String path) {
        assertFalse(path.isEmpty(), "Resource paths cannot be empty.");
        assertFalse(path.startsWith("/"), "Resource paths must be relative: " + path);
        assertTrue(Arrays.stream(path.split("/"))
                        .allMatch(s -> !s.isEmpty() && !s.contains("\\")),
                "Resource name segments must not be empty or contain '\\'");
    }

    static Path toRelativePath(String name, String separator) {
        return Path.of(name.replace("/", separator));
    }

    static String toName(Path path) {
        StringBuilder name = new StringBuilder("/modules");
        for (Path seg : path) {
            name.append("/").append(seg);
        }
        return name.toString();
    }
}
