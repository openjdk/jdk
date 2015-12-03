/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package jdk.tools.jimage;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import jdk.internal.jimage.Archive;
import jdk.internal.jimage.ImageFileCreator;
import jdk.internal.jimage.ImageModuleData;
import jdk.internal.jimage.ImageModuleDataWriter;

/**
 *
 * Support for extracted image.
 */
public final class ExtractedImage {

    /**
     * An Archive backed by a directory.
     */
    public class DirArchive implements Archive {

        /**
         * A File located in a Directory.
         */
        private class FileEntry extends Archive.Entry {

            private final long size;
            private final Path path;

            FileEntry(Path path, String name) {
                super(DirArchive.this, getPathName(path), name,
                        Archive.Entry.EntryType.CLASS_OR_RESOURCE);
                this.path = path;
                try {
                    size = Files.size(path);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }

            /**
             * Returns the number of bytes of this file.
             */
            @Override
            public long size() {
                return size;
            }

            @Override
            public InputStream stream() throws IOException {
                InputStream stream = Files.newInputStream(path);
                open.add(stream);
                return stream;
            }
        }

        private final Path dirPath;
        private final String moduleName;
        private final List<InputStream> open = new ArrayList<>();
        private final int chop;

        protected DirArchive(Path dirPath) throws IOException {
            if (!Files.isDirectory(dirPath)) {
                throw new IOException("Not a directory");
            }
            chop = dirPath.toString().length() + 1;
            this.moduleName = dirPath.getFileName().toString();
            this.dirPath = dirPath;
        }

        @Override
        public String moduleName() {
            return moduleName;
        }

        @Override
        public Stream<Entry> entries() {
            try {
                return Files.walk(dirPath).map(this::toEntry).filter(n -> n != null);
            } catch(IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        private Archive.Entry toEntry(Path p) {
            if (Files.isDirectory(p)) {
                return null;
            }
            String name = getPathName(p).substring(chop);
            if (name.startsWith("_")) {
                return null;
            }
            if (verbose) {
                String verboseName = moduleName + "/" + name;
                log.println(verboseName);
            }

            return new FileEntry(p, name);
        }

        @Override
        public void close() throws IOException {
            IOException e = null;
            for (InputStream stream : open) {
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
        public void open() throws IOException {
            // NOOP
        }
    }
    private Map<String, Set<String>> modulePackages = new LinkedHashMap<>();
    private Set<Archive> archives = new HashSet<>();
    private final PrintWriter log;
    private final boolean verbose;
    private final String jdataName;
    ExtractedImage(Path dirPath, PrintWriter log,
            boolean verbose) throws IOException {
        if (!Files.isDirectory(dirPath)) {
            throw new IOException("Not a directory");
        }
        List<String> jdataNameHolder = new ArrayList<>();
        Files.walk(dirPath, 1).forEach((p) -> {
            try {
                if (!dirPath.equals(p)) {
                    String name = getPathName(p);
                    if (name.endsWith(ImageModuleData.META_DATA_EXTENSION)) {
                        jdataNameHolder.add(p.getFileName().toString());
                        List<String> lines = Files.readAllLines(p);
                        for (Entry<String, List<String>> entry
                                : ImageModuleDataWriter.toModulePackages(lines).entrySet()) {
                            Set<String> pkgs = new HashSet<>();
                            pkgs.addAll(entry.getValue());
                            modulePackages.put(entry.getKey(), pkgs);
                        }
                        modulePackages = Collections.unmodifiableMap(modulePackages);
                    } else {
                        if (Files.isDirectory(p)) {
                            Archive a = new DirArchive(p);
                            archives.add(a);
                        }
                    }
                }
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        });
        archives = Collections.unmodifiableSet(archives);
        this.log = log;
        this.verbose = verbose;
        if (jdataNameHolder.size() != 1) {
            throw new IOException("Wrong module information");
        }
        // The name of the metadata resource must be reused in the recreated jimage
        String name = jdataNameHolder.get(0);
        // Extension will be added when recreating the jimage
        if (name.endsWith(ImageModuleData.META_DATA_EXTENSION)) {
            name = name.substring(0, name.length()
                    - ImageModuleData.META_DATA_EXTENSION.length());
        }
        jdataName = name;
    }

    void recreateJImage(Path path) throws IOException {

        ImageFileCreator.recreateJimage(path, jdataName, archives, modulePackages);
    }

    private static String getPathName(Path path) {
        return path.toString().replace(File.separatorChar, '/');
    }
}
