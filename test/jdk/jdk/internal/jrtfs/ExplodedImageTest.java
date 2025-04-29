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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
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
    public void testCommonModuleExpectations(String fileTree, @TempDir Path rootDir) throws IOException {
        String separator = rootDir.getFileSystem().getSeparator();
        Set<String> resourceNames = buildFileHierarchy(rootDir, fileTree);
        ExplodedImageHelper image = new ExplodedImageHelper(rootDir);

        // All names here start with "/modules/...".
        for (String name : resourceNames) {
            assertTrue(name.startsWith("/modules/"), "Unexpected resource name: " + name);
            Node node = image.findNode(name);
            assertNotNull(node, "Expected node for resource name: " + name);
            String content = new String(node.getResource(), UTF_8);

            // Tests that each resource (underlying file) has the expected contents.
            Path expectedPath = toRelativePath(name.substring("/modules/".length()), separator);
            assertEquals("Path: " + expectedPath, content);
        }

        assertEquals(0,
                walk(image, "/modules").filter(Node::isLink).count(),
                "Modules directory should not contain link nodes.");
        assertEquals(resourceNames,
                walk(image, "/modules")
                        .filter(n -> !n.isDirectory())
                        .map(Node::getName)
                        .collect(toSet()),
                "Mismatched module nodes.");
        // All non-directory nodes should throw IAE if asked for children.
        walk(image, "/modules")
                .filter(n -> !n.isDirectory())
                .forEach(n -> assertThrows(IllegalArgumentException.class, n::getChildNames));
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

        assertTrue(walk(image, "/packages").allMatch(n -> n.isLink() || n.isDirectory()));
        Set<String> packages = assertNode(image, "/packages").getChildNames();
        assertEquals(
                Set.of("/packages/foo", "/packages/foo.bar", "/packages/foo.baz"),
                packages,
                "Unexpected package names: " + packages);

        Set<String> fooBarMods = assertNode(image, "/packages/foo.bar").getChildNames();
        assertEquals(
                Set.of("/packages/foo.bar/one", "/packages/foo.bar/two"),
                fooBarMods,
                "Unexpected module names links: " + fooBarMods);

        Set<String> fooBazMods = assertNode(image, "/packages/foo.baz").getChildNames();
        assertEquals(
                Set.of("/packages/foo.baz/two", "/packages/foo.baz/three"),
                fooBazMods,
                "Unexpected module names links: " + fooBazMods);
    }

    static Node assertNode(ExplodedImageHelper image, String path) {
        Node node = image.findNode(path);
        assertNotNull(node, "Expected non-null node for path: " + path);
        return node;
    }

    static Stream<Node> walk(ExplodedImageHelper image, String start) {
        List<Node> nodes = new ArrayList<>();
        depthFirstWalk(start, image, nodes::add);
        return nodes.stream();
    }

    static void depthFirstWalk(String path, ExplodedImageHelper image, Consumer<Node> action) {
        Node node = Objects.requireNonNull(image.findNode(path), "No node at: " + path);
        action.accept(node);
        if (node.isDirectory()) {
            node.getChildNames().forEach(p -> depthFirstWalk(p, image, action));
        }
    }

    // Assumes newline or command separated list of files. Directories are
    // created implicitly, files contain "Path: <relative path>".
    static Set<String> buildFileHierarchy(Path rootDir, String fileTree) {
        String separator = rootDir.getFileSystem().getSeparator();
        List<Path> relativeModulePaths = Arrays.stream(fileTree.split("[\\s\n]+"))
                .peek(ExplodedImageTest::assertPath)
                .map(s -> toRelativePath(s, separator))
                .toList();
        relativeModulePaths.forEach(p -> createDummyFile(p, rootDir));
        return relativeModulePaths.stream()
                .map(ExplodedImageTest::toName)
                .collect(toSet());
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
