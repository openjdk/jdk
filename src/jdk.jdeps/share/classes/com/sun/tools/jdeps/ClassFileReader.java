/*
 * Copyright (c) 2012, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.jdeps;

import com.sun.tools.jdeps.Dependencies.ClassFileError;

import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * ClassFileReader reads ClassModel(s) of a given path that can be
 * a .class file, a directory, or a JAR file.
 */
public class ClassFileReader implements Closeable {
    /**
     * Returns a ClassFileReader instance of a given path.
     */
    public static ClassFileReader newInstance(Path path, Runtime.Version version) throws IOException {
        if (Files.notExists(path)) {
            throw new FileNotFoundException(path.toString());
        }

        if (Files.isDirectory(path)) {
            return new DirectoryReader(path);
        } else if (path.getFileName().toString().endsWith(".jar")) {
            return new JarFileReader(path, version);
        } else {
            return new ClassFileReader(path);
        }
    }

    /**
     * Returns a ClassFileReader instance of a given FileSystem and path.
     *
     * This method is used for reading classes from jrtfs.
     */
    public static ClassFileReader newInstance(FileSystem fs, Path path) throws IOException {
        return new DirectoryReader(fs, path);
    }

    protected final Path path;
    protected final String baseFileName;
    protected Set<String> entries; // binary names

    protected final List<String> skippedEntries = new ArrayList<>();
    protected ClassFileReader(Path path) {
        this.path = path;
        this.baseFileName = path.getFileName() != null
                                ? path.getFileName().toString()
                                : path.toString();
    }

    public String getFileName() {
        return baseFileName;
    }

    public List<String> skippedEntries() {
        return skippedEntries;
    }

    protected void skipEntry(Throwable ex, String entryPath) {
        skippedEntries.add(String.format("%s: %s", ex.toString(), entryPath));
    }

    /**
     * Returns all entries in this archive.
     */
    public Set<String> entries() {
        Set<String> es = this.entries;
        if (es == null) {
            // lazily scan the entries
            this.entries = scan();
        }
        return this.entries;
    }

    /**
     * Returns the ClassModel matching the given binary name
     * or a fully-qualified class name.
     */
    public ClassModel getClassFile(String name) throws IOException {
        if (name.indexOf('.') > 0) {
            int i = name.lastIndexOf('.');
            String pathname = name.replace('.', File.separatorChar) + ".class";
            if (baseFileName.equals(pathname) ||
                    baseFileName.equals(pathname.substring(0, i) + "$" +
                                        pathname.substring(i+1, pathname.length()))) {
                return readClassFile(path);
            }
        } else {
            if (baseFileName.equals(name.replace('/', File.separatorChar) + ".class")) {
                return readClassFile(path);
            }
        }
        return null;
    }

    public void forEachClassFile(Consumer<ClassModel> handler) throws IOException {
        if (baseFileName.endsWith(".class")) {
            // propagate ClassFileError for single file
            try {
                handler.accept(readClassFile(path));
            } catch (ClassFileError ex) {
                skipEntry(ex, path.toString());
            }
        }
    }

    protected ClassModel readClassFile(Path p) throws IOException {
        try {
            return ClassFile.of().parse(p);
        } catch (IllegalArgumentException e) {
            throw new ClassFileError(e);
        }
    }

    protected Set<String> scan() {
        try {
            ClassModel cf = ClassFile.of().parse(path);
            String name = cf.isModuleInfo()
                ? "module-info" : cf.thisClass().asInternalName();
            return Collections.singleton(name);
        } catch (IllegalArgumentException|IOException e) {
            throw new ClassFileError(e);
        }
    }

    static boolean isClass(Path file) {
        String fn = file.getFileName().toString();
        return fn.endsWith(".class");
    }

    @Override
    public void close() throws IOException {
    }

    public String toString() {
        return path.toString();
    }

    private static class DirectoryReader extends ClassFileReader {
        protected final String fsSep;
        DirectoryReader(Path path) throws IOException {
            this(FileSystems.getDefault(), path);
        }
        DirectoryReader(FileSystem fs, Path path) throws IOException {
            super(path);
            this.fsSep = fs.getSeparator();
        }

