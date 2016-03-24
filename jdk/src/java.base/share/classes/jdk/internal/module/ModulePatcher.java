/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.module;

import java.io.Closeable;
import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import jdk.internal.misc.JavaLangModuleAccess;
import jdk.internal.misc.SharedSecrets;
import sun.misc.Resource;
import sun.net.www.ParseUtil;


/**
 * Provides support for patching modules in the boot layer with -Xpatch.
 */

public final class ModulePatcher {

    private static final JavaLangModuleAccess JLMA
        = SharedSecrets.getJavaLangModuleAccess();

    // the prefix of the system properties that encode the value of -Xpatch
    private static final String PATCH_PROPERTY_PREFIX = "jdk.launcher.patch.";

    // module name -> sequence of patches (directories or JAR files)
    private static final Map<String, List<Path>> PATCH_MAP = decodeProperties();

    private ModulePatcher() { }


    /**
     * Decodes the values of -Xpatch options, returning a Map of module name to
     * list of file paths.
     *
     * @throws IllegalArgumentException if the the module name is missing or
     *         -Xpatch is used more than once to patch the same module
     */
    private static Map<String, List<Path>> decodeProperties() {

        int index = 0;
        String value = System.getProperty(PATCH_PROPERTY_PREFIX + index);
        if (value == null)
            return Collections.emptyMap();  // -Xpatch not specified

        Map<String, List<Path>> map = new HashMap<>();
        while (value != null) {
            int pos = value.indexOf('=');

            if (pos == -1 && index > 0)
                throwIAE("Unable to parse: " + value);

            if (pos == 0)
                throwIAE("Missing module name: " + value);

            if (pos > 0) {

                // new format: <module>=<file>(:<file>)*

                String mn = value.substring(0, pos);
                List<Path> list = map.get(mn);
                if (list != null)
                    throwIAE("Module " + mn + " specified more than once");
                list = new ArrayList<>();
                map.put(mn, list);

                String paths = value.substring(pos+1);
                for (String path : paths.split(File.pathSeparator)) {
                    if (!path.isEmpty()) {
                        list.add(Paths.get(path));
                    }
                }

            } else {

                // old format: <dir>(:<dir>)*

                assert index == 0; // old format only allowed in first -Xpatch

                String[] dirs = value.split(File.pathSeparator);
                for (String d : dirs) {
                    if (d.length() > 0) {
                        Path top = Paths.get(d);
                        try {
                            Files.list(top).forEach(e -> {
                                String mn = e.getFileName().toString();
                                Path dir = top.resolve(mn);
                                map.computeIfAbsent(mn, k -> new ArrayList<>())
                                    .add(dir);
                            });
                        } catch (IOException ignore) { }
                    }
                }

            }


            index++;
            value = System.getProperty(PATCH_PROPERTY_PREFIX + index);
        }

        return map;
    }


    /**
     * Returns a module reference that interposes on the given module if
     * needed. If there are no patches for the given module then the module
     * reference is simply returned. Otherwise the patches for the module
     * are scanned (to find any new concealed packages) and a new module
     * reference is returned.
     *
     * @throws UncheckedIOException if an I/O error is detected
     */
    public static ModuleReference interposeIfNeeded(ModuleReference mref) {

        ModuleDescriptor descriptor = mref.descriptor();
        String mn = descriptor.name();

        // if there are no patches for the module then nothing to do
        List<Path> paths = PATCH_MAP.get(mn);
        if (paths == null)
            return mref;


        // scan the JAR file or directory tree to get the set of packages
        Set<String> packages = new HashSet<>();
        try {
            for (Path file : paths) {
                if (Files.isRegularFile(file)) {

                    // JAR file
                    try (JarFile jf = new JarFile(file.toFile())) {
                        jf.stream()
                          .filter(e -> e.getName().endsWith(".class"))
                          .map(e -> toPackageName(file, e))
                          .filter(pn -> pn.length() > 0)
                          .forEach(packages::add);
                    }

                } else if (Files.isDirectory(file)) {

                    // exploded directory
                    Path top = file;
                    Files.find(top, Integer.MAX_VALUE,
                            ((path, attrs) -> attrs.isRegularFile() &&
                                    path.toString().endsWith(".class")))
                            .map(path -> toPackageName(top, path))
                            .filter(pn -> pn.length() > 0)
                            .forEach(packages::add);

                }
            }

        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }

        // if there are new packages then we need a new ModuleDescriptor
        Set<String> original = descriptor.packages();
        packages.addAll(original);
        if (packages.size() > original.size()) {
            descriptor = JLMA.newModuleDescriptor(descriptor, packages);
        }

        // return a new module reference
        URI location = mref.location().orElse(null);
        return new ModuleReference(descriptor, location,
                                   () -> new PatchedModuleReader(paths, mref));

    }


