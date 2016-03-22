/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.ProviderNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.lang.model.SourceVersion;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.resources.CompilerProperties.Errors;
import com.sun.tools.javac.resources.CompilerProperties.Warnings;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Pair;
import com.sun.tools.javac.util.StringUtils;

import static javax.tools.StandardLocation.PLATFORM_CLASS_PATH;

import static com.sun.tools.javac.main.Option.BOOTCLASSPATH;
import static com.sun.tools.javac.main.Option.DJAVA_ENDORSED_DIRS;
import static com.sun.tools.javac.main.Option.DJAVA_EXT_DIRS;
import static com.sun.tools.javac.main.Option.ENDORSEDDIRS;
import static com.sun.tools.javac.main.Option.EXTDIRS;
import static com.sun.tools.javac.main.Option.XBOOTCLASSPATH;
import static com.sun.tools.javac.main.Option.XBOOTCLASSPATH_APPEND;
import static com.sun.tools.javac.main.Option.XBOOTCLASSPATH_PREPEND;

/**
 * This class converts command line arguments, environment variables and system properties (in
 * File.pathSeparator-separated String form) into a boot class path, user class path, and source
 * path (in {@code Collection<String>} form).
 *
 * <p>
 * <b>This is NOT part of any supported API. If you write code that depends on this, you do so at
 * your own risk. This code and its internal interfaces are subject to change or deletion without
 * notice.</b>
 */
public class Locations {

    /**
     * The log to use for warning output
     */
    private Log log;

    /**
     * Access to (possibly cached) file info
     */
    private FSInfo fsInfo;

    /**
     * Whether to warn about non-existent path elements
     */
    private boolean warn;

    private ModuleNameReader moduleNameReader;

    static final Path javaHome = Paths.get(System.getProperty("java.home"));
    static final Path thisSystemModules = javaHome.resolve("lib").resolve("modules");

    Map<Path, FileSystem> fileSystems = new LinkedHashMap<>();
    List<Closeable> closeables = new ArrayList<>();

    Locations() {
        initHandlers();
    }

    public void close() throws IOException {
        ListBuffer<IOException> list = new ListBuffer<>();
        closeables.forEach(closeable -> {
            try {
                closeable.close();
            } catch (IOException ex) {
                list.add(ex);
            }
        });
        if (list.nonEmpty()) {
            IOException ex = new IOException();
            for (IOException e: list)
                ex.addSuppressed(e);
            throw ex;
        }
    }

    // could replace Lint by "boolean warn"
    void update(Log log, Lint lint, FSInfo fsInfo) {
        this.log = log;
        warn = lint.isEnabled(Lint.LintCategory.PATH);
        this.fsInfo = fsInfo;
    }

    boolean isDefaultBootClassPath() {
        BootClassPathLocationHandler h
                = (BootClassPathLocationHandler) getHandler(PLATFORM_CLASS_PATH);
        return h.isDefault();
    }

    /**
     * Split a search path into its elements. Empty path elements will be ignored.
     *
     * @param searchPath The search path to be split
     * @return The elements of the path
     */
    private static Iterable<Path> getPathEntries(String searchPath) {
        return getPathEntries(searchPath, null);
    }

    /**
     * Split a search path into its elements. If emptyPathDefault is not null, all empty elements in the
     * path, including empty elements at either end of the path, will be replaced with the value of
     * emptyPathDefault.
     *
     * @param searchPath The search path to be split
     * @param emptyPathDefault The value to substitute for empty path elements, or null, to ignore
     * empty path elements
     * @return The elements of the path
     */
    private static Iterable<Path> getPathEntries(String searchPath, Path emptyPathDefault) {
        ListBuffer<Path> entries = new ListBuffer<>();
        for (String s: searchPath.split(Pattern.quote(File.pathSeparator), -1)) {
            if (s.isEmpty()) {
                if (emptyPathDefault != null) {
                    entries.add(emptyPathDefault);
                }
            } else {
                entries.add(Paths.get(s));
            }
        }
        return entries;
    }

    /**
     * Utility class to help evaluate a path option. Duplicate entries are ignored, jar class paths
     * can be expanded.
     */
    private class SearchPath extends LinkedHashSet<Path> {

        private static final long serialVersionUID = 0;

        private boolean expandJarClassPaths = false;
        private final Set<Path> canonicalValues = new HashSet<>();

        public SearchPath expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        /**
         * What to use when path element is the empty string
         */
        private Path emptyPathDefault = null;

        public SearchPath emptyPathDefault(Path x) {
            emptyPathDefault = x;
            return this;
        }

        public SearchPath addDirectories(String dirs, boolean warn) {
            boolean prev = expandJarClassPaths;
            expandJarClassPaths = true;
            try {
                if (dirs != null) {
                    for (Path dir : getPathEntries(dirs)) {
                        addDirectory(dir, warn);
                    }
                }
                return this;
            } finally {
                expandJarClassPaths = prev;
            }
        }

        public SearchPath addDirectories(String dirs) {
            return addDirectories(dirs, warn);
        }

        private void addDirectory(Path dir, boolean warn) {
            if (!Files.isDirectory(dir)) {
                if (warn) {
                    log.warning(Lint.LintCategory.PATH,
                            "dir.path.element.not.found", dir);
                }
                return;
            }

            try (Stream<Path> s = Files.list(dir)) {
                s.filter(dirEntry -> isArchive(dirEntry))
                        .forEach(dirEntry -> addFile(dirEntry, warn));
            } catch (IOException ignore) {
            }
        }

        public SearchPath addFiles(String files, boolean warn) {
            if (files != null) {
                addFiles(getPathEntries(files, emptyPathDefault), warn);
            }
            return this;
        }

        public SearchPath addFiles(String files) {
            return addFiles(files, warn);
        }

