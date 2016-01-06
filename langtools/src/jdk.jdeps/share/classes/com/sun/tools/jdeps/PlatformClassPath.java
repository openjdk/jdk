/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.classfile.ClassFile;
import com.sun.tools.jdeps.ClassFileReader.ModuleClassReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedInputStream;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ClassPath for Java SE and JDK
 */
class PlatformClassPath {
    private static List<Archive> modules;
    static synchronized List<Archive> getModules(Path mpath) throws IOException {
        if (modules == null) {
            initPlatformModules(mpath);
        }
        return modules;
    }

    private static void initPlatformModules(Path mpath) throws IOException {
        ImageHelper helper = ImageHelper.getInstance(mpath);
        String fn = System.getProperty("jdeps.modules.xml");
        if (fn != null) {
            Path p = Paths.get(fn);
            try (InputStream in = new BufferedInputStream(Files.newInputStream(p))) {
                modules = new ArrayList<>(ModulesXmlReader.load(helper, in));
            }
        } else {
            try (InputStream in = PlatformClassPath.class
                    .getResourceAsStream("resources/jdeps-modules.xml")) {
                modules = new ArrayList<>(ModulesXmlReader.load(helper, in));
            }
        }
        if (findModule("java.base") != null) {
            Profile.initProfiles(modules);
        }
    }

    /**
     * Finds the module with the given name. Returns null
     * if such module doesn't exist.
     *
     * @param mn module name
     */
    static Module findModule(String mn) {
        for (Archive a : modules) {
            if (Module.class.isInstance(a)) {
                Module m = (Module)a;
                if (mn.equals(m.name())) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * Returns JAR files in $java.home/lib.  This is for transition until
     * all components are linked into jimage.
     */
    static List<Archive> getJarFiles() throws IOException {
        Path home = Paths.get(System.getProperty("java.home"), "lib");
        return Files.find(home, 1, (Path p, BasicFileAttributes attr)
                -> p.getFileName().toString().endsWith(".jar"))
                .map(Archive::getInstance)
                .collect(Collectors.toList());
    }

    static class ImageHelper {
        private static boolean isJrtAvailable() {
            try {
                FileSystems.getFileSystem(URI.create("jrt:/"));
                return true;
            } catch (ProviderNotFoundException | FileSystemNotFoundException e) {
                return false;
            }
        }

        static ImageHelper getInstance(Path mpath) throws IOException {
            if (mpath != null) {
                return new ImageHelper(mpath);
            }

            if (isJrtAvailable()) {
                // jrt file system
                FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
                return new ImageHelper(fs, fs.getPath("/modules"));
            } else {
                // exploded modules
                String home = System.getProperty("java.home");
                Path exploded = Paths.get(home, "modules");
                if (!Files.isDirectory(exploded)) {
                     throw new InternalError(home + " not a modular image");
                }
                return new ImageHelper(exploded);
            }
        }

        private final FileSystem fs;
        private final Path mpath;

        ImageHelper(Path path) throws IOException {
            this(FileSystems.getDefault(), path);
        }

        ImageHelper(FileSystem fs, Path path) throws IOException {
            this.fs = fs;
            this.mpath = path;
        }

        /**
         * Returns a ModuleClassReader that only reads classes for the given modulename.
         */
        public ModuleClassReader getModuleClassReader(String modulename)
            throws IOException
        {
            Path mp = mpath.resolve(modulename);
            if (Files.exists(mp) && Files.isDirectory(mp)) {
                return new ModuleClassReader(fs, modulename, mp);
            } else {
                // aggregator module or os-specific module in jdeps-modules.xml
                // mdir not exist
                return new NonExistModuleReader(fs, modulename, mp);
            }
        }

        static class NonExistModuleReader extends ModuleClassReader {
            private final List<ClassFile> classes = Collections.emptyList();

            private NonExistModuleReader(FileSystem fs, String mn, Path mpath)
                throws IOException
            {
                super(fs, mn, mpath);
            }

            public ClassFile getClassFile(String name) throws IOException {
                return null;
            }

            public Iterable<ClassFile> getClassFiles() throws IOException {
                return classes;
            }

            public Set<String> packages() {
                return Collections.emptySet();
            }
        }
    }
}
