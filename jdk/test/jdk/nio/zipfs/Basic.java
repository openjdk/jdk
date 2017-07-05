/*
 * Copyright (c) 2009, 2011, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.file.*;
import java.nio.file.attribute.*;
import java.nio.file.spi.FileSystemProvider;
import java.util.*;
import java.net.URI;
import java.io.IOException;

/**
 *
 * @test
 * @bug 8038500
 * @summary Basic test for zip provider
 */

public class Basic {
    public static void main(String[] args) throws Exception {
        Path zipfile = Paths.get(System.getProperty("test.jdk"),
                                 "jre/lib/ext/zipfs.jar");
        // Test: zip should should be returned in provider list
        boolean found = false;

        for (FileSystemProvider provider: FileSystemProvider.installedProviders()) {
            if (provider.getScheme().equalsIgnoreCase("jar")) {
                found = true;
                break;
            }
        }
        if (!found)
            throw new RuntimeException("'jar' provider not installed");

        // Test: FileSystems#newFileSystem(Path)
        Map<String,?> env = new HashMap<String,Object>();
        FileSystems.newFileSystem(zipfile, null).close();

        // Test: FileSystems#newFileSystem(URI)
        URI uri = new URI("jar", zipfile.toUri().toString(), null);
        FileSystem fs = FileSystems.newFileSystem(uri, env, null);

        // Test: exercise toUri method
        String expected = uri.toString() + "!/foo";
        String actual = fs.getPath("/foo").toUri().toString();
        if (!actual.equals(expected)) {
            throw new RuntimeException("toUri returned '" + actual +
                "', expected '" + expected + "'");
        }

        // Test: exercise directory iterator and retrieval of basic attributes
        Files.walkFileTree(fs.getPath("/"), new FileTreePrinter());

        // Test: DirectoryStream
        found = false;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(fs.getPath("/"))) {
            for (Path entry: stream) {
                found = entry.toString().equals("/META-INF/");
                if (found) break;
            }
        }

        if (!found)
            throw new RuntimeException("Expected file not found");

        // Test: copy file from zip file to current (scratch) directory
        Path source = fs.getPath("/META-INF/services/java.nio.file.spi.FileSystemProvider");
        if (Files.exists(source)) {
            Path target = Paths.get(source.getFileName().toString());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            try {
                long s1 = Files.readAttributes(source, BasicFileAttributes.class).size();
                long s2 = Files.readAttributes(target, BasicFileAttributes.class).size();
                if (s2 != s1)
                    throw new RuntimeException("target size != source size");
            } finally {
                Files.delete(target);
            }
        }

        // Test: FileStore
        FileStore store = Files.getFileStore(fs.getPath("/"));
        if (!store.supportsFileAttributeView("basic"))
            throw new RuntimeException("BasicFileAttributeView should be supported");

        // Test: ClosedFileSystemException
        fs.close();
        if (fs.isOpen())
            throw new RuntimeException("FileSystem should be closed");
        try {
            fs.provider().checkAccess(fs.getPath("/missing"), AccessMode.READ);
        } catch (ClosedFileSystemException x) { }
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
                System.out.format(" (%d)", attrs.size());
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