        public SearchPath addFiles(Iterable<? extends Path> files, boolean warn) {
            if (files != null) {
                for (Path file : files) {
                    addFile(file, warn);
                }
            }
            return this;
        }

        public SearchPath addFiles(Iterable<? extends Path> files) {
            return addFiles(files, warn);
        }

        public void addFile(Path file, boolean warn) {
            if (contains(file)) {
                // discard duplicates
                return;
            }

            if (!fsInfo.exists(file)) {
                /* No such file or directory exists */
                if (warn) {
                    log.warning(Lint.LintCategory.PATH,
                            "path.element.not.found", file);
                }
                super.add(file);
                return;
            }

            Path canonFile = fsInfo.getCanonicalFile(file);
            if (canonicalValues.contains(canonFile)) {
                /* Discard duplicates and avoid infinite recursion */
                return;
            }

            if (fsInfo.isFile(file)) {
                /* File is an ordinary file. */
                if (!isArchive(file)
                        && !file.getFileName().toString().endsWith(".jmod")
                        && !file.endsWith("modules")) {
                    /* Not a recognized extension; open it to see if
                     it looks like a valid zip file. */
                    try {
                        // TODO: use of ZipFile should be updated
                        ZipFile z = new ZipFile(file.toFile());
                        z.close();
                        if (warn) {
                            log.warning(Lint.LintCategory.PATH,
                                    "unexpected.archive.file", file);
                        }
                    } catch (IOException e) {
                        // FIXME: include e.getLocalizedMessage in warning
                        if (warn) {
                            log.warning(Lint.LintCategory.PATH,
                                    "invalid.archive.file", file);
                        }
                        return;
                    }
                }
            }

            /* Now what we have left is either a directory or a file name
             conforming to archive naming convention */
            super.add(file);
            canonicalValues.add(canonFile);

            if (expandJarClassPaths && fsInfo.isFile(file) && !file.endsWith("modules")) {
                addJarClassPath(file, warn);
            }
        }

        // Adds referenced classpath elements from a jar's Class-Path
        // Manifest entry.  In some future release, we may want to
        // update this code to recognize URLs rather than simple
        // filenames, but if we do, we should redo all path-related code.
        private void addJarClassPath(Path jarFile, boolean warn) {
            try {
                for (Path f : fsInfo.getJarClassPath(jarFile)) {
                    addFile(f, warn);
                }
            } catch (IOException e) {
                log.error("error.reading.file", jarFile, JavacFileManager.getMessage(e));
            }
        }
    }

    /**
     * Base class for handling support for the representation of Locations.
     *
     * Locations are (by design) opaque handles that can easily be implemented
     * by enums like StandardLocation. Within JavacFileManager, each Location
     * has an associated LocationHandler, which provides much of the appropriate
     * functionality for the corresponding Location.
     *
     * @see #initHandlers
     * @see #getHandler
     */
    protected abstract class LocationHandler {

        /**
         * @see JavaFileManager#handleOption
         */
        abstract boolean handleOption(Option option, String value);

        /**
         * @see StandardJavaFileManager#hasLocation
         */
        boolean isSet() {
            return (getPaths() != null);
        }

        /**
         * @see StandardJavaFileManager#getLocation
         */
        abstract Collection<Path> getPaths();

        /**
         * @see StandardJavaFileManager#setLocation
         */
        abstract void setPaths(Iterable<? extends Path> files) throws IOException;

        /**
         * @see JavaFileManager#getModuleLocation(Location, String)
         */
        Location getModuleLocation(String moduleName) throws IOException {
            return null;
        }

        /**
         * @see JavaFileManager#getModuleLocation(Location, JavaFileObject, String)
         */
        Location getModuleLocation(Path dir) {
            return null;
        }

        /**
         * @see JavaFileManager#inferModuleName
         */
        String inferModuleName() {
            return null;
        }

        /**
         * @see JavaFileManager#listModuleLocations
         */
        Iterable<Set<Location>> listModuleLocations() throws IOException {
            return null;
        }
    }

    /**
     * A LocationHandler for a given Location, and associated set of options.
     */
    private abstract class BasicLocationHandler extends LocationHandler {

        final Location location;
        final Set<Option> options;

        /**
         * Create a handler. The location and options provide a way to map from a location or an
         * option to the corresponding handler.
         *
         * @param location the location for which this is the handler
         * @param options the options affecting this location
         * @see #initHandlers
         */
        protected BasicLocationHandler(Location location, Option... options) {
            this.location = location;
            this.options = options.length == 0
                    ? EnumSet.noneOf(Option.class)
                    : EnumSet.copyOf(Arrays.asList(options));
        }
    }

    /**
     * General purpose implementation for output locations, such as -d/CLASS_OUTPUT and
     * -s/SOURCE_OUTPUT. All options are treated as equivalent (i.e. aliases.)
     * The value is a single file, possibly null.
     */
    private class OutputLocationHandler extends BasicLocationHandler {

        private Path outputDir;
        private Map<String, Location> moduleLocations;

        OutputLocationHandler(Location location, Option... options) {
            super(location, options);
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option)) {
                return false;
            }

