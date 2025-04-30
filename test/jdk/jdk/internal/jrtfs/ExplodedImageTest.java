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

import jdk.internal.jrtfs.ImageHelper;
import jdk.internal.jrtfs.ImageHelper.Node;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.*;

/*
 * @test
 * @bug 8352750
 * @summary Tests ExplodedImage creation from local files/directories.
 * @compile/module=java.base jdk/internal/jrtfs/ImageHelper.java
 * @modules java.base/jdk.internal.jrtfs
 * @run junit/othervm ExplodedImageTest
 */
public class ExplodedImageTest {

    @TempDir
    private Path modulesDir;
    private String fsSeparator;

    @BeforeEach
    public void setSeparator() {
        this.fsSeparator = modulesDir.getFileSystem().getSeparator();
    }

    @ParameterizedTest
    @ValueSource(strings =
            {       // Basic paths.
                    """
                    abc/foo/bar/gus.txt
                    java.base/java/lang/Integer.class
                    """,
                    // Packages appearing in several modules.
                    """
                    one/foo/bar/Bar1.class
                    two/foo/bar/Bar2.class
                    two/foo/gus/Gus1.class
                    three/foo/gus/Gus2.class
                    """})
    public void testCommonModuleExpectations(String fileTree) throws IOException {
        Set<String> resourceNames = buildFileHierarchy(modulesDir, fileTree);
        ImageHelper image = ImageHelper.createExplodedImage(modulesDir);

        // All names here start with "/modules/...".
        for (String name : resourceNames) {
            assertTrue(name.startsWith("/modules/"), "Unexpected resource name: " + name);
            Node node = image.findNode(name);
            assertNotNull(node, "Expected node for resource name: " + name);
            String content = new String(node.getResource(), UTF_8);

            // Tests that each resource (underlying file) has the expected contents.
            Path expectedPath = toRelativePath(name.substring("/modules/".length()));
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

        // All links in "/packages/<pkg.name>/<modname>" link to "/modules/<modname>"
        walk(image, "/packages")
                .filter(Node::isLink)
                .forEach(n -> assertEquals(assertNode(image, "/modules/" + n.getLocalName()), n.resolveLink(true)));
    }

    @Test
    public void testSharedPackages() throws IOException {
        // Three modules "one", "two", "three"
        // Three packages, "foo", "foo.bar" and "foo.gus"
        // Some packages exist in multiple modules.
        String fileTree = """
                          one/foo/bar/Bar1.class
                          two/foo/bar/Bar2.class
                          two/foo/gus/Gus1.class
                          three/foo/gus/Gus2.class
                          """;
        buildFileHierarchy(modulesDir, fileTree);
        ImageHelper image = ImageHelper.createExplodedImage(modulesDir);

        assertTrue(walk(image, "/packages").allMatch(n -> n.isLink() || n.isDirectory()));
        Set<String> packages = assertNode(image, "/packages").getChildNames();
        assertEquals(
                Set.of("/packages/foo", "/packages/foo.bar", "/packages/foo.gus"),
                packages,
                "Unexpected package names: " + packages);

        Set<String> fooModules = assertNode(image, "/packages/foo").getLocalChildNames();
        assertEquals(Set.of("one", "two", "three"), fooModules);

        Set<String> barModules = assertNode(image, "/packages/foo.bar").getLocalChildNames();
        assertEquals(Set.of("one", "two"), barModules);

        Set<String> gusModules = assertNode(image, "/packages/foo.gus").getLocalChildNames();
        assertEquals(Set.of("two", "three"), gusModules);

        Node modOne = assertNode(image, "/modules/one");
        // Even though the package name is in the path, the node links to the module root.
        Node oneFoo = assertNode(image, "/packages/foo/one").resolveLink(true);
        Node oneBar = assertNode(image, "/packages/foo.bar/one").resolveLink(true);
        assertEquals(modOne, oneFoo);
        assertEquals(modOne, oneBar);
    }

    static Node assertNode(ImageHelper image, String path) {
        Node node = image.findNode(path);
        assertNotNull(node, "Expected non-null node for path: " + path);
        return node;
    }

    static Stream<Node> walk(ImageHelper image, String start) {
        List<Node> nodes = new ArrayList<>();
        depthFirstWalk(start, image, nodes::add);
        return nodes.stream();
    }

    static void depthFirstWalk(String path, ImageHelper image, Consumer<Node> action) {
        Node node = Objects.requireNonNull(image.findNode(path), "No node at: " + path);
        action.accept(node);
        if (node.isDirectory()) {
            node.getChildNames().forEach(p -> depthFirstWalk(p, image, action));
        }
    }

    // Assumes newline or command separated list of files. Directories are
    // created implicitly, files contain "Path: <relative path>".
    Set<String> buildFileHierarchy(Path rootDir, String fileTree) {
        List<Path> relativeModulePaths = Arrays.stream(fileTree.split("[\\s\n]+"))
                .peek(ExplodedImageTest::assertPath)
                .map(this::toRelativePath)
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

    Path toRelativePath(String name) {
        return Path.of(name.replace("/", fsSeparator));
    }

    static String toName(Path path) {
        StringBuilder name = new StringBuilder("/modules");
        for (Path seg : path) {
            name.append("/").append(seg);
        }
        return name.toString();
    }
}
