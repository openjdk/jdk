/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package build.tools.module;

import jdk.internal.jimage.Archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.internal.jimage.Archive.Entry.EntryType;

/**
 * An Archive backed by an exploded representation on disk.
 */
public class ModuleArchive implements Archive {
    private final Path classes;
    private final Path cmds;
    private final Path libs;
    private final Path configs;
    private final String moduleName;

    private final List<InputStream> opened = new ArrayList<>();

    public ModuleArchive(String moduleName, Path classes, Path cmds,
                         Path libs, Path configs) {
        this.moduleName = moduleName;
        this.classes = classes;
        this.cmds = cmds;
        this.libs = libs;
        this.configs = configs;
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public void open() throws IOException {
        // NOOP
    }

    @Override
    public void close() throws IOException {
        IOException e = null;
        for (InputStream stream : opened) {
            try {
                stream.close();
            } catch (IOException ex) {
                if (e == null) {
                    e = ex;
                } else {
                    e.addSuppressed(ex);
                }
            }
        }
        if (e != null) {
            throw e;
        }
    }

    @Override
    public Stream<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        try {
            /*
             * This code should be revisited to avoid buffering of the entries.
             * 1) Do we really need sorting classes? This force buffering of entries.
             *    libs, cmds and configs are not sorted.
             * 2) I/O streams should be concatenated instead of buffering into
             *    entries list.
             * 3) Close I/O streams in a close handler.
             */
            if (classes != null) {
                try (Stream<Path> stream = Files.walk(classes)) {
                    entries.addAll(stream
                            .filter(p -> !Files.isDirectory(p)
                                    && !classes.relativize(p).toString().startsWith("_the.")
                                    && !classes.relativize(p).toString().endsWith(".bc")
                                    && !classes.relativize(p).toString().equals("javac_state"))
                            .sorted()
                            .map(p -> toEntry(p, classes, EntryType.CLASS_OR_RESOURCE))
                            .collect(Collectors.toList()));
                }
            }
            if (cmds != null) {
                try (Stream<Path> stream = Files.walk(cmds)) {
                    entries.addAll(stream
                            .filter(p -> !Files.isDirectory(p))
                            .map(p -> toEntry(p, cmds, EntryType.NATIVE_CMD))
                            .collect(Collectors.toList()));
                }
            }
            if (libs != null) {
                try (Stream<Path> stream = Files.walk(libs)) {
                    entries.addAll(stream
                            .filter(p -> !Files.isDirectory(p))
                            .map(p -> toEntry(p, libs, EntryType.NATIVE_LIB))
                            .collect(Collectors.toList()));
                }
            }
            if (configs != null) {
                try (Stream<Path> stream = Files.walk(configs)) {
                entries.addAll(stream
                        .filter(p -> !Files.isDirectory(p))
                        .map(p -> toEntry(p, configs, EntryType.CONFIG))
                        .collect(Collectors.toList()));
                }
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return entries.stream();
    }

    private class FileEntry extends Entry {
        private final boolean isDirectory;
        private final long size;
        private final Path entryPath;
        FileEntry(Path entryPath, String path, EntryType type,
                  boolean isDirectory, long size) {
            super(ModuleArchive.this, path, path, type);
            this.entryPath = entryPath;
            this.isDirectory = isDirectory;
            this.size = size;
        }

        public boolean isDirectory() {
            return isDirectory;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public InputStream stream() throws IOException {
            InputStream stream = Files.newInputStream(entryPath);
            opened.add(stream);
            return stream;
        }
    }

    private Entry toEntry(Path entryPath, Path basePath, EntryType section) {
        try {
            String path = basePath.relativize(entryPath).toString().replace('\\', '/');
            return new FileEntry(entryPath, path, section,
                    false, Files.size(entryPath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

