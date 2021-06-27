/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import jdk.test.lib.util.JarBuilder;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @test
 * @bug 8251329
 * @summary Verify that Files.walkFileTree, on a ZipFileSystem representing a jar file, doesn't end up in an infinite loop
 * when the source jar has an entry named "."
 * @library /test/lib/
 * @run testng ZipFSFileWalkTest
 */
public class ZipFSFileWalkTest {

    private static final byte[] FILE_CONTENT = "Hello world!!!".getBytes(StandardCharsets.UTF_8);

    /**
     * Uses {@link Files#walkFileTree(Path, FileVisitor)} to walk a zipfs created on a jar which
     * has some entries named "." and "..". Expects that the walk completes successfully and finds the
     * expected content.
     */
    @Test
    public void testSingleAndDoubleDotEntries() throws Exception {
        final Path jar = Path.of("8251329-test.jar");
        final JarBuilder builder = new JarBuilder(jar.toString());
        // add some entries
        builder.addEntry("d1/", new byte[0]);
        builder.addEntry("d1/file.txt", FILE_CONTENT);
        // add the "." dir
        builder.addEntry("./", new byte[0]);
        // add the ".." dir
        builder.addEntry("../", new byte[0]);
        // add some other entries
        builder.addEntry("file.txt", FILE_CONTENT);
        builder.addEntry("d2/", new byte[0]);
        builder.addEntry("d3./", new byte[0]);
        builder.addEntry("d3./..file.txt", FILE_CONTENT);
        // add a "." dir under some other dir
        builder.addEntry("d2/./", new byte[0]);
        builder.addEntry("d2/./file.txt", FILE_CONTENT);
        // add a ".." dir under some other dir
        builder.addEntry("d1/../", new byte[0]);
        builder.addEntry("d1/../file.txt", FILE_CONTENT);
        builder.build();
        try (final FileSystem fs = FileSystems.newFileSystem(jar)) {
            // walk using the root path and a relative path
            final List<Path> walkStartPaths = List.of(
                    fs.getRootDirectories().iterator().next(),
                    fs.getPath("./"));
            for (final Path walkStartPath : walkStartPaths) {
                // paths are relative to the start path of our tree walk
                final Set<String> expectedDirs = new HashSet<>(Set.of(
                        walkStartPath.toString(),
                        walkStartPath.resolve("META-INF").toString(),
                        walkStartPath.resolve("d1").toString(),
                        walkStartPath.resolve("d2").toString(),
                        walkStartPath.resolve("d3.").toString()));
                final Set<String> expectedFiles = new HashSet<>(Set.of(
                        walkStartPath.resolve("META-INF/MANIFEST.MF").toString(),
                        walkStartPath.resolve("d1/file.txt").toString(),
                        walkStartPath.resolve("file.txt").toString(),
                        walkStartPath.resolve("d3./..file.txt").toString()));
                final SimpleJarFileVisitor visitor = new SimpleJarFileVisitor(expectedDirs, expectedFiles);
                System.out.println("Walking file tree starting at " + walkStartPath + " of jar " + jar);
                Files.walkFileTree(walkStartPath, visitor);
                // make sure all expected content was found
                visitor.assertVisitedAllExpected();
            }
        }
    }

    private static final class SimpleJarFileVisitor extends SimpleFileVisitor<Path> {
        private final Set<String> expectedDirs;
        private final Set<String> expectedFiles;

        private SimpleJarFileVisitor(final Set<String> expectedDirs, final Set<String> expectedFiles) {
            this.expectedDirs = new HashSet<>(expectedDirs);
            this.expectedFiles = new HashSet<>(expectedFiles);
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) throws IOException {
            if (!expectedDirs.remove(dir.toString())) {
                throw new IOException("Unexpected directory " + dir + " " + expectedDirs);
            }
            System.out.println("Visited directory " + dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes at) throws IOException {
            if (!expectedFiles.remove(file.toString())) {
                throw new IOException("Unexpected file " + file);
            }
            System.out.println("Visited file " + file);
            // the test isn't interested in the manifest file content
            if (file.getFileName().toString().equals("MANIFEST.MF")) {
                return FileVisitResult.CONTINUE;
            }
            if (!Arrays.equals(FILE_CONTENT, Files.readAllBytes(file))) {
                throw new RuntimeException("Unexpected content in file " + file);
            }
            return FileVisitResult.CONTINUE;
        }

        private void assertVisitedAllExpected() {
            if (!this.expectedDirs.isEmpty()) {
                throw new RuntimeException("Missing directories " + this.expectedDirs);
            }
            if (!this.expectedFiles.isEmpty()) {
                throw new RuntimeException("Missing files " + this.expectedFiles);
            }
        }
    }
}
