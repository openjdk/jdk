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

package jdk.internal.jrtfs;

import jdk.internal.jimage.ImageReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests an {@link ExplodedImage} view of a class-file hierarchy.
 *
 * <p>For simplicity and performance, only a subset of the JRT files are copied
 * to disk for testing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ExplodedImageTest {

    private Path modulesRoot;
    private SystemImage explodedImage;
    private String pathSeparator;

    @BeforeAll
    public void createTestDirectory(@TempDir Path modulesRoot) throws IOException {
        this.modulesRoot = modulesRoot;
        this.pathSeparator = modulesRoot.getFileSystem().getSeparator();
        // Copy only a useful subset of files for testing. Use at least two
        // modules with "overlapping" packages to test /package links better.
        unpackModulesDirectoriesFromJrtFileSystem(modulesRoot,
                "java.base/java/util",
                "java.base/java/util/zip",
                "java.logging/java/util/logging");
        this.explodedImage = new ExplodedImage(modulesRoot);
    }

    /** Unpacks a list of "/modules/..." directories non-recursively into the specified root directory. */
    private static void unpackModulesDirectoriesFromJrtFileSystem(Path modulesRoot, String... dirNames)
            throws IOException {
        FileSystem jrtfs = FileSystems.getFileSystem(URI.create("jrt:/"));
        List<Path> srcDirs = Arrays.stream(dirNames).map(s -> "/modules/" + s).map(jrtfs::getPath).toList();
        for (Path srcDir : srcDirs) {
            // Skip-1 to remove "modules" segment (not part of the file system path).
            Path dstDir = StreamSupport.stream(srcDir.spliterator(), false)
                    .skip(1)
                    .reduce(modulesRoot, (path, segment) -> path.resolve(segment.toString()));
            Files.createDirectories(dstDir);
            try (DirectoryStream<Path> files = Files.newDirectoryStream(srcDir)) {
                for (Path srcFile : files) {
                    Files.copy(srcFile, dstDir.resolve(srcFile.getFileName().toString()));
                }
            }
        }
    }

    @Test
    public void topLevelNodes() throws IOException {
        ImageReader.Node root = explodedImage.findNode("/");
        ImageReader.Node modules = explodedImage.findNode("/modules");
        ImageReader.Node packages = explodedImage.findNode("/packages");
        assertEquals(
                Set.of(modules.getName(), packages.getName()),
                root.getChildNames().collect(Collectors.toSet()));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/modules/java.base/java/util/List.class",
            "/modules/java.base/java/util/zip/ZipEntry.class",
            "/modules/java.logging/java/util/logging/Logger.class"})
    public void basicLookupResource(String expectedResourceName) throws IOException {
        ImageReader.Node node = assertResourceNode(expectedResourceName);

        Path fsRelPath = getRelativePath(expectedResourceName);
        assertArrayEquals(
                Files.readAllBytes(modulesRoot.resolve(fsRelPath)),
                explodedImage.getResource(node));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/modules/java.base",
            "/modules/java.logging",
            "/modules/java.base/java",
            "/modules/java.base/java/util",
            "/modules/java.logging/java/util",
    })
    public void basicLookupDirectory(String expectedDirectoryName) throws IOException {
        ImageReader.Node node = assertDirectoryNode(expectedDirectoryName);

        Path fsRelPath = getRelativePath(expectedDirectoryName);
        List<String> fsChildBaseNames;
        try (DirectoryStream<Path> paths = Files.newDirectoryStream(modulesRoot.resolve(fsRelPath))) {
            fsChildBaseNames = StreamSupport.stream(paths.spliterator(), false)
                    .map(Path::getFileName)
                    .map(Path::toString)
                    .toList();
        }
        List<String> nodeChildBaseNames = node.getChildNames()
                .map(s -> s.substring(node.getName().length() + 1))
                .toList();
        assertEquals(fsChildBaseNames, nodeChildBaseNames, "expected same child names");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/packages/java/java.base",
            "/packages/java/java.logging",
            "/packages/java.util/java.base",
            "/packages/java.util/java.logging",
            "/packages/java.util.zip/java.base"})
    public void basicLookupPackageLinks(String expectedLinkName) throws IOException {
        ImageReader.Node node = assertLinkNode(expectedLinkName);
        ImageReader.Node resolved = node.resolveLink();
        assertSame(explodedImage.findNode(resolved.getName()), resolved);
        String moduleName = expectedLinkName.substring(expectedLinkName.lastIndexOf('/') + 1);
        assertEquals("/modules/" + moduleName, resolved.getName());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/packages/java",
            "/packages/java.util",
            "/packages/java.util.zip"})
    public void packageDirectories(String expectedDirectoryName) throws IOException {
        ImageReader.Node node = assertDirectoryNode(expectedDirectoryName);
        assertTrue(node.getChildNames().findFirst().isPresent(),
                "Package directories should not be empty: " + node);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "",
            ".",
            "/.",
            "modules",
            "packages",
            "/modules/",
            "/modules/xxxx",
            "/modules/java.base/java/lang/Xxxx.class",
            "/packages/",
            "/packages/xxxx",
            "/packages/java.xxxx",
            "/packages/java.util.",
            // Mismatched module.
            "/packages/java.util.logging/java.base",
            "/packages/java.util.zip/java.logging",
            // Links are not resolved as they are fetched (old/broken behaviour).
            "/packages/java.util/java.base/java/util/Vector.class",
    })
    public void invalidNames(String invalidName) throws IOException {
        assertNull(explodedImage.findNode(invalidName), "No node expected for: " + invalidName);
    }

    private ImageReader.Node assertResourceNode(String name) throws IOException {
        ImageReader.Node node = explodedImage.findNode(name);
        assertNotNull(node);
        assertEquals(name, node.getName(), "expected node name: " + name);
        assertTrue(node.isResource(), "expected a resource: " + node);
        assertFalse(node.isDirectory(), "resources are not directories: " + node);
        assertFalse(node.isLink(), "resources are not links: " + node);
        return node;
    }

    private ImageReader.Node assertDirectoryNode(String name) throws IOException {
        ImageReader.Node node = explodedImage.findNode(name);
        assertNotNull(node);
        assertEquals(name, node.getName(), "expected node name: " + name);
        assertTrue(node.isDirectory(), "expected a directory: " + node);
        assertFalse(node.isResource(), "directories are not resources: " + node);
        assertFalse(node.isLink(), "directories are not links: " + node);
        return node;
    }

    private ImageReader.Node assertLinkNode(String name) throws IOException {
        ImageReader.Node node = explodedImage.findNode(name);
        assertNotNull(node);
        assertEquals(name, node.getName(), "expected node name: " + name);
        assertTrue(node.isLink(), "expected a link: " + node);
        assertFalse(node.isResource(), "links are not resources: " + node);
        assertFalse(node.isDirectory(), "links are not directories: " + node);
        return node;
    }

    private Path getRelativePath(String name) {
        return Path.of(name.substring("/modules/".length()).replace("/", pathSeparator));
    }
}
