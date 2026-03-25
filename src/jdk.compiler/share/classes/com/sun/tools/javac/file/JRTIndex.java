/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.file;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.SoftReference;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.Set;

import javax.tools.FileObject;

import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.util.Assert;

/**
 * A package-oriented index into the jrt: filesystem.
 *
 * <p>Instances of this class may share underlying file-system resources. This
 * is to avoid the need for singleton instances with unbounded lifetimes which
 * could never release and close the underlying JRT file-system, effectively
 * creating a resource leak.
 */
// Final to ensure equals/hashCode are not overridden (instance sharing relies
// on default identity semantics).
public final class JRTIndex implements Closeable {
    /**
     * Potentially shared access to underlying resources. Resources exist for
     * both preview and non-preview mode, and this field holds the version
     * corresponding to the preview mode flag with which it was created.
     */
    private final FileSystemResources sharedResources;

    /**
     * Create and initialize an index based on the preview mode flag.
     */
    private JRTIndex(boolean previewMode) throws IOException {
        this.sharedResources = FileSystemResources.claim(previewMode, this);
    }

    @Override
    public void close() throws IOException {
        // Release is atomic and succeeds at most once per index.
        if (!sharedResources.release(this)) {
            throw new IllegalStateException("JRTIndex is closed");
        }
    }

    /**
     * {@return a JRT index suitable for the given preview mode}
     *
     * <p>The returned instance must be closed by the caller.
     */
    public static JRTIndex instance(boolean previewMode) {
        try {
            return new JRTIndex(previewMode);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** {@return whether the JRT file-system is available to create an index} */
    public static boolean isAvailable() {
        try {
            FileSystems.getFileSystem(URI.create("jrt:/"));
            return true;
        } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
            return false;
        }
    }

    /**
     * Underlying file system resources potentially shared between many indexes.
     *
     * <p>This class is thread-safe so JRT indexes can be created from arbitrary
     * threads.
     */
    private static class FileSystemResources {
        // Holds the active non-preview (index 0) and preview (index 1) indexes.
        // Active instances can be reset multiple times.
        private static final FileSystemResources[] instances = new FileSystemResources[2];

        /** The jrt: file system. */
        private final FileSystem jrtfs;

        /** A lazily evaluated set of entries about the contents of the jrt: file system. */
        // Synchronized by this instance.
        private final Map<RelativeDirectory, SoftReference<Entry>> entries = new HashMap<>();

        // The set of indexes which have claimed this resource. This assumes
        // that index instances have default identity semantics.
        // Synchronized by FileSystemResources.class, NOT instance.
        private final Set<JRTIndex> owners = new HashSet<>();
        private final boolean previewMode;

        // Created on demand in getCtInfo(), synchronized by this instance.
        private ResourceBundle ctBundle = null;

        // Monotonic, synchronized by this instance.
        private boolean isClosed = false;

        private FileSystemResources(boolean previewMode) throws IOException {
            this.jrtfs = FileSystems.newFileSystem(URI.create("jrt:/"), Map.of("previewMode", Boolean.toString(previewMode)));
            this.previewMode = previewMode;
        }

        /** Claims shared ownership of resources for in index. */
        static FileSystemResources claim(boolean previewMode, JRTIndex owner) throws IOException {
            int idx = previewMode ? 1 : 0;
            synchronized (FileSystemResources.class) {
                var active = instances[idx];
                if (active == null) {
                    active = new FileSystemResources(previewMode);
                    instances[idx] = active;
                }
                // Since claim is only called once per instance (during init)
                // seeing an index that's already claimed should be impossible.
                Assert.check(active.owners.add(owner));
                return active;
            }
        }

        /**
         * Releases ownership of this resource for an index with an existing claim.
         *
         * @return whether the given index is being released for the first time
         */
        boolean release(JRTIndex owner) throws IOException {
            int idx = previewMode ? 1 : 0;
            boolean shouldClose;
            synchronized (FileSystemResources.class) {
                Assert.check(instances[idx] == this);
                // Not finding a claim means the index was already released/closed.
                if (!owners.remove(owner)) {
                    return false;
                }
                shouldClose = owners.isEmpty();
                if (shouldClose) {
                    instances[idx] = null;
                }
            }
            if (shouldClose) {
                // This should be the only call to close() on the resource instance.
                close();
            }
            return true;
        }

        /** Close underlying shared resources once no users exist (called exactly once). */
        private synchronized void close() throws IOException {
            Assert.check(!isClosed);
            jrtfs.close();
            entries.clear();
            ctBundle = null;
            isClosed = true;
        }

        synchronized Entry getEntry(RelativeDirectory rd) throws IOException {
            if (isClosed) {
                throw new IllegalStateException("JRTIndex is closed");
            }
            SoftReference<Entry> ref = entries.get(rd);
            Entry e = (ref == null) ? null : ref.get();
            if (e == null) {
                Map<String, Path> files = new LinkedHashMap<>();
                Set<RelativeDirectory> subdirs = new LinkedHashSet<>();
                Path dir;
                if (rd.path.isEmpty()) {
                    dir = jrtfs.getPath("/modules");
                } else {
                    Path pkgs = jrtfs.getPath("/packages");
                    dir = pkgs.resolve(rd.getPath().replaceAll("/$", "").replace("/", "."));
                }
                if (Files.exists(dir)) {
                    try (DirectoryStream<Path> modules = Files.newDirectoryStream(dir)) {
                        for (Path module: modules) {
                            if (Files.isSymbolicLink(module))
                                module = Files.readSymbolicLink(module);
                            Path p = rd.resolveAgainst(module);
                            if (!Files.exists(p))
                                continue;
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(p)) {
                                for (Path entry: stream) {
                                    String name = entry.getFileName().toString();
                                    if (Files.isRegularFile(entry)) {
                                        // TODO: consider issue of files with same name in different modules
                                        files.put(name, entry);
                                    } else if (Files.isDirectory(entry)) {
                                        subdirs.add(new RelativeDirectory(rd, name));
                                    }
                                }
                            }
                        }
                    }
                }
                e = new Entry(Collections.unmodifiableMap(files),
                        Collections.unmodifiableSet(subdirs),
                        getCtInfo(rd));
                entries.put(rd, new SoftReference<>(e));
            }
            return e;
        }

        private CtSym getCtInfo(RelativeDirectory dir) {
            if (dir.path.isEmpty())
                return CtSym.EMPTY;
            // It's a side-effect of the default build rules that ct.properties
            // ends up as a resource bundle.
            if (ctBundle == null) {
                final String bundleName = "com.sun.tools.javac.resources.ct";
                ctBundle = ResourceBundle.getBundle(bundleName);
            }
            try {
                String attrs = ctBundle.getString(dir.path.replace('/', '.') + '*');
                boolean hidden = false;
                boolean proprietary = false;
                String minProfile = null;
                for (String attr: attrs.split(" +", 0)) {
                    switch (attr) {
                        case "hidden":
                            hidden = true;
                            break;
                        case "proprietary":
                            proprietary = true;
                            break;
                        default:
                            minProfile = attr;
                    }
                }
                return new CtSym(hidden, proprietary, minProfile);
            } catch (MissingResourceException e) {
                return CtSym.EMPTY;
            }

        }

        boolean isJrtPath(Path p) {
            // This still succeeds after the jrtfs is closed.
            return (p.getFileSystem() == jrtfs);
        }
    }

