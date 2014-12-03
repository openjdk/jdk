/*
 * Copyright (c) 2003, 2014, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import javax.tools.JavaFileManager;
import javax.tools.JavaFileManager.Location;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.main.Option;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.StringUtils;

import static javax.tools.StandardLocation.CLASS_PATH;
import static javax.tools.StandardLocation.PLATFORM_CLASS_PATH;
import static javax.tools.StandardLocation.SOURCE_PATH;

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

    // Used by Locations(for now) to indicate that the PLATFORM_CLASS_PATH
    // should use the jrt: file system.
    // When Locations has been converted to use java.nio.file.Path,
    // Locations can use Paths.get(URI.create("jrt:"))
    static final File JRT_MARKER_FILE = new File("JRT_MARKER_FILE");

    public Locations() {
        initHandlers();
    }

    // could replace Lint by "boolean warn"
    public void update(Log log, Lint lint, FSInfo fsInfo) {
        this.log = log;
        warn = lint.isEnabled(Lint.LintCategory.PATH);
        this.fsInfo = fsInfo;
    }

    public Collection<File> bootClassPath() {
        return getLocation(PLATFORM_CLASS_PATH);
    }

    public boolean isDefaultBootClassPath() {
        BootClassPathLocationHandler h
                = (BootClassPathLocationHandler) getHandler(PLATFORM_CLASS_PATH);
        return h.isDefault();
    }

    public Collection<File> userClassPath() {
        return getLocation(CLASS_PATH);
    }

    public Collection<File> sourcePath() {
        Collection<File> p = getLocation(SOURCE_PATH);
        // TODO: this should be handled by the LocationHandler
        return p == null || p.isEmpty() ? null : p;
    }

    /**
     * Split a path into its elements. Empty path elements will be ignored.
     *
     * @param path The path to be split
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path) {
        return getPathEntries(path, null);
    }

    /**
     * Split a path into its elements. If emptyPathDefault is not null, all empty elements in the
     * path, including empty elements at either end of the path, will be replaced with the value of
     * emptyPathDefault.
     *
     * @param path The path to be split
     * @param emptyPathDefault The value to substitute for empty path elements, or null, to ignore
     * empty path elements
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path, File emptyPathDefault) {
        ListBuffer<File> entries = new ListBuffer<>();
        int start = 0;
        while (start <= path.length()) {
            int sep = path.indexOf(File.pathSeparatorChar, start);
            if (sep == -1) {
                sep = path.length();
            }
            if (start < sep) {
                entries.add(new File(path.substring(start, sep)));
            } else if (emptyPathDefault != null) {
                entries.add(emptyPathDefault);
            }
            start = sep + 1;
        }
        return entries;
    }

    /**
     * Utility class to help evaluate a path option. Duplicate entries are ignored, jar class paths
     * can be expanded.
     */
    private class SearchPath extends LinkedHashSet<File> {

        private static final long serialVersionUID = 0;

        private boolean expandJarClassPaths = false;
        private final Set<File> canonicalValues = new HashSet<>();

        public SearchPath expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        /**
         * What to use when path element is the empty string
         */
        private File emptyPathDefault = null;

        public SearchPath emptyPathDefault(File x) {
            emptyPathDefault = x;
            return this;
        }

        public SearchPath addDirectories(String dirs, boolean warn) {
            boolean prev = expandJarClassPaths;
            expandJarClassPaths = true;
            try {
                if (dirs != null) {
                    for (File dir : getPathEntries(dirs)) {
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

        private void addDirectory(File dir, boolean warn) {
            if (!dir.isDirectory()) {
                if (warn) {
                    log.warning(Lint.LintCategory.PATH,
                            "dir.path.element.not.found", dir);
                }
                return;
            }

            File[] files = dir.listFiles();
            if (files == null) {
                return;
            }

            for (File direntry : files) {
                if (isArchive(direntry)) {
                    addFile(direntry, warn);
                }
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

        public SearchPath addFiles(Iterable<? extends File> files, boolean warn) {
            if (files != null) {
                for (File file : files) {
                    addFile(file, warn);
                }
            }
            return this;
        }

        public SearchPath addFiles(Iterable<? extends File> files) {
            return addFiles(files, warn);
        }

        public void addFile(File file, boolean warn) {
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

            File canonFile = fsInfo.getCanonicalFile(file);
            if (canonicalValues.contains(canonFile)) {
                /* Discard duplicates and avoid infinite recursion */
                return;
            }

            if (fsInfo.isFile(file)) {
                /* File is an ordinary file. */
                if (!isArchive(file) && !file.getName().endsWith(".jimage")) {
                    /* Not a recognized extension; open it to see if
                     it looks like a valid zip file. */
                    try {
                        ZipFile z = new ZipFile(file);
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

            if (expandJarClassPaths && fsInfo.isFile(file) && !file.getName().endsWith(".jimage")) {
                addJarClassPath(file, warn);
            }
        }

        // Adds referenced classpath elements from a jar's Class-Path
        // Manifest entry.  In some future release, we may want to
        // update this code to recognize URLs rather than simple
        // filenames, but if we do, we should redo all path-related code.
        private void addJarClassPath(File jarFile, boolean warn) {
            try {
                for (File f : fsInfo.getJarClassPath(jarFile)) {
                    addFile(f, warn);
                }
            } catch (IOException e) {
                log.error("error.reading.file", jarFile, JavacFileManager.getMessage(e));
            }
        }
    }

    /**
     * Base class for handling support for the representation of Locations. Implementations are
     * responsible for handling the interactions between the command line options for a location,
     * and API access via setLocation.
     *
     * @see #initHandlers
     * @see #getHandler
     */
    protected abstract class LocationHandler {

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
        protected LocationHandler(Location location, Option... options) {
            this.location = location;
            this.options = options.length == 0
                    ? EnumSet.noneOf(Option.class)
                    : EnumSet.copyOf(Arrays.asList(options));
        }

        /**
         * @see JavaFileManager#handleOption
         */
        abstract boolean handleOption(Option option, String value);

        /**
         * @see StandardJavaFileManager#getLocation
         */
        abstract Collection<File> getLocation();

        /**
         * @see StandardJavaFileManager#setLocation
         */
        abstract void setLocation(Iterable<? extends File> files) throws IOException;
    }

    /**
     * General purpose implementation for output locations, such as -d/CLASS_OUTPUT and
     * -s/SOURCE_OUTPUT. All options are treated as equivalent (i.e. aliases.) The value is a single
     * file, possibly null.
     */
    private class OutputLocationHandler extends LocationHandler {

        private File outputDir;

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
            outputDir = (value == null) ? null : new File(value);
            return true;
        }

        @Override
        Collection<File> getLocation() {
            return (outputDir == null) ? null : Collections.singleton(outputDir);
        }

        @Override
        void setLocation(Iterable<? extends File> files) throws IOException {
            if (files == null) {
                outputDir = null;
            } else {
                Iterator<? extends File> pathIter = files.iterator();
                if (!pathIter.hasNext()) {
                    throw new IllegalArgumentException("empty path for directory");
                }
                File dir = pathIter.next();
                if (pathIter.hasNext()) {
                    throw new IllegalArgumentException("path too long for directory");
                }
                if (!dir.exists()) {
                    throw new FileNotFoundException(dir + ": does not exist");
                } else if (!dir.isDirectory()) {
                    throw new IOException(dir + ": not a directory");
                }
                outputDir = dir;
            }
        }
    }

    /**
     * General purpose implementation for search path locations, such as -sourcepath/SOURCE_PATH and
     * -processorPath/ANNOTATION_PROCESS_PATH. All options are treated as equivalent (i.e. aliases.)
     * The value is an ordered set of files and/or directories.
     */
    private class SimpleLocationHandler extends LocationHandler {

        protected Collection<File> searchPath;

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
        Collection<File> getLocation() {
            return searchPath;
        }

        @Override
        void setLocation(Iterable<? extends File> files) {
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
     * Subtype of SimpleLocationHandler for -classpath/CLASS_PATH. If no value is given, a default
     * is provided, based on system properties and other values.
     */
    private class ClassPathLocationHandler extends SimpleLocationHandler {

        ClassPathLocationHandler() {
            super(StandardLocation.CLASS_PATH,
                    Option.CLASSPATH, Option.CP);
        }

        @Override
        Collection<File> getLocation() {
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
                    .emptyPathDefault(new File("."));  // Empty path elt ==> current directory
        }

        private void lazy() {
            if (searchPath == null) {
                setLocation(null);
            }
        }
    }

    /**
     * Custom subtype of LocationHandler for PLATFORM_CLASS_PATH. Various options are supported for
     * different components of the platform class path. Setting a value with setLocation overrides
     * all existing option values. Setting any option overrides any value set with setLocation, and
     * reverts to using default values for options that have not been set. Setting -bootclasspath or
     * -Xbootclasspath overrides any existing value for -Xbootclasspath/p: and -Xbootclasspath/a:.
     */
    private class BootClassPathLocationHandler extends LocationHandler {

        private Collection<File> searchPath;
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
        Collection<File> getLocation() {
            lazy();
            return searchPath;
        }

        @Override
        void setLocation(Iterable<? extends File> files) {
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
            String java_home = System.getProperty("java.home");

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
                Collection<File> systemClasses = systemClasses(java_home);
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
                File jfxrt = new File(new File(java_home, "lib"), "jfxrt.jar");
                if (jfxrt.exists()) {
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
        private Collection<File> systemClasses(String java_home) throws IOException {
            // Return .jimage files if available
            Path libModules = Paths.get(java_home, "lib", "modules");
            if (Files.exists(libModules)) {
                try (Stream<Path> files = Files.list(libModules)) {
                    boolean haveJImageFiles =
                            files.anyMatch(f -> f.getFileName().toString().endsWith(".jimage"));
                    if (haveJImageFiles) {
                        return Collections.singleton(JRT_MARKER_FILE);
                    }
                }
            }

            // Temporary: if no .jimage files, return individual modules
            if (Files.exists(libModules.resolve("java.base"))) {
                return Files.list(libModules)
                            .map(d -> d.resolve("classes"))
                            .map(Path::toFile)
                            .collect(Collectors.toList());
            }

            // Exploded module image
            Path modules = Paths.get(java_home, "modules");
            if (Files.isDirectory(modules.resolve("java.base"))) {
                return Files.list(modules)
                            .map(Path::toFile)
                            .collect(Collectors.toList());
            }

            // not a modular image that we know about
            return null;
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

    Map<Location, LocationHandler> handlersForLocation;
    Map<Option, LocationHandler> handlersForOption;

    void initHandlers() {
        handlersForLocation = new HashMap<>();
        handlersForOption = new EnumMap<>(Option.class);

        LocationHandler[] handlers = {
            new BootClassPathLocationHandler(),
            new ClassPathLocationHandler(),
            new SimpleLocationHandler(StandardLocation.SOURCE_PATH, Option.SOURCEPATH),
            new SimpleLocationHandler(StandardLocation.ANNOTATION_PROCESSOR_PATH, Option.PROCESSORPATH),
            new OutputLocationHandler((StandardLocation.CLASS_OUTPUT), Option.D),
            new OutputLocationHandler((StandardLocation.SOURCE_OUTPUT), Option.S),
            new OutputLocationHandler((StandardLocation.NATIVE_HEADER_OUTPUT), Option.H)
        };

        for (LocationHandler h : handlers) {
            handlersForLocation.put(h.location, h);
            for (Option o : h.options) {
                handlersForOption.put(o, h);
            }
        }
    }

    public boolean handleOption(Option option, String value) {
        LocationHandler h = handlersForOption.get(option);
        return (h == null ? false : h.handleOption(option, value));
    }

    Collection<File> getLocation(Location location) {
        LocationHandler h = getHandler(location);
        return (h == null ? null : h.getLocation());
    }

    File getOutputLocation(Location location) {
        if (!location.isOutputLocation()) {
            throw new IllegalArgumentException();
        }
        LocationHandler h = getHandler(location);
        return ((OutputLocationHandler) h).outputDir;
    }

    void setLocation(Location location, Iterable<? extends File> files) throws IOException {
        LocationHandler h = getHandler(location);
        if (h == null) {
            if (location.isOutputLocation()) {
                h = new OutputLocationHandler(location);
            } else {
                h = new SimpleLocationHandler(location);
            }
            handlersForLocation.put(location, h);
        }
        h.setLocation(files);
    }

    protected LocationHandler getHandler(Location location) {
        location.getClass(); // null check
        return handlersForLocation.get(location);
    }

    /**
     * Is this the name of an archive file?
     */
    private boolean isArchive(File file) {
        String n = StringUtils.toLowerCase(file.getName());
        return fsInfo.isFile(file)
                && (n.endsWith(".jar") || n.endsWith(".zip"));
    }

    /**
     * Utility method for converting a search path string to an array of directory and JAR file
     * URLs.
     *
     * Note that this method is called by apt and the DocletInvoker.
     *
     * @param path the search path string
     * @return the resulting array of directory and JAR file URLs
     */
    public static URL[] pathToURLs(String path) {
        StringTokenizer st = new StringTokenizer(path, File.pathSeparator);
        URL[] urls = new URL[st.countTokens()];
        int count = 0;
        while (st.hasMoreTokens()) {
            URL url = fileToURL(new File(st.nextToken()));
            if (url != null) {
                urls[count++] = url;
            }
        }
        urls = Arrays.copyOf(urls, count);
        return urls;
    }

    /**
     * Returns the directory or JAR file URL corresponding to the specified local file name.
     *
     * @param file the File object
     * @return the resulting directory or JAR file URL, or null if unknown
     */
    private static URL fileToURL(File file) {
        String name;
        try {
            name = file.getCanonicalPath();
        } catch (IOException e) {
            name = file.getAbsolutePath();
        }
        name = name.replace(File.separatorChar, '/');
        if (!name.startsWith("/")) {
            name = "/" + name;
        }
        // If the file does not exist, then assume that it's a directory
        if (!file.isFile()) {
            name = name + "/";
        }
        try {
            return new URL("file", "", name);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(file.toString());
        }
    }
}
