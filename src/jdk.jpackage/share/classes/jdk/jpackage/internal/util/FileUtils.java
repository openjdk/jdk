/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package jdk.jpackage.internal.util;

import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NotLinkException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.function.ThrowingConsumer;

public final class FileUtils {

    public static void deleteRecursive(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        var callback = new RecursiveDeleter();

        Files.walkFileTree(directory, callback);

        if (callback.ex != null) {
            throw callback.ex;
        }
    }

    public static void copyRecursive(Path src, Path dest, CopyOption... options)
            throws IOException {

        List<CopyAction> copyActions = new ArrayList<>();

        if (Files.isDirectory(src)) {
            Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(final Path dir,
                        final BasicFileAttributes attrs) {
                    copyActions.add(new CopyAction(null, dest.resolve(src.relativize(dir))));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(final Path file,
                        final BasicFileAttributes attrs) {
                    copyActions.add(new CopyAction(file, dest.resolve(src.relativize(file))));
                    return FileVisitResult.CONTINUE;
                }
            });
        } else {
            Optional.ofNullable(dest.getParent()).ifPresent(dstDir -> {
                copyActions.add(new CopyAction(null, dstDir));
            });
            copyActions.add(new CopyAction(src, dest));
        }

        for (var copyAction : copyActions) {
            copyAction.apply(options);
        }
    }

    public static Path readSymlinkTargetRecursive(Path symlink) throws IOException, NotLinkException {
        try {
            var target = Files.readSymbolicLink(symlink);
            if (Files.isSymbolicLink(target)) {
                return readSymlinkTargetRecursive(target);
            } else {
                return target;
            }
        } catch (NotLinkException ex) {
            throw ex;
        }
    }

    private static record CopyAction(Path src, Path dest) {

        void apply(CopyOption... options) throws IOException {
            if (List.of(options).contains(StandardCopyOption.REPLACE_EXISTING)) {
                // They requested copying with replacing the existing content.
                if (src == null && Files.isRegularFile(dest)) {
                    // This copy action creates a directory, but a file at the same path already exists, so delete it.
                    Files.deleteIfExists(dest);
                } else if (src != null && Files.isDirectory(dest)) {
                    // This copy action copies a file, but a directory at the same path exists already, so delete it.
                    deleteRecursive(dest);
                }
            }

            if (src == null) {
                Files.createDirectories(dest);
            } else {
                Files.copy(src, dest, options);
            }
        }
    }

    private static class RecursiveDeleter extends SimpleFileVisitor<Path> {

        @Override
        public FileVisitResult visitFile(Path file,
                BasicFileAttributes attr) throws IOException {
            adjustAttributes(file);
            runActionOnPath(Files::delete, file);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir,
                BasicFileAttributes attr) throws IOException {
            adjustAttributes(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e)
                throws IOException {
            runActionOnPath(Files::delete, dir);
            return FileVisitResult.CONTINUE;
        }

        private static void adjustAttributes(Path path) throws IOException {
            if (OperatingSystem.isWindows()) {
                Files.setAttribute(path, "dos:readonly", false);
            }
        }

        private void runActionOnPath(ThrowingConsumer<Path, IOException> action, Path path) {
            try {
                action.accept(path);
            } catch (IOException ex) {
                if (this.ex == null) {
                    this.ex = ex;
                }
            }
        }

        private IOException ex;
    }
}