    /**
     * An entry provides cached info about a specific package directory within jrt:.
     */
    static class Entry {
        /**
         * The regular files for this package.
         * For now, assume just one instance of each file across all modules.
         */
        final Map<String, Path> files;

        /**
         * The set of subdirectories in jrt: for this package.
         */
        final Set<RelativeDirectory> subdirs;

        /**
         * The info that used to be in ct.sym for classes in this package.
         */
        final CtSym ctSym;

        private Entry(Map<String, Path> files, Set<RelativeDirectory> subdirs, CtSym ctSym) {
            this.files = files;
            this.subdirs = subdirs;
            this.ctSym = ctSym;
        }
    }

    /**
     * The info that used to be in ct.sym for classes in a package.
     */
    public static class CtSym {
        /**
         * The classes in this package are internal and not visible.
         */
        public final boolean hidden;
        /**
         * The classes in this package are proprietary and will generate a warning.
         */
        public final boolean proprietary;
        /**
         * The minimum profile in which classes in this package are available.
         */
        public final String minProfile;

        CtSym(boolean hidden, boolean proprietary, String minProfile) {
            this.hidden = hidden;
            this.proprietary = proprietary;
            this.minProfile = minProfile;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder("CtSym[");
            boolean needSep = false;
            if (hidden) {
                sb.append("hidden");
                needSep = true;
            }
            if (proprietary) {
                if (needSep) sb.append(",");
                sb.append("proprietary");
                needSep = true;
            }
            if (minProfile != null) {
                if (needSep) sb.append(",");
                sb.append(minProfile);
            }
            sb.append("]");
            return sb.toString();
        }

        static final CtSym EMPTY = new CtSym(false, false, null);
    }

    /**
     * Returns a non-owned reference to the file system underlying this index.
     *
     * <p>When this index is closed its file system, and any {@link Path paths}
     * derived from it, will become unusable.
     */
    public FileSystem getFileSystem() {
        return sharedResources.jrtfs;
    }

    /**
     * Returns symbol information (possibly cached) for a given package.
     *
     * <p>This remains usable after the index is closed.
     */
    public CtSym getCtSym(CharSequence packageName) throws IOException {
        return getEntry(RelativeDirectory.forPackage(packageName)).ctSym;
    }

    /**
     * Returns package information (possibly cached) for the given directory.
     *
     * <p>When this index is closed its file system, and any {@link Path paths}
     * derived from it, will become unusable. This includes paths inside this
     * entry.
     */
    Entry getEntry(RelativeDirectory rd) throws IOException {
        return sharedResources.getEntry(rd);
    }

    /**
     * {@returns whether the given {@link FileObject file} belongs to this index}
     *
     * <p>A file "belongs" to an index if it was found in that index by {@code
     * ClassFinder}. Since indexes can differ with respect to preview mode, it
     * is important that the {@code ClassFinder} and {@link JavacFileManager}
     * agree on the preview mode setting being used during compilation.
     *
     * <p>This test will continue to succeed after the index is closed, but the
     * file object will no longer be usable.
     */
    public boolean isInJRT(FileObject fo) {
        // It is not sufficient to test if the file's path is *any* JRT path,
        // it must exist in the file-system instance of this index (which should
        // be the same index used by ClassFinder to obtain file objects).
        if (fo instanceof PathFileObject pathFileObject) {
            return sharedResources.isJrtPath(pathFileObject.getPath());
        } else {
            return false;
        }
    }
}
