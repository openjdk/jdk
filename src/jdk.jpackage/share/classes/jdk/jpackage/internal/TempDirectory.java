/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import jdk.jpackage.internal.cli.Options;
import jdk.jpackage.internal.cli.StandardOption;
import jdk.jpackage.internal.util.FileUtils;
import jdk.jpackage.internal.util.Slot;

final class TempDirectory implements Closeable {

    TempDirectory(Options options, RetryExecutorFactory retryExecutorFactory) throws IOException {
        this(StandardOption.TEMP_ROOT.findIn(options), retryExecutorFactory);
    }

    TempDirectory(Optional<Path> tempDir, RetryExecutorFactory retryExecutorFactory) throws IOException {
        this(tempDir.isEmpty() ? Files.createTempDirectory("jdk.jpackage") : tempDir.get(),
                tempDir.isEmpty(),
                retryExecutorFactory);
    }

    TempDirectory(Path tempDir, boolean deleteOnClose, RetryExecutorFactory retryExecutorFactory) throws IOException {
        this.path = Objects.requireNonNull(tempDir);
        this.deleteOnClose = deleteOnClose;
        this.retryExecutorFactory = Objects.requireNonNull(retryExecutorFactory);
    }

    Options map(Options options) {
        if (deleteOnClose) {
            return options.copyWithDefaultValue(StandardOption.TEMP_ROOT, path);
        } else {
            return options;
        }
    }

    Path path() {
        return path;
    }

    boolean deleteOnClose() {
        return deleteOnClose;
    }

    @Override
    public void close() throws IOException {
        if (deleteOnClose) {
            retryExecutorFactory.<Void, IOException>retryExecutor(IOException.class)
                    .setMaxAttemptsCount(5)
                    .setAttemptTimeout(2, TimeUnit.SECONDS)
                    .setExecutable(context -> {
                        try {
                            FileUtils.deleteRecursive(path);
                        } catch (IOException ex) {
                            if (!context.isLastAttempt()) {
                                throw ex;
                            } else {
                                // Collect the list of leftover files. Collect at most the first 100 files.
                                var remainingFiles = DirectoryListing.listFilesAndEmptyDirectories(
                                        path, MAX_REPORTED_UNDELETED_FILE_COUNT).paths();

                                if (remainingFiles.equals(List.of(path))) {
                                    Log.info(I18N.format("warning.tempdir.cleanup-failed", path));
                                } else {
                                    remainingFiles.forEach(file -> {
                                        Log.info(I18N.format("warning.tempdir.cleanup-file-failed", file));
                                    });
                                }

                                Log.verbose(ex);
                            }
                        }
                        return null;
                    }).execute();
        }
    }

    record DirectoryListing(List<Path> paths, boolean complete) {
        DirectoryListing {
            Objects.requireNonNull(paths);
        }

        static DirectoryListing listFilesAndEmptyDirectories(Path path, int limit) {
            Objects.requireNonNull(path);
            if (limit < 0) {
                throw new IllegalArgumentException();
            } else if (limit == 0) {
                return new DirectoryListing(List.of(), !Files.exists(path));
            }

            var paths = new ArrayList<Path>();
            var stopped = Slot.<Boolean>createEmpty();

            stopped.set(false);

            try {
                Files.walkFileTree(path, new FileVisitor<>() {

                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                        try (var walk = Files.walk(dir)) {
                            if (walk.skip(1).findAny().isEmpty()) {
                                // This is an empty directory, add it to the list.
                                return addPath(dir, FileVisitResult.SKIP_SUBTREE);
                            }
                        } catch (IOException ex) {
                            Log.verbose(ex);
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        return addPath(file, FileVisitResult.CONTINUE);
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return addPath(file, FileVisitResult.CONTINUE);
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }

                    private FileVisitResult addPath(Path v, FileVisitResult result) {
                        if (paths.size() < limit) {
                            paths.add(v);
                            return result;
                        } else {
                            stopped.set(true);
                        }
                        return FileVisitResult.TERMINATE;
                    }

                });
            } catch (IOException ex) {
                Log.verbose(ex);
            }

            return new DirectoryListing(Collections.unmodifiableList(paths), !stopped.get());
        }
    }

    private final Path path;
    private final boolean deleteOnClose;
    private final RetryExecutorFactory retryExecutorFactory;

    private final static int MAX_REPORTED_UNDELETED_FILE_COUNT = 100;
}
