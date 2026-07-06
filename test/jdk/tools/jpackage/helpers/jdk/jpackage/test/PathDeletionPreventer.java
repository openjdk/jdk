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

package jdk.jpackage.test;

import java.io.Closeable;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import jdk.internal.util.OperatingSystem;
import jdk.jpackage.internal.util.SetBuilder;

/**
 * Path deletion preventer. Encapsulates platform-specifics of how to make a
 * file or a directory non-deletable.
 * <p>
 * Implementation should be sufficient to make {@code Files#delete(Path)}
 * applied to the path protected from deletion throw.
 */
public sealed interface PathDeletionPreventer {

    enum Implementation {
        /**
         * Uses Java file lock to prevent deletion of a file.
         * Works on Windows. Doesn't work on Linux and macOS.
         */
        FILE_CHANNEL_LOCK,

        /**
         * Removes write permission from a non-empty directory to prevent its deletion.
         * Works on Linux and macOS. Doesn't work on Windows.
         */
        READ_ONLY_NON_EMPTY_DIRECTORY,
        ;
    }

    Implementation implementation();

    Closeable preventPathDeletion(Path path) throws IOException;

    enum FileChannelLockPathDeletionPreventer implements PathDeletionPreventer {
        INSTANCE;

        @Override
        public Implementation implementation() {
            return Implementation.FILE_CHANNEL_LOCK;
        }

        @Override
        public Closeable preventPathDeletion(Path path) throws IOException {
            return new UndeletablePath(path);
        }

        private static final class UndeletablePath implements Closeable {

            UndeletablePath(Path file) throws IOException {
                var fos = new FileOutputStream(Objects.requireNonNull(file).toFile());
                boolean lockCreated = false;
                try {
                    this.lock = fos.getChannel().lock();
                    this.fos = fos;
                    lockCreated = true;
                } finally {
                    if (!lockCreated) {
                        fos.close();
                    }
                }
            }

            @Override
            public void close() throws IOException {
                try {
                    lock.close();
                } finally {
                    fos.close();
                }
            }

            private final FileOutputStream fos;
            private final FileLock lock;
        }
    }

    enum ReadOnlyDirectoryPathDeletionPreventer implements PathDeletionPreventer {
        INSTANCE;

        @Override
        public Implementation implementation() {
            return Implementation.READ_ONLY_NON_EMPTY_DIRECTORY;
        }

        @Override
        public Closeable preventPathDeletion(Path path) throws IOException {
            return new UndeletablePath(path);
        }

        private static final class UndeletablePath implements Closeable {

            UndeletablePath(Path dir) throws IOException {
                this.dir = Objects.requireNonNull(dir);

                // Deliberately don't use Files#createDirectories() as don't want to create missing directories.
                try {
                    Files.createDirectory(dir);
                    Files.createFile(dir.resolve("empty"));
                } catch (FileAlreadyExistsException ex) {
                }

                perms = Files.getPosixFilePermissions(dir);
                if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
                    Files.setPosixFilePermissions(dir, SetBuilder.<PosixFilePermission>build()
                            .add(perms)
                            .remove(PosixFilePermission.OWNER_WRITE)
                            .emptyAllowed(true)
                            .create());
                }
            }

            @Override
            public void close() throws IOException {
                if (perms.contains(PosixFilePermission.OWNER_WRITE)) {
                    Files.setPosixFilePermissions(dir, perms);
                }
            }

            private final Path dir;
            private final Set<PosixFilePermission> perms;
        }
    }

    static final PathDeletionPreventer DEFAULT = new Supplier<PathDeletionPreventer>() {

        @Override
        public PathDeletionPreventer get() {
            switch (OperatingSystem.current()) {
                case WINDOWS -> {
                    return FileChannelLockPathDeletionPreventer.INSTANCE;
                }
                default -> {
                    return ReadOnlyDirectoryPathDeletionPreventer.INSTANCE;
                }
            }
        }

    }.get();
}
