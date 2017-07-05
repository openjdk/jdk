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

package jdk.tools.jlink.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jdk.tools.jlink.internal.Archive.Entry.EntryType;

/**
 * An Archive backed by a jar file.
 */
public abstract class JarArchive implements Archive {

    /**
     * An entry located in a jar file.
     */
    private class JarEntry extends Entry {

        private final long size;
        private final ZipEntry entry;
        private final ZipFile file;

        JarEntry(String path, String name, EntryType type, ZipFile file, ZipEntry entry) {
            super(JarArchive.this, path, name, type);
            this.entry = entry;
            this.file = file;
            size = entry.getSize();
        }

        /**
         * Returns the number of uncompressed bytes for this entry.
         */
        @Override
        public long size() {
            return size;
        }

        @Override
        public InputStream stream() throws IOException {
            return file.getInputStream(entry);
        }
    }

    private static final String MODULE_INFO = "module-info.class";

    private final Path file;
    private final String moduleName;
    // currently processed ZipFile
    private ZipFile zipFile;

    protected JarArchive(String mn, Path file) {
        Objects.requireNonNull(mn);
        Objects.requireNonNull(file);
        this.moduleName = mn;
        this.file = file;
    }

    @Override
    public String moduleName() {
        return moduleName;
    }

    @Override
    public Path getPath() {
        return file;
    }

    @Override
    public Stream<Entry> entries() {
        try {
            if (zipFile == null) {
                open();
            }
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
        return zipFile.stream().map(this::toEntry).filter(n -> n != null);
    }

    abstract EntryType toEntryType(String entryName);

    abstract String getFileName(String entryName);

    private Entry toEntry(ZipEntry ze) {
        String name = ze.getName();
        String fn = getFileName(name);

        if (ze.isDirectory() || fn.startsWith("_")) {
            return null;
        }

        EntryType rt = toEntryType(name);

        if (fn.equals(MODULE_INFO)) {
            fn = moduleName + "/" + MODULE_INFO;
        }
        return new JarEntry(ze.getName(), fn, rt, zipFile, ze);
    }

    @Override
    public void close() throws IOException {
        if (zipFile != null) {
            zipFile.close();
        }
    }

    @Override
    public void open() throws IOException {
        if (zipFile != null) {
            zipFile.close();
        }
        zipFile = new ZipFile(file.toFile());
    }
}