        protected Set<String> scan() {
            try (Stream<Path> stream = Files.walk(path, Integer.MAX_VALUE)) {
                return stream.filter(ClassFileReader::isClass)
                             .map(path::relativize)
                             .map(Path::toString)
                             .map(p -> p.replace(File.separatorChar, '/'))
                             .collect(Collectors.toSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        public ClassModel getClassFile(String name) throws IOException {
            if (name.indexOf('.') > 0) {
                int i = name.lastIndexOf('.');
                String pathname = name.replace(".", fsSep) + ".class";
                Path p = path.resolve(pathname);
                if (Files.notExists(p)) {
                    p = path.resolve(pathname.substring(0, i) + "$" +
                            pathname.substring(i+1, pathname.length()));
                }
                if (Files.exists(p)) {
                    return readClassFile(p);
                }
            } else {
                Path p = path.resolve(name + ".class");
                if (Files.exists(p)) {
                    return readClassFile(p);
                }
            }
            return null;
        }

        @Override
        public void forEachClassFile(Consumer<ClassModel> handler) throws IOException {
            try (Stream<Path> stream = Files.walk(path, Integer.MAX_VALUE)) {
                stream.filter(ClassFileReader::isClass)
                      .forEach(e -> {
                          try {
                              handler.accept(readClassFile(e));
                          } catch (ClassFileError | IOException ex) {
                              skipEntry(ex, e.toString());
                          }
                      });
            }
        }
    }

    static class JarFileReader extends ClassFileReader {
        private final JarFile jarfile;
        private final Runtime.Version version;

        JarFileReader(Path path, Runtime.Version version) throws IOException {
            this(path, openJarFile(path.toFile(), version), version);
        }

        JarFileReader(Path path, JarFile jf, Runtime.Version version) throws IOException {
            super(path);
            this.jarfile = jf;
            this.version = version;
        }

        @Override
        public void close() throws IOException {
            jarfile.close();
        }

        private static JarFile openJarFile(File f, Runtime.Version version)
                throws IOException {
            JarFile jf;
            if (version == null) {
                jf = new JarFile(f, false);
                if (jf.isMultiRelease()) {
                    throw new MultiReleaseException("err.multirelease.option.notfound", f.getName());
                }
            } else {
                jf = new JarFile(f, false, ZipFile.OPEN_READ, version);
            }
            return jf;
        }

        private static boolean isJarEntryClass(JarEntry e) {
            return e.getName().endsWith(".class");
        }

        protected Set<String> scan() {
            return jarfile.versionedStream()
                          .filter(JarFileReader::isJarEntryClass)
                          .map(JarEntry::getName)
                          .collect(Collectors.toSet());
        }

        public ClassModel getClassFile(String name) throws IOException {
            if (name.indexOf('.') > 0) {
                int i = name.lastIndexOf('.');
                String entryName = name.replace('.', '/') + ".class";
                JarEntry e = jarfile.getJarEntry(entryName);
                if (e == null) {
                    e = jarfile.getJarEntry(entryName.substring(0, i) + "$"
                            + entryName.substring(i + 1, entryName.length()));
                }
                if (e != null) {
                    return readClassFile(jarfile, e);
                }
            } else {
                JarEntry e = jarfile.getJarEntry(name + ".class");
                if (e != null) {
                    return readClassFile(jarfile, e);
                }
            }
            return null;
        }

        protected ClassModel readClassFile(JarFile jarfile, JarEntry e) throws IOException {
            try (InputStream is = jarfile.getInputStream(e)) {
                ClassModel cf = ClassFile.of().parse(is.readAllBytes());
                // exclude module-info.class since this jarFile is on classpath
                if (jarfile.isMultiRelease() && !cf.isModuleInfo()) {
                    VersionHelper.add(jarfile, e, cf);
                }
                return cf;
            } catch (IllegalArgumentException ex) {
                throw new ClassFileError(ex);
            }
        }

        @Override
        public void forEachClassFile(Consumer<ClassModel> handler) throws IOException {
            jarfile.versionedStream()
                   .filter(JarFileReader::isJarEntryClass)
                   .forEach(e -> {
                       try {
                           handler.accept(readClassFile(jarfile, e));
                       } catch (ClassFileError | IOException ex) {
                           skipEntry(ex, e.getName() + " (" + jarfile.getName() + ")");
                       }
                   });
        }
    }
}
