/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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


package org.graalvm.compiler.debug;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.serviceprovider.GraalServices;

/**
 * Manages a directory into which diagnostics such crash reports and dumps should be written. The
 * directory is archived and deleted when {@link #close()} is called.
 */
public class DiagnosticsOutputDirectory {

    /**
     * Use an illegal file name to denote that {@link #close()} has been called.
     */
    private static final String CLOSED = "\u0000";

    public DiagnosticsOutputDirectory(OptionValues options) {
        this.options = options;
    }

    private final OptionValues options;

    private String path;

    /**
     * Gets the path to the output directory managed by this object, creating if it doesn't exist
     * and has not been deleted.
     *
     * @returns the directory or {@code null} if the could not be created or has been deleted
     */
    public String getPath() {
        return getPath(true);
    }

    private synchronized String getPath(boolean createIfNull) {
        if (path == null && createIfNull) {
            path = createPath();
            File dir = new File(path).getAbsoluteFile();
            if (!dir.exists()) {
                dir.mkdirs();
                if (!dir.exists()) {
                    TTY.println("Warning: could not create Graal diagnostic directory " + dir);
                    return null;
                }
            }
        }
        return CLOSED.equals(path) ? null : path;
    }

    /**
     * Gets the path of the directory to be created.
     *
     * Subclasses can override this to determine how the path name is created.
     *
     * @return the path to be created
     */
    protected String createPath() {
        Path baseDir;
        try {
            baseDir = DebugOptions.getDumpDirectory(options);
        } catch (IOException e) {
            // Default to current directory if there was a problem creating the
            // directory specified by the DumpPath option.
            baseDir = Paths.get(".");
        }
        return baseDir.resolve("graal_diagnostics_" + GraalServices.getExecutionID()).toAbsolutePath().toString();
    }

    /**
     * Archives and deletes this directory if it exists.
     */
    public void close() {
        archiveAndDelete();
    }

    /**
     * Archives and deletes the {@linkplain #getPath() output directory} if it exists.
     */
    private synchronized void archiveAndDelete() {
        String outDir = getPath(false);
        if (outDir != null) {
            // Notify other threads calling getPath() that the directory is deleted.
            // This attempts to mitigate other threads writing to the directory
            // while it is being archived and deleted.
            path = CLOSED;

            Path dir = Paths.get(outDir);
            if (dir.toFile().exists()) {
                File zip = new File(outDir + ".zip").getAbsoluteFile();
                List<Path> toDelete = new ArrayList<>();
                try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zip))) {
                    zos.setLevel(Deflater.BEST_COMPRESSION);
                    Files.walkFileTree(dir, Collections.emptySet(), Integer.MAX_VALUE, new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (attrs.isRegularFile()) {
                                String name = dir.relativize(file).toString();
                                ZipEntry ze = new ZipEntry(name);
                                zos.putNextEntry(ze);
                                Files.copy(file, zos);
                                zos.closeEntry();
                            }
                            toDelete.add(file);
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path d, IOException exc) throws IOException {
                            toDelete.add(d);
                            return FileVisitResult.CONTINUE;
                        }
                    });
                    // Keep this in sync with the catch_files in common.hocon
                    TTY.println("Graal diagnostic output saved in %s", zip);
                } catch (IOException e) {
                    TTY.printf("IO error archiving %s:%n%s. The directory will not be deleted and must be " +
                                    "manually removed once the VM exits.%n", dir, e);
                    toDelete.clear();
                }
                if (!toDelete.isEmpty()) {
                    IOException lastDeletionError = null;
                    for (Path p : toDelete) {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            lastDeletionError = e;
                        }
                    }
                    if (lastDeletionError != null) {
                        TTY.printf("IO error deleting %s:%n%s. This is most likely due to a compilation on " +
                                        "another thread holding a handle to a file within this directory. " +
                                        "Please delete the directory manually once the VM exits.%n", dir, lastDeletionError);
                    }
                }
            }
        }
    }
}