    /**
     * A ModuleReader that reads resources from a patched module.
     *
     * This class is public so as to expose the findResource method to the
     * built-in class loaders and avoid locating the resource twice during
     * class loading (once to locate the resource, the second to gets the
     * URL for the CodeSource).
     */
    public static class PatchedModuleReader implements ModuleReader {
        private final List<ResourceFinder> finders;
        private final ModuleReference mref;
        private final URL delegateCodeSourceURL;
        private volatile ModuleReader delegate;

        /**
         * Creates the ModuleReader to reads resources a patched module.
         */
        PatchedModuleReader(List<Path> patches, ModuleReference mref) {
            List<ResourceFinder> finders = new ArrayList<>();
            boolean initialized = false;
            try {
                for (Path file : patches) {
                    if (Files.isRegularFile(file)) {
                        finders.add(new JarResourceFinder(file));
                    } else {
                        finders.add(new ExplodedResourceFinder(file));
                    }
                }
                initialized = true;
            } catch (IOException ioe) {
                throw new UncheckedIOException(ioe);
            } finally {
                // close all ResourceFinder in the event of an error
                if (!initialized) closeAll(finders);
            }

            this.finders = finders;
            this.mref = mref;
            this.delegateCodeSourceURL = codeSourceURL(mref);
        }

        /**
         * Closes all resource finders.
         */
        private static void closeAll(List<ResourceFinder> finders) {
            for (ResourceFinder finder : finders) {
                try { finder.close(); } catch (IOException ioe) { }
            }
        }

        /**
         * Returns the code source URL for the given module.
         */
        private static URL codeSourceURL(ModuleReference mref) {
            try {
                Optional<URI> ouri = mref.location();
                if (ouri.isPresent())
                    return ouri.get().toURL();
            } catch (MalformedURLException e) { }
            return null;
        }

        /**
         * Returns the ModuleReader to delegate to when the resource is not
         * found in a patch location.
         */
        private ModuleReader delegate() throws IOException {
            ModuleReader r = delegate;
            if (r == null) {
                synchronized (this) {
                    r = delegate;
                    if (r == null) {
                        delegate = r = mref.open();
                    }
                }
            }
            return r;
        }

        /**
         * Finds a resources in the patch locations. Returns null if not found.
         */
        private Resource findResourceInPatch(String name) throws IOException {
            for (ResourceFinder finder : finders) {
                Resource r = finder.find(name);
                if (r != null)
                    return r;
            }
            return null;
        }

        /**
         * Finds a resource of the given name in the patched module.
         */
        public Resource findResource(String name) throws IOException {

            // patch locations
            Resource r = findResourceInPatch(name);
            if (r != null)
                return r;

            // original module
            ByteBuffer bb = delegate().read(name).orElse(null);
            if (bb == null)
                return null;

            return new Resource() {
                private <T> T shouldNotGetHere(Class<T> type) {
                    throw new InternalError("should not get here");
                }
                @Override
                public String getName() {
                    return shouldNotGetHere(String.class);
                }
                @Override
                public URL getURL() {
                    return shouldNotGetHere(URL.class);
                }
                @Override
                public URL getCodeSourceURL() {
                    return delegateCodeSourceURL;
                }
                @Override
                public ByteBuffer getByteBuffer() throws IOException {
                    return bb;
                }
                @Override
                public InputStream getInputStream() throws IOException {
                    return shouldNotGetHere(InputStream.class);
                }
                @Override
                public int getContentLength() throws IOException {
                    return shouldNotGetHere(int.class);
                }
            };
        }

        @Override
        public Optional<URI> find(String name) throws IOException {
            Resource r = findResourceInPatch(name);
            if (r != null) {
                URI uri = URI.create(r.getURL().toString());
                return Optional.of(uri);
            } else {
                return delegate().find(name);
            }
        }

        @Override
        public Optional<InputStream> open(String name) throws IOException {
            Resource r = findResourceInPatch(name);
            if (r != null) {
                return Optional.of(r.getInputStream());
            } else {
                return delegate().open(name);
            }
        }

        @Override
        public Optional<ByteBuffer> read(String name) throws IOException {
            Resource r = findResourceInPatch(name);
            if (r != null) {
                ByteBuffer bb = r.getByteBuffer();
                assert !bb.isDirect();
                return Optional.of(bb);
            } else {
                return delegate().read(name);
            }
        }

