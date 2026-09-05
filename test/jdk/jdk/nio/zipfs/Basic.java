/*
 * Copyright (c) 2009, 2026, Oracle and/or its affiliates. All rights reserved.
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

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.nio.file.AccessMode;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderMismatchException;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.spi.FileSystemProvider;
import java.net.URI;
import java.io.IOException;
import java.util.Map;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/*
 * @test
 * @bug 8038500 8040059 8150366 8150496 8147539 8290047
 * @summary Basic test for zip provider
 * @modules jdk.zipfs
 * @run junit Basic
 */
public class Basic {

    static Path jarFile;
    static URI uri;

    @BeforeAll
    static void setup() throws IOException, URISyntaxException {
        jarFile = Utils.createJarFile("basic.jar",
                "META-INF/services/java.nio.file.spi.FileSystemProvider");
        uri = new URI("jar", jarFile.toUri().toString(), null);
    }

    @AfterAll
    static void cleanUp() throws IOException {
        Files.deleteIfExists(jarFile);
    }

    @Test
    void providerListTest() {
        // Test: zip should be returned in provider list
        assertTrue(FileSystemProvider.installedProviders().stream()
                .anyMatch(p -> p.getScheme().equalsIgnoreCase("jar")),
                "'jar' provider not installed");
    }

    @Test
    void newFileSystemTest() throws IOException {
        // To test `newFileSystem`, close the shared FileSystem
        var fs = FileSystems.newFileSystem(uri, Map.of());
        fs.close();
        // Test: FileSystems#newFileSystem(Path)
        FileSystems.newFileSystem(jarFile).close();
        // Test: FileSystems#newFileSystem(URI)
        FileSystems.newFileSystem(uri, Map.of()).close();
    }

    @Test
    void toUriTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: exercise toUri method
            String expected = uri.toString() + "!/foo";
            String actual = fs.getPath("/foo").toUri().toString();
            assertEquals(expected, actual, "toUri returned '" + actual +
                    "', expected '" + expected + "'");
        }
    }

    @Test
    void directoryIteratorTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: exercise directory iterator and retrieval of basic attributes
            Files.walkFileTree(fs.getPath("/"), new FileTreePrinter());
        }
    }

    @Test
    void copyFileTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: copy file from zip file to current (scratch) directory
            Path source = fs.getPath("/META-INF/services/java.nio.file.spi.FileSystemProvider");
            if (Files.exists(source)) {
                Path target = Path.of(source.getFileName().toString());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                try {
                    long s1 = Files.readAttributes(source, BasicFileAttributes.class).size();
                    long s2 = Files.readAttributes(target, BasicFileAttributes.class).size();
                    assertEquals(s1, s2, "target size != source size");
                } finally {
                    Files.delete(target);
                }
            }
        }
    }

    @Test
    void fileStoreTest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: FileStore
            FileStore store = Files.getFileStore(fs.getPath("/"));
            assertTrue(store.supportsFileAttributeView("basic"),
                    "BasicFileAttributeView should be supported");
        }
    }

    @Test
    void watchRegisterNPETest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: watch register should throw PME
            assertThrows(ProviderMismatchException.class, () -> fs.getPath("/")
                            .register(FileSystems.getDefault().newWatchService(), ENTRY_CREATE),
                    "watch service is not supported");
        }
    }

    @Test
    void pathMatcherIAETest() throws IOException {
        try (var fs = FileSystems.newFileSystem(uri, Map.of())) {
            // Test: IllegalArgumentException
            assertThrows(IllegalArgumentException.class, () -> fs.getPathMatcher(":glob"),
                    "IllegalArgumentException not thrown");
            assertDoesNotThrow(() -> fs.getPathMatcher("glob:"),
                    "Unexpected IllegalArgumentException");
        }
    }

    @Test
    void closedFileSystemTest() throws IOException {
        // Test: ClosedFileSystemException
        var fs = FileSystems.newFileSystem(uri, Map.of());
        fs.close();
        assertFalse(fs.isOpen(), "FileSystem should be closed");
        assertThrows(ClosedFileSystemException.class,
                () -> fs.provider().checkAccess(fs.getPath("/missing"), AccessMode.READ));
    }

    // FileVisitor that pretty prints a file tree
    static class FileTreePrinter extends SimpleFileVisitor<Path> {
        private int indent = 0;

        private void indent() {
            StringBuilder sb = new StringBuilder(indent);
            for (int i=0; i<indent; i++) sb.append(" ");
            System.out.print(sb);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                                                 BasicFileAttributes attrs)
        {
            if (dir.getFileName() != null) {
                indent();
                System.out.println(dir.getFileName() + "/");
                indent++;
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file,
                                         BasicFileAttributes attrs)
        {
            indent();
            System.out.print(file.getFileName());
            if (attrs.isRegularFile())
                System.out.format("%n%s%n", attrs);

            System.out.println();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc)
            throws IOException
        {
            if (exc != null)
                super.postVisitDirectory(dir, exc);
            if (dir.getFileName() != null)
                indent--;
            return FileVisitResult.CONTINUE;
        }
    }
}