            // TODO: could/should validate outputDir exists and is a directory
            // need to decide how best to report issue for benefit of
            // direct API call on JavaFileManager.handleOption(specifies IAE)
            // vs. command line decoding.
            outputDir = (value == null) ? null : Paths.get(value);
            return true;
        }

        @Override
        Collection<Path> getPaths() {
            return (outputDir == null) ? null : Collections.singleton(outputDir);
        }

        @Override
        void setPaths(Iterable<? extends Path> files) throws IOException {
            if (files == null) {
                outputDir = null;
            } else {
                Iterator<? extends Path> pathIter = files.iterator();
                if (!pathIter.hasNext()) {
                    throw new IllegalArgumentException("empty path for directory");
                }
                Path dir = pathIter.next();
                if (pathIter.hasNext()) {
                    throw new IllegalArgumentException("path too long for directory");
                }
                if (!Files.exists(dir)) {
                    throw new FileNotFoundException(dir + ": does not exist");
                } else if (!Files.isDirectory(dir)) {
                    throw new IOException(dir + ": not a directory");
                }
                outputDir = dir;
            }
            moduleLocations = null;
        }

        @Override
        Location getModuleLocation(String name) {
            if (moduleLocations == null)
                moduleLocations = new HashMap<>();
            Location l = moduleLocations.get(name);
            if (l == null) {
                l = new ModuleLocationHandler(location.getName() + "[" + name + "]",
                        name,
                        Collections.singleton(outputDir.resolve(name)),
                        true, false);
                moduleLocations.put(name, l);
            }
            return l;
        }
    }

    /**
     * General purpose implementation for search path locations,
     * such as -sourcepath/SOURCE_PATH and -processorPath/ANNOTATION_PROCESSOR_PATH.
     * All options are treated as equivalent (i.e. aliases.)
     * The value is an ordered set of files and/or directories.
     */
    private class SimpleLocationHandler extends BasicLocationHandler {

        protected Collection<Path> searchPath;

        SimpleLocationHandler(Location location, Option... options) {
            super(location, options);
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option)) {
                return false;
            }
            searchPath = value == null ? null
                    : Collections.unmodifiableCollection(createPath().addFiles(value));
            return true;
        }

        @Override
        Collection<Path> getPaths() {
            return searchPath;
        }

        @Override
        void setPaths(Iterable<? extends Path> files) {
            SearchPath p;
            if (files == null) {
                p = computePath(null);
            } else {
                p = createPath().addFiles(files);
            }
            searchPath = Collections.unmodifiableCollection(p);
        }

        protected SearchPath computePath(String value) {
            return createPath().addFiles(value);
        }

        protected SearchPath createPath() {
            return new SearchPath();
        }
    }

    /**
     * Subtype of SimpleLocationHandler for -classpath/CLASS_PATH.
     * If no value is given, a default is provided, based on system properties and other values.
     */
    private class ClassPathLocationHandler extends SimpleLocationHandler {

        ClassPathLocationHandler() {
            super(StandardLocation.CLASS_PATH,
                    Option.CLASSPATH, Option.CP);
        }

        @Override
        Collection<Path> getPaths() {
            lazy();
            return searchPath;
        }

        @Override
        protected SearchPath computePath(String value) {
            String cp = value;

            // CLASSPATH environment variable when run from `javac'.
            if (cp == null) {
                cp = System.getProperty("env.class.path");
            }

            // If invoked via a java VM (not the javac launcher), use the
            // platform class path
            if (cp == null && System.getProperty("application.home") == null) {
                cp = System.getProperty("java.class.path");
            }

            // Default to current working directory.
            if (cp == null) {
                cp = ".";
            }

            return createPath().addFiles(cp);
        }

        @Override
        protected SearchPath createPath() {
            return new SearchPath()
                    .expandJarClassPaths(true) // Only search user jars for Class-Paths
                    .emptyPathDefault(Paths.get("."));  // Empty path elt ==> current directory
        }

        private void lazy() {
            if (searchPath == null) {
                setPaths(null);
            }
        }
    }

    /**
     * Custom subtype of LocationHandler for PLATFORM_CLASS_PATH.
     * Various options are supported for different components of the
     * platform class path.
     * Setting a value with setLocation overrides all existing option values.
     * Setting any option overrides any value set with setLocation, and
     * reverts to using default values for options that have not been set.
     * Setting -bootclasspath or -Xbootclasspath overrides any existing
     * value for -Xbootclasspath/p: and -Xbootclasspath/a:.
     */
    private class BootClassPathLocationHandler extends BasicLocationHandler {

        private Collection<Path> searchPath;
        final Map<Option, String> optionValues = new EnumMap<>(Option.class);

        /**
         * Is the bootclasspath the default?
         */
        private boolean isDefault;

        BootClassPathLocationHandler() {
            super(StandardLocation.PLATFORM_CLASS_PATH,
                    Option.BOOTCLASSPATH, Option.XBOOTCLASSPATH,
                    Option.XBOOTCLASSPATH_PREPEND,
                    Option.XBOOTCLASSPATH_APPEND,
                    Option.ENDORSEDDIRS, Option.DJAVA_ENDORSED_DIRS,
                    Option.EXTDIRS, Option.DJAVA_EXT_DIRS);
        }

        boolean isDefault() {
            lazy();
            return isDefault;
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option)) {
                return false;
            }

            option = canonicalize(option);
            optionValues.put(option, value);
            if (option == BOOTCLASSPATH) {
                optionValues.remove(XBOOTCLASSPATH_PREPEND);
                optionValues.remove(XBOOTCLASSPATH_APPEND);
            }
            searchPath = null;  // reset to "uninitialized"
            return true;
        }
        // where
        // TODO: would be better if option aliasing was handled at a higher
        // level
        private Option canonicalize(Option option) {
            switch (option) {
                case XBOOTCLASSPATH:
                    return Option.BOOTCLASSPATH;
                case DJAVA_ENDORSED_DIRS:
                    return Option.ENDORSEDDIRS;
                case DJAVA_EXT_DIRS:
                    return Option.EXTDIRS;
                default:
                    return option;
            }
        }

        @Override
        Collection<Path> getPaths() {
            lazy();
            return searchPath;
        }

        @Override
        void setPaths(Iterable<? extends Path> files) {
            if (files == null) {
                searchPath = null;  // reset to "uninitialized"
            } else {
                isDefault = false;
                SearchPath p = new SearchPath().addFiles(files, false);
                searchPath = Collections.unmodifiableCollection(p);
                optionValues.clear();
            }
        }

        SearchPath computePath() throws IOException {
            SearchPath path = new SearchPath();

            String bootclasspathOpt = optionValues.get(BOOTCLASSPATH);
            String endorseddirsOpt = optionValues.get(ENDORSEDDIRS);
            String extdirsOpt = optionValues.get(EXTDIRS);
            String xbootclasspathPrependOpt = optionValues.get(XBOOTCLASSPATH_PREPEND);
            String xbootclasspathAppendOpt = optionValues.get(XBOOTCLASSPATH_APPEND);
            path.addFiles(xbootclasspathPrependOpt);

            if (endorseddirsOpt != null) {
                path.addDirectories(endorseddirsOpt);
            } else {
                path.addDirectories(System.getProperty("java.endorsed.dirs"), false);
            }

            if (bootclasspathOpt != null) {
                path.addFiles(bootclasspathOpt);
            } else {
                // Standard system classes for this compiler's release.
                Collection<Path> systemClasses = systemClasses();
                if (systemClasses != null) {
                    path.addFiles(systemClasses, false);
                } else {
                    // fallback to the value of sun.boot.class.path
                    String files = System.getProperty("sun.boot.class.path");
                    path.addFiles(files, false);
                }
            }

            path.addFiles(xbootclasspathAppendOpt);

            // Strictly speaking, standard extensions are not bootstrap
            // classes, but we treat them identically, so we'll pretend
            // that they are.
            if (extdirsOpt != null) {
                path.addDirectories(extdirsOpt);
            } else {
                // Add lib/jfxrt.jar to the search path
               Path jfxrt = javaHome.resolve("lib/jfxrt.jar");
                if (Files.exists(jfxrt)) {
                    path.addFile(jfxrt, false);
                }
                path.addDirectories(System.getProperty("java.ext.dirs"), false);
            }

            isDefault =
                       (xbootclasspathPrependOpt == null)
                    && (bootclasspathOpt == null)
                    && (xbootclasspathAppendOpt == null);

            return path;
        }

        /**
         * Return a collection of files containing system classes.
         * Returns {@code null} if not running on a modular image.
         *
         * @throws UncheckedIOException if an I/O errors occurs
         */
        private Collection<Path> systemClasses() throws IOException {
            // Return "modules" jimage file if available
            if (Files.isRegularFile(thisSystemModules)) {
                return addAdditionalBootEntries(Collections.singleton(thisSystemModules));
            }

            // Exploded module image
            Path modules = javaHome.resolve("modules");
            if (Files.isDirectory(modules.resolve("java.base"))) {
                try (Stream<Path> listedModules = Files.list(modules)) {
                    return addAdditionalBootEntries(listedModules.collect(Collectors.toList()));
                }
            }

            // not a modular image that we know about
            return null;
        }

        //ensure bootclasspath prepends/appends are reflected in the systemClasses
        private Collection<Path> addAdditionalBootEntries(Collection<Path> modules) throws IOException {
            String files = System.getProperty("sun.boot.class.path");
            if (files == null)
                return modules;

            Set<Path> paths = new LinkedHashSet<>();

            // The JVM no longer supports -Xbootclasspath/p:, so any interesting
            // entries should be appended to the set of modules.

            paths.addAll(modules);

            for (String s : files.split(Pattern.quote(File.pathSeparator))) {
                paths.add(Paths.get(s));
            }

            return paths;
        }

        private void lazy() {
            if (searchPath == null) {
                try {
                searchPath = Collections.unmodifiableCollection(computePath());
                } catch (IOException e) {
                    // TODO: need better handling here, e.g. javac Abort?
                    throw new UncheckedIOException(e);
                }
            }
        }
    }

    /**
     * A LocationHander to represent modules found from a module-oriented
     * location such as MODULE_SOURCE_PATH, UPGRADE_MODULE_PATH,
     * SYSTEM_MODULES and MODULE_PATH.
     *
     * The Location can be specified to accept overriding classes from the
     * -Xpatch:dir parameter.
     */
    private class ModuleLocationHandler extends LocationHandler implements Location {
        protected final String name;
        protected final String moduleName;
        protected final Collection<Path> searchPath;
        protected final Collection<Path> searchPathWithOverrides;
        protected final boolean output;

        ModuleLocationHandler(String name, String moduleName, Collection<Path> searchPath,
                boolean output, boolean allowOverrides) {
            this.name = name;
            this.moduleName = moduleName;
            this.searchPath = searchPath;
            this.output = output;

            if (allowOverrides) {
                if (patchMap != null) {
                    SearchPath mPatch = patchMap.get(moduleName);
                    if (mPatch != null) {
                        SearchPath sp = new SearchPath();
                        sp.addAll(mPatch);
                        sp.addAll(searchPath);
                        searchPathWithOverrides = sp;
                    } else {
                        searchPathWithOverrides = searchPath;
                    }
                } else {
                     // for old style patch option; retained for transition
                    Set<Path> overrides = new LinkedHashSet<>();
                    if (moduleOverrideSearchPath != null) {
                       for (Path p: moduleOverrideSearchPath) {
                           Path o = p.resolve(moduleName);
                           if (Files.isDirectory(o)) {
                               overrides.add(o);
                           }
                       }
                    }

                    if (!overrides.isEmpty()) {
                        overrides.addAll(searchPath);
                        searchPathWithOverrides = overrides;
                    } else {
                        searchPathWithOverrides = searchPath;
                    }
                }
            } else {
                searchPathWithOverrides = searchPath;
            }
        }

        @Override // defined by Location
        public String getName() {
            return name;
        }

        @Override // defined by Location
        public boolean isOutputLocation() {
            return output;
        }

        @Override // defined by LocationHandler
        boolean handleOption(Option option, String value) {
            throw new UnsupportedOperationException();
        }

        @Override // defined by LocationHandler
        Collection<Path> getPaths() {
            // For now, we always return searchPathWithOverrides. This may differ from the
            // JVM behavior if there is a module-info.class to be found in the overriding
            // classes.
            return searchPathWithOverrides;
        }

        @Override // defined by LocationHandler
        void setPaths(Iterable<? extends Path> files) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override // defined by LocationHandler
        String inferModuleName() {
            return moduleName;
        }
    }

    /**
     * A LocationHandler for simple module-oriented search paths,
     * like UPGRADE_MODULE_PATH and MODULE_PATH.
     */
    private class ModulePathLocationHandler extends SimpleLocationHandler {
        ModulePathLocationHandler(Location location, Option... options) {
            super(location, options);
        }

        @Override
        public boolean handleOption(Option option, String value) {
            if (!options.contains(option)) {
                return false;
            }
            setPaths(value == null ? null : getPathEntries(value));
            return true;
        }

        @Override
        Iterable<Set<Location>> listModuleLocations() {
            if (searchPath == null)
                return Collections.emptyList();

            return () -> new ModulePathIterator();
        }

        @Override
        void setPaths(Iterable<? extends Path> paths) {
            if (paths != null) {
                for (Path p: paths) {
                    checkValidModulePathEntry(p);
                }
            }
            super.setPaths(paths);
        }

        private void checkValidModulePathEntry(Path p) {
            if (Files.isDirectory(p)) {
                // either an exploded module or a directory of modules
                return;
            }

            String name = p.getFileName().toString();
            int lastDot = name.lastIndexOf(".");
            if (lastDot > 0) {
                switch (name.substring(lastDot)) {
                    case ".jar":
                    case ".jmod":
                        return;
                }
            }
            throw new IllegalArgumentException(p.toString());
        }

        class ModulePathIterator implements Iterator<Set<Location>> {
            Iterator<Path> pathIter = searchPath.iterator();
            int pathIndex = 0;
            Set<Location> next = null;

            @Override
            public boolean hasNext() {
                if (next != null)
                    return true;

                while (next == null) {
                    if (pathIter.hasNext()) {
                        Path path = pathIter.next();
                        if (Files.isDirectory(path)) {
                            next = scanDirectory(path);
                        } else {
                            next = scanFile(path);
                        }
                        pathIndex++;
                    } else
                        return false;
                }
                return true;
            }

            @Override
            public Set<Location> next() {
                hasNext();
                if (next != null) {
                    Set<Location> result = next;
                    next = null;
                    return result;
                }
                throw new NoSuchElementException();
            }

            private Set<Location> scanDirectory(Path path) {
                Set<Path> paths = new LinkedHashSet<>();
                Path moduleInfoClass = null;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry: stream) {
                        if (entry.endsWith("module-info.class")) {
                            moduleInfoClass = entry;
                            break;  // no need to continue scanning
                        }
                        paths.add(entry);
                    }
                } catch (DirectoryIteratorException | IOException ignore) {
                    log.error(Errors.LocnCantReadDirectory(path));
                    return Collections.emptySet();
                }

                if (moduleInfoClass != null) {
                    // It's an exploded module directly on the module path.
                    // We can't infer module name from the directory name, so have to
                    // read module-info.class.
                    try {
                        String moduleName = readModuleName(moduleInfoClass);
                        String name = location.getName()
                                + "[" + pathIndex + ":" + moduleName + "]";
                        ModuleLocationHandler l = new ModuleLocationHandler(name, moduleName,
                                Collections.singleton(path), false, true);
                        return Collections.singleton(l);
                    } catch (ModuleNameReader.BadClassFile e) {
                        log.error(Errors.LocnBadModuleInfo(path));
                        return Collections.emptySet();
                    } catch (IOException e) {
                        log.error(Errors.LocnCantReadFile(path));
                        return Collections.emptySet();
                    }
                }

                // A directory of modules
                Set<Location> result = new LinkedHashSet<>();
                int index = 0;
                for (Path entry : paths) {
                    Pair<String,Path> module = inferModuleName(entry);
                    if (module == null) {
                        // diagnostic reported if necessary; skip to next
                        continue;
                    }
                    String moduleName = module.fst;
                    Path modulePath = module.snd;
                    String name = location.getName()
                            + "[" + pathIndex + "." + (index++) + ":" + moduleName + "]";
                    ModuleLocationHandler l = new ModuleLocationHandler(name, moduleName,
                            Collections.singleton(modulePath), false, true);
                    result.add(l);
                }
                return result;
            }

            private Set<Location> scanFile(Path path) {
                Pair<String,Path> module = inferModuleName(path);
                if (module == null) {
                    // diagnostic reported if necessary
                    return Collections.emptySet();
                }
                String moduleName = module.fst;
                Path modulePath = module.snd;
                String name = location.getName()
                        + "[" + pathIndex + ":" + moduleName + "]";
                ModuleLocationHandler l = new ModuleLocationHandler(name, moduleName,
                        Collections.singleton(modulePath), false, true);
                return Collections.singleton(l);
            }

            private Pair<String,Path> inferModuleName(Path p) {
                if (Files.isDirectory(p)) {
                    if (Files.exists(p.resolve("module-info.class"))) {
                        String name = p.getFileName().toString();
                        if (SourceVersion.isName(name))
                            return new Pair<>(name, p);
                    }
                    return null;
                }

                if (p.getFileName().toString().endsWith(".jar")) {
                    try (FileSystem fs = FileSystems.newFileSystem(p, null)) {
                        Path moduleInfoClass = fs.getPath("module-info.class");
                        if (Files.exists(moduleInfoClass)) {
                            String moduleName = readModuleName(moduleInfoClass);
                            return new Pair<>(moduleName, p);
                        }
                    } catch (ModuleNameReader.BadClassFile e) {
                        log.error(Errors.LocnBadModuleInfo(p));
                        return null;
                    } catch (IOException e) {
                        log.error(Errors.LocnCantReadFile(p));
                        return null;
                    }

                    //automatic module:
                    String fn = p.getFileName().toString();
                    //from ModulePath.deriveModuleDescriptor:

                    // drop .jar
                    String mn = fn.substring(0, fn.length()-4);

                    // find first occurrence of -${NUMBER}. or -${NUMBER}$
                    Matcher matcher = Pattern.compile("-(\\d+(\\.|$))").matcher(mn);
                    if (matcher.find()) {
                        int start = matcher.start();

                        mn = mn.substring(0, start);
                    }

                    // finally clean up the module name
                    mn =  mn.replaceAll("[^A-Za-z0-9]", ".")  // replace non-alphanumeric
                            .replaceAll("(\\.)(\\1)+", ".")   // collapse repeating dots
                            .replaceAll("^\\.", "")           // drop leading dots
                            .replaceAll("\\.$", "");          // drop trailing dots


                    if (!mn.isEmpty()) {
                        return new Pair<>(mn, p);
                    }

                    log.error(Errors.LocnCantGetModuleNameForJar(p));
                    return null;
                }

                if (p.getFileName().toString().endsWith(".jmod")) {
                    try {
                        FileSystem fs = fileSystems.get(p);
                        if (fs == null) {
                            URI uri = URI.create("jar:" + p.toUri());
                            fs = FileSystems.newFileSystem(uri, Collections.emptyMap(), null);
                            try {
                                Path moduleInfoClass = fs.getPath("classes/module-info.class");
                                String moduleName = readModuleName(moduleInfoClass);
                                Path modulePath = fs.getPath("classes");
                                fileSystems.put(p, fs);
                                closeables.add(fs);
                                fs = null; // prevent fs being closed in the finally clause
                                return new Pair<>(moduleName, modulePath);
                            } finally {
                                if (fs != null)
                                    fs.close();
                            }
                        }
                    } catch (ProviderNotFoundException e) {
                        // will be thrown if the file is not a valid zip file
                        log.error(Errors.LocnCantReadFile(p));
                        return null;
                    } catch (ModuleNameReader.BadClassFile e) {
                        log.error(Errors.LocnBadModuleInfo(p));
                    } catch (IOException e) {
                        log.error(Errors.LocnCantReadFile(p));
                        return null;
                    }
                }

                if (warn && false) {  // temp disable
                    log.warning(Warnings.LocnUnknownFileOnModulePath(p));
                }
                return null;
            }

            private String readModuleName(Path path) throws IOException, ModuleNameReader.BadClassFile {
                if (moduleNameReader == null)
                    moduleNameReader = new ModuleNameReader();
                return moduleNameReader.readModuleName(path);
            }
        }

    }

    private class ModuleSourcePathLocationHandler extends BasicLocationHandler {

        private Map<String, Location> moduleLocations;
        private Map<Path, Location> pathLocations;


        ModuleSourcePathLocationHandler() {
            super(StandardLocation.MODULE_SOURCE_PATH,
                    Option.MODULESOURCEPATH);
        }

        @Override
        boolean handleOption(Option option, String value) {
            init(value);
            return true;
        }

        void init(String value) {
            Collection<String> segments = new ArrayList<>();
            for (String s: value.split(File.pathSeparator)) {
                expandBraces(s, segments);
            }

            Map<String, Collection<Path>> map = new LinkedHashMap<>();
            final String MARKER = "*";
            for (String seg: segments) {
                int markStart = seg.indexOf(MARKER);
                if (markStart == -1) {
                    add(map, Paths.get(seg), null);
                } else {
                    if (markStart == 0 || !isSeparator(seg.charAt(markStart - 1))) {
                        throw new IllegalArgumentException("illegal use of " + MARKER + " in " + seg);
                    }
                    Path prefix = Paths.get(seg.substring(0, markStart - 1));
                    Path suffix;
                    int markEnd = markStart + MARKER.length();
                    if (markEnd == seg.length()) {
                        suffix = null;
                    } else if (!isSeparator(seg.charAt(markEnd))
                            || seg.indexOf(MARKER, markEnd) != -1) {
                        throw new IllegalArgumentException("illegal use of " + MARKER + " in " + seg);
                    } else {
                        suffix = Paths.get(seg.substring(markEnd + 1));
                    }
                    add(map, prefix, suffix);
                }
            }

            moduleLocations = new LinkedHashMap<>();
            pathLocations = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                String name = location.getName() + "[" + k + "]";
                ModuleLocationHandler h = new ModuleLocationHandler(name, k, v, false, false);
                moduleLocations.put(k, h);
                v.forEach(p -> pathLocations.put(normalize(p), h));
            });
        }

        private boolean isSeparator(char ch) {
            // allow both separators on Windows
            return (ch == File.separatorChar) || (ch == '/');
        }

        void add(Map<String, Collection<Path>> map, Path prefix, Path suffix) {
            if (!Files.isDirectory(prefix)) {
                if (warn) {
                    String key = Files.exists(prefix)
                            ? "dir.path.element.not.directory"
                            : "dir.path.element.not.found";
                    log.warning(Lint.LintCategory.PATH, key, prefix);
                }
                return;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(prefix, path -> Files.isDirectory(path))) {
                for (Path entry: stream) {
                    Path path = (suffix == null) ? entry : entry.resolve(suffix);
                    if (Files.isDirectory(path)) {
                        String name = entry.getFileName().toString();
                        Collection<Path> paths = map.get(name);
                        if (paths == null)
                            map.put(name, paths = new ArrayList<>());
                        paths.add(path);
                    }
                }
            } catch (IOException e) {
                // TODO? What to do?
                System.err.println(e);
            }
        }

        private void expandBraces(String value, Collection<String> results) {
            int depth = 0;
            int start = -1;
            String prefix = null;
            String suffix = null;
            for (int i = 0; i < value.length(); i++) {
                switch (value.charAt(i)) {
                    case '{':
                        depth++;
                        if (depth == 1) {
                            prefix = value.substring(0, i);
                            suffix = value.substring(getMatchingBrace(value, i) + 1);
                            start = i + 1;
                        }
                        break;

                    case ',':
                        if (depth == 1) {
                            String elem = value.substring(start, i);
                            expandBraces(prefix + elem + suffix, results);
                            start = i + 1;
                        }
                        break;

                    case '}':
                        switch (depth) {
                            case 0:
                                throw new IllegalArgumentException("mismatched braces");

                            case 1:
                                String elem = value.substring(start, i);
                                expandBraces(prefix + elem + suffix, results);
                                return;

                            default:
                                depth--;
                        }
                        break;
                }
            }
            if (depth > 0)
                throw new IllegalArgumentException("mismatched braces");
            results.add(value);
        }

        int getMatchingBrace(String value, int offset) {
            int depth = 1;
            for (int i = offset + 1; i < value.length(); i++) {
                switch (value.charAt(i)) {
                    case '{':
                        depth++;
                        break;

                    case '}':
                        if (--depth == 0)
                            return i;
                        break;
                }
            }
            throw new IllegalArgumentException("mismatched braces");
        }

        @Override
        boolean isSet() {
            return (moduleLocations != null);
        }

        @Override
        Collection<Path> getPaths() {
            throw new UnsupportedOperationException();
        }

        @Override
        void setPaths(Iterable<? extends Path> files) throws IOException {
            throw new UnsupportedOperationException();
        }

        @Override
        Location getModuleLocation(String name) {
            return (moduleLocations == null) ? null : moduleLocations.get(name);
        }

        @Override
        Location getModuleLocation(Path dir) {
            return (pathLocations == null) ? null : pathLocations.get(dir);
        }

        @Override
        Iterable<Set<Location>> listModuleLocations() {
            if (moduleLocations == null)
                return Collections.emptySet();
            Set<Location> locns = new LinkedHashSet<>();
            moduleLocations.forEach((k, v) -> locns.add(v));
            return Collections.singleton(locns);
        }

    }

    private class SystemModulesLocationHandler extends BasicLocationHandler {
        private Path javaHome;
        private Path modules;
        private Map<String, ModuleLocationHandler> systemModules;

        SystemModulesLocationHandler() {
            super(StandardLocation.SYSTEM_MODULES, Option.SYSTEM);
            javaHome = Paths.get(System.getProperty("java.home"));
        }

        @Override
        boolean handleOption(Option option, String value) {
            if (!options.contains(option)) {
                return false;
            }

            if (value == null) {
                javaHome = Paths.get(System.getProperty("java.home"));
            } else if (value.equals("none")) {
                javaHome = null;
            } else {
                update(Paths.get(value));
            }

            modules = null;
            return true;
        }

        @Override
        Collection<Path> getPaths() {
            return (javaHome == null) ? null : Collections.singleton(javaHome);
        }

        @Override
        void setPaths(Iterable<? extends Path> files) throws IOException {
            if (files == null) {
                javaHome = null;
            } else {
                Iterator<? extends Path> pathIter = files.iterator();
                if (!pathIter.hasNext()) {
                    throw new IllegalArgumentException("empty path for directory"); // TODO: FIXME
                }
                Path dir = pathIter.next();
                if (pathIter.hasNext()) {
                    throw new IllegalArgumentException("path too long for directory"); // TODO: FIXME
                }
                if (!Files.exists(dir)) {
                    throw new FileNotFoundException(dir + ": does not exist");
                } else if (!Files.isDirectory(dir)) {
                    throw new IOException(dir + ": not a directory");
                }
                update(dir);
            }
        }

        private void update(Path p) {
            if (!isCurrentPlatform(p) && !Files.exists(p.resolve("jrt-fs.jar")) && !Files.exists(javaHome.resolve("modules")))
                throw new IllegalArgumentException(p.toString());
            javaHome = p;
            modules = null;
        }

        private boolean isCurrentPlatform(Path p) {
            Path jh = Paths.get(System.getProperty("java.home"));
            try {
                return Files.isSameFile(p, jh);
            } catch (IOException ex) {
                throw new IllegalArgumentException(p.toString(), ex);
            }
        }

        @Override
        Location getModuleLocation(String name) throws IOException {
            initSystemModules();
            return systemModules.get(name);
        }

        @Override
        Iterable<Set<Location>> listModuleLocations() throws IOException {
            initSystemModules();
            Set<Location> locns = new LinkedHashSet<>();
            for (Location l: systemModules.values())
                locns.add(l);
            return Collections.singleton(locns);
        }

        private void initSystemModules() throws IOException {
            if (systemModules != null) {
                return;
            }

            if (javaHome == null) {
                systemModules = Collections.emptyMap();
                return;
            }

            if (modules == null) {
                try {
                    URI jrtURI = URI.create("jrt:/");
                    FileSystem jrtfs;

                    if (isCurrentPlatform(javaHome)) {
                        jrtfs = FileSystems.getFileSystem(jrtURI);
                    } else {
                        try {
                            Map<String, String> attrMap =
                                    Collections.singletonMap("java.home", javaHome.toString());
                            jrtfs = FileSystems.newFileSystem(jrtURI, attrMap);
                        } catch (ProviderNotFoundException ex) {
                            URL javaHomeURL = javaHome.resolve("jrt-fs.jar").toUri().toURL();
                            ClassLoader currentLoader = Locations.class.getClassLoader();
                            URLClassLoader fsLoader =
                                    new URLClassLoader(new URL[] {javaHomeURL}, currentLoader);

                            jrtfs = FileSystems.newFileSystem(jrtURI, Collections.emptyMap(), fsLoader);

                            closeables.add(fsLoader);
                        }

                        closeables.add(jrtfs);
                    }

                    modules = jrtfs.getPath("/modules");
                } catch (FileSystemNotFoundException | ProviderNotFoundException e) {
                    modules = javaHome.resolve("modules");
                    if (!Files.exists(modules))
                        throw new IOException("can't find system classes", e);
                }
            }

            systemModules = new LinkedHashMap<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modules, Files::isDirectory)) {
                for (Path entry : stream) {
                    String moduleName = entry.getFileName().toString();
                    String name = location.getName() + "[" + moduleName + "]";
                    ModuleLocationHandler h = new ModuleLocationHandler(name, moduleName,
                            Collections.singleton(entry), false, true);
                    systemModules.put(moduleName, h);
                }
            }
        }
    }

    Map<Location, LocationHandler> handlersForLocation;
    Map<Option, LocationHandler> handlersForOption;

    void initHandlers() {
        handlersForLocation = new HashMap<>();
        handlersForOption = new EnumMap<>(Option.class);

        BasicLocationHandler[] handlers = {
            new BootClassPathLocationHandler(),
            new ClassPathLocationHandler(),
            new SimpleLocationHandler(StandardLocation.SOURCE_PATH, Option.SOURCEPATH),
            new SimpleLocationHandler(StandardLocation.ANNOTATION_PROCESSOR_PATH, Option.PROCESSORPATH),
            new SimpleLocationHandler(StandardLocation.ANNOTATION_PROCESSOR_MODULE_PATH, Option.PROCESSORMODULEPATH),
            new OutputLocationHandler(StandardLocation.CLASS_OUTPUT, Option.D),
            new OutputLocationHandler(StandardLocation.SOURCE_OUTPUT, Option.S),
            new OutputLocationHandler(StandardLocation.NATIVE_HEADER_OUTPUT, Option.H),
            new ModuleSourcePathLocationHandler(),
            // TODO: should UPGRADE_MODULE_PATH be merged with SYSTEM_MODULES?
            new ModulePathLocationHandler(StandardLocation.UPGRADE_MODULE_PATH, Option.UPGRADEMODULEPATH),
            new ModulePathLocationHandler(StandardLocation.MODULE_PATH, Option.MODULEPATH, Option.MP),
            new SystemModulesLocationHandler(),
        };

        for (BasicLocationHandler h : handlers) {
            handlersForLocation.put(h.location, h);
            for (Option o : h.options) {
                handlersForOption.put(o, h);
            }
        }
    }

    private SearchPath moduleOverrideSearchPath; // for old style patch option; retained for transition
    private Map<String, SearchPath> patchMap;

    boolean handleOption(Option option, String value) {
        switch (option) {
            case XPATCH:
                if (value.contains("=")) {
                    Map<String, SearchPath> map = new LinkedHashMap<>();
                    for (String entry: value.split(",")) {
                        int eq = entry.indexOf('=');
                        if (eq > 0) {
                            String mName = entry.substring(0, eq);
                            SearchPath mPatchPath = new SearchPath()
                                    .addFiles(entry.substring(eq + 1));
                            boolean ok = true;
                            for (Path p: mPatchPath) {
                                Path mi = p.resolve("module-info.class");
                                if (Files.exists(mi)) {
                                    log.error(Errors.LocnModuleInfoNotAllowedOnPatchPath(mi));
                                    ok = false;
                                }
                            }
                            if (ok && !mPatchPath.isEmpty()) {
                                map.computeIfAbsent(mName, (_x) -> new SearchPath())
                                        .addAll(mPatchPath);
                            }
                        } else {
                            log.error(Errors.LocnInvalidArgForXpatch(entry));
                        }
                    }
                    patchMap = map;
                } else {
                     // for old style patch option; retained for transition
                    moduleOverrideSearchPath = new SearchPath().addFiles(value);
                }
                return true;
            default:
                LocationHandler h = handlersForOption.get(option);
                return (h == null ? false : h.handleOption(option, value));
        }
    }

    boolean hasLocation(Location location) {
        LocationHandler h = getHandler(location);
        return (h == null ? false : h.isSet());
    }

    Collection<Path> getLocation(Location location) {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.getPaths());
    }

    Path getOutputLocation(Location location) {
        if (!location.isOutputLocation()) {
            throw new IllegalArgumentException();
        }
        LocationHandler h = getHandler(location);
        return ((OutputLocationHandler) h).outputDir;
    }

    void setLocation(Location location, Iterable<? extends Path> files) throws IOException {
        LocationHandler h = getHandler(location);
        if (h == null) {
            if (location.isOutputLocation()) {
                h = new OutputLocationHandler(location);
            } else {
                h = new SimpleLocationHandler(location);
            }
            handlersForLocation.put(location, h);
        }
        h.setPaths(files);
    }

    Location getModuleLocation(Location location, String name) throws IOException {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.getModuleLocation(name));
    }

    Location getModuleLocation(Location location, Path dir) {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.getModuleLocation(dir));
    }

    String inferModuleName(Location location) {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.inferModuleName());
    }

    Iterable<Set<Location>> listModuleLocations(Location location) throws IOException {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.listModuleLocations());
    }

    protected LocationHandler getHandler(Location location) {
        Objects.requireNonNull(location);
        return (location instanceof LocationHandler)
                ? (LocationHandler) location
                : handlersForLocation.get(location);
    }

    /**
     * Is this the name of an archive file?
     */
    private boolean isArchive(Path file) {
        String n = StringUtils.toLowerCase(file.getFileName().toString());
        return fsInfo.isFile(file)
                && (n.endsWith(".jar") || n.endsWith(".zip"));
    }

    static Path normalize(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

}