        @Override
        public void release(ByteBuffer bb) {
            if (bb.isDirect()) {
                try {
                    delegate().release(bb);
                } catch (IOException ioe) {
                    throw new InternalError(ioe);
                }
            }
        }

        @Override
        public void close() throws IOException {
            closeAll(finders);
            delegate().close();
        }
    }


    /**
     * A resource finder that find resources in a patch location.
     */
    private static interface ResourceFinder extends Closeable {
        Resource find(String name) throws IOException;
    }


    /**
     * A ResourceFinder that finds resources in a JAR file.
     */
    private static class JarResourceFinder implements ResourceFinder {
        private final JarFile jf;
        private final URL csURL;

        JarResourceFinder(Path path) throws IOException {
            this.jf = new JarFile(path.toFile());
            this.csURL = path.toUri().toURL();
        }

        @Override
        public void close() throws IOException {
            jf.close();
        }

        @Override
        public Resource find(String name) throws IOException {
            JarEntry entry = jf.getJarEntry(name);
            if (entry == null)
                return null;

            return new Resource() {
                @Override
                public String getName() {
                    return name;
                }
                @Override
                public URL getURL() {
                    String encodedPath = ParseUtil.encodePath(name, false);
                    try {
                        return new URL("jar:" + csURL + "!/" + encodedPath);
                    } catch (MalformedURLException e) {
                        return null;
                    }
                }
                @Override
                public URL getCodeSourceURL() {
                    return csURL;
                }
                @Override
                public ByteBuffer getByteBuffer() throws IOException {
                    byte[] bytes = getInputStream().readAllBytes();
                    return ByteBuffer.wrap(bytes);
                }
                @Override
                public InputStream getInputStream() throws IOException {
                    return jf.getInputStream(entry);
                }
                @Override
                public int getContentLength() throws IOException {
                    long size = entry.getSize();
                    return (size > Integer.MAX_VALUE) ? -1 : (int) size;
                }
            };
        }
    }


    /**
     * A ResourceFinder that finds resources on the file system.
     */
    private static class ExplodedResourceFinder implements ResourceFinder {
        private final Path dir;

        ExplodedResourceFinder(Path dir) {
            this.dir = dir;
        }

        @Override
        public void close() { }

        @Override
        public Resource find(String name) throws IOException {
            Path file = Paths.get(name.replace('/', File.separatorChar));
            if (file.getRoot() == null) {
                file = dir.resolve(file);
            } else {
                // drop the root component so that the resource is
                // located relative to the module directory
                int n = file.getNameCount();
                if (n == 0)
                    return null;
                file = dir.resolve(file.subpath(0, n));
            }

            if (Files.isRegularFile(file)) {
                return newResource(name, dir, file);
            } else {
                return null;
            }
        }

        private Resource newResource(String name, Path top, Path file) {
            return new Resource() {
                @Override
                public String getName() {
                    return name;
                }
                @Override
                public URL getURL() {
                    try {
                        return file.toUri().toURL();
                    } catch (IOException | IOError e) {
                        return null;
                    }
                }
                @Override
                public URL getCodeSourceURL() {
                    try {
                        return top.toUri().toURL();
                    } catch (IOException | IOError e) {
                        return null;
                    }
                }
                @Override
                public ByteBuffer getByteBuffer() throws IOException {
                    return ByteBuffer.wrap(Files.readAllBytes(file));
                }
                @Override
                public InputStream getInputStream() throws IOException {
                    return Files.newInputStream(file);
                }
                @Override
                public int getContentLength() throws IOException {
                    long size = Files.size(file);
                    return (size > Integer.MAX_VALUE) ? -1 : (int)size;
                }
            };
        }
    }


    /**
     * Derives a package name from a file path to a .class file.
     */
    private static String toPackageName(Path top, Path file) {
        Path entry = top.relativize(file);
        Path parent = entry.getParent();
        if (parent == null) {
            return warnUnnamedPackage(top, entry.toString());
        } else {
            return parent.toString().replace(File.separatorChar, '.');
        }
    }

    /**
     * Derives a package name from the name of an entry in a JAR file.
     */
    private static String toPackageName(Path file, JarEntry entry) {
        String name = entry.getName();
        int index = name.lastIndexOf("/");
        if (index == -1) {
            return warnUnnamedPackage(file, name);
        } else {
            return name.substring(0, index).replace('/', '.');
        }
    }

    private static String warnUnnamedPackage(Path file, String e) {
        System.err.println("WARNING: " + e + " not allowed in patch: " + file);
        return "";
    }

    private static void throwIAE(String msg) {
        throw new IllegalArgumentException(msg);
    }

}