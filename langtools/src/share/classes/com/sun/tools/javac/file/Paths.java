/*
 * Copyright 2003-2008 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.zip.ZipFile;
import javax.tools.JavaFileManager.Location;

import com.sun.tools.javac.code.Lint;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.ListBuffer;
import com.sun.tools.javac.util.Log;
import com.sun.tools.javac.util.Options;

import static javax.tools.StandardLocation.*;
import static com.sun.tools.javac.main.OptionName.*;

/** This class converts command line arguments, environment variables
 *  and system properties (in File.pathSeparator-separated String form)
 *  into a boot class path, user class path, and source path (in
 *  Collection<String> form).
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Paths {

    /** The context key for the todo list */
    protected static final Context.Key<Paths> pathsKey =
        new Context.Key<Paths>();

    /** Get the Paths instance for this context.
     *  @param context the context
     *  @return the Paths instance for this context
     */
    static Paths instance(Context context) {
        Paths instance = context.get(pathsKey);
        if (instance == null)
            instance = new Paths(context);
        return instance;
    }

    /** The log to use for warning output */
    private Log log;

    /** Collection of command-line options */
    private Options options;

    /** Handler for -Xlint options */
    private Lint lint;

    /** Access to (possibly cached) file info */
    private FSInfo fsInfo;

    protected Paths(Context context) {
        context.put(pathsKey, this);
        pathsForLocation = new HashMap<Location,Path>(16);
        setContext(context);
    }

    void setContext(Context context) {
        log = Log.instance(context);
        options = Options.instance(context);
        lint = Lint.instance(context);
        fsInfo = FSInfo.instance(context);
    }

    /** Whether to warn about non-existent path elements */
    private boolean warn;

    private Map<Location, Path> pathsForLocation;

    private boolean inited = false; // TODO? caching bad?

    /**
     * rt.jar as found on the default bootclass path.  If the user specified a
     * bootclasspath, null is used.
     */
    private File bootClassPathRtJar = null;

    Path getPathForLocation(Location location) {
        Path path = pathsForLocation.get(location);
        if (path == null)
            setPathForLocation(location, null);
        return pathsForLocation.get(location);
    }

    void setPathForLocation(Location location, Iterable<? extends File> path) {
        // TODO? if (inited) throw new IllegalStateException
        // TODO: otherwise reset sourceSearchPath, classSearchPath as needed
        Path p;
        if (path == null) {
            if (location == CLASS_PATH)
                p = computeUserClassPath();
            else if (location == PLATFORM_CLASS_PATH)
                p = computeBootClassPath();
            else if (location == ANNOTATION_PROCESSOR_PATH)
                p = computeAnnotationProcessorPath();
            else if (location == SOURCE_PATH)
                p = computeSourcePath();
            else
                // no defaults for other paths
                p = null;
        } else {
            p = new Path();
            for (File f: path)
                p.addFile(f, warn); // TODO: is use of warn appropriate?
        }
        pathsForLocation.put(location, p);
    }

    protected void lazy() {
        if (!inited) {
            warn = lint.isEnabled(Lint.LintCategory.PATH);

            pathsForLocation.put(PLATFORM_CLASS_PATH, computeBootClassPath());
            pathsForLocation.put(CLASS_PATH, computeUserClassPath());
            pathsForLocation.put(SOURCE_PATH, computeSourcePath());

            inited = true;
        }
    }

    public Collection<File> bootClassPath() {
        lazy();
        return Collections.unmodifiableCollection(getPathForLocation(PLATFORM_CLASS_PATH));
    }
    public Collection<File> userClassPath() {
        lazy();
        return Collections.unmodifiableCollection(getPathForLocation(CLASS_PATH));
    }
    public Collection<File> sourcePath() {
        lazy();
        Path p = getPathForLocation(SOURCE_PATH);
        return p == null || p.size() == 0
            ? null
            : Collections.unmodifiableCollection(p);
    }

    boolean isBootClassPathRtJar(File file) {
        return file.equals(bootClassPathRtJar);
    }

    /**
     * Split a path into its elements. Empty path elements will be ignored.
     * @param path The path to be split
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path) {
        return getPathEntries(path, null);
    }

    /**
     * Split a path into its elements. If emptyPathDefault is not null, all
     * empty elements in the path, including empty elements at either end of
     * the path, will be replaced with the value of emptyPathDefault.
     * @param path The path to be split
     * @param emptyPathDefault The value to substitute for empty path elements,
     *  or null, to ignore empty path elements
     * @return The elements of the path
     */
    private static Iterable<File> getPathEntries(String path, File emptyPathDefault) {
        ListBuffer<File> entries = new ListBuffer<File>();
        int start = 0;
        while (start <= path.length()) {
            int sep = path.indexOf(File.pathSeparatorChar, start);
            if (sep == -1)
                sep = path.length();
            if (start < sep)
                entries.add(new File(path.substring(start, sep)));
            else if (emptyPathDefault != null)
                entries.add(emptyPathDefault);
            start = sep + 1;
        }
        return entries;
    }

    private class Path extends LinkedHashSet<File> {
        private static final long serialVersionUID = 0;

        private boolean expandJarClassPaths = false;
        private Set<File> canonicalValues = new HashSet<File>();

        public Path expandJarClassPaths(boolean x) {
            expandJarClassPaths = x;
            return this;
        }

        /** What to use when path element is the empty string */
        private File emptyPathDefault = null;

        public Path emptyPathDefault(File x) {
            emptyPathDefault = x;
            return this;
        }

        public Path() { super(); }

        public Path addDirectories(String dirs, boolean warn) {
            if (dirs != null)
                for (File dir : getPathEntries(dirs))
                    addDirectory(dir, warn);
            return this;
        }

        public Path addDirectories(String dirs) {
            return addDirectories(dirs, warn);
        }

        private void addDirectory(File dir, boolean warn) {
            if (!dir.isDirectory()) {
                if (warn)
                    log.warning("dir.path.element.not.found", dir);
                return;
            }

            File[] files = dir.listFiles();
            if (files == null)
                return;

            for (File direntry : files) {
                if (isArchive(direntry))
                    addFile(direntry, warn);
            }
        }

        public Path addFiles(String files, boolean warn) {
            if (files != null)
                for (File file : getPathEntries(files, emptyPathDefault))
                    addFile(file, warn);
            return this;
        }

        public Path addFiles(String files) {
            return addFiles(files, warn);
        }

        public void addFile(File file, boolean warn) {
            File canonFile = fsInfo.getCanonicalFile(file);
            if (contains(file) || canonicalValues.contains(canonFile)) {
                /* Discard duplicates and avoid infinite recursion */
                return;
            }

            if (! fsInfo.exists(file)) {
                /* No such file or directory exists */
                if (warn)
                    log.warning("path.element.not.found", file);
            } else if (fsInfo.isFile(file)) {
                /* File is an ordinary file. */
                if (!isArchive(file)) {
                    /* Not a recognized extension; open it to see if
                     it looks like a valid zip file. */
                    try {
                        ZipFile z = new ZipFile(file);
                        z.close();
                        if (warn)
                            log.warning("unexpected.archive.file", file);
                    } catch (IOException e) {
                        // FIXME: include e.getLocalizedMessage in warning
                        if (warn)
                            log.warning("invalid.archive.file", file);
                        return;
                    }
                }
            }

            /* Now what we have left is either a directory or a file name
               confirming to archive naming convention */
            super.add(file);
            canonicalValues.add(canonFile);

            if (expandJarClassPaths && fsInfo.exists(file) && fsInfo.isFile(file))
                addJarClassPath(file, warn);
        }

        // Adds referenced classpath elements from a jar's Class-Path
        // Manifest entry.  In some future release, we may want to
        // update this code to recognize URLs rather than simple
        // filenames, but if we do, we should redo all path-related code.
        private void addJarClassPath(File jarFile, boolean warn) {
            try {
                for (File f: fsInfo.getJarClassPath(jarFile)) {
                    addFile(f, warn);
                }
            } catch (IOException e) {
                log.error("error.reading.file", jarFile, e.getLocalizedMessage());
            }
        }
    }

    private Path computeBootClassPath() {
        bootClassPathRtJar = null;
        String optionValue;
        Path path = new Path();

        path.addFiles(options.get(XBOOTCLASSPATH_PREPEND));

        if ((optionValue = options.get(ENDORSEDDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.endorsed.dirs"), false);

        if ((optionValue = options.get(BOOTCLASSPATH)) != null) {
            path.addFiles(optionValue);
        } else {
            // Standard system classes for this compiler's release.
            String files = System.getProperty("sun.boot.class.path");
            path.addFiles(files, false);
            File rt_jar = new File("rt.jar");
            for (File file : getPathEntries(files)) {
                if (new File(file.getName()).equals(rt_jar))
                    bootClassPathRtJar = file;
            }
        }

        path.addFiles(options.get(XBOOTCLASSPATH_APPEND));

        // Strictly speaking, standard extensions are not bootstrap
        // classes, but we treat them identically, so we'll pretend
        // that they are.
        if ((optionValue = options.get(EXTDIRS)) != null)
            path.addDirectories(optionValue);
        else
            path.addDirectories(System.getProperty("java.ext.dirs"), false);

        return path;
    }

    private Path computeUserClassPath() {
        String cp = options.get(CLASSPATH);

        // CLASSPATH environment variable when run from `javac'.
        if (cp == null) cp = System.getProperty("env.class.path");

        // If invoked via a java VM (not the javac launcher), use the
        // platform class path
        if (cp == null && System.getProperty("application.home") == null)
            cp = System.getProperty("java.class.path");

        // Default to current working directory.
        if (cp == null) cp = ".";

        return new Path()
            .expandJarClassPaths(true)        // Only search user jars for Class-Paths
            .emptyPathDefault(new File("."))  // Empty path elt ==> current directory
            .addFiles(cp);
    }

    private Path computeSourcePath() {
        String sourcePathArg = options.get(SOURCEPATH);
        if (sourcePathArg == null)
            return null;

        return new Path().addFiles(sourcePathArg);
    }

    private Path computeAnnotationProcessorPath() {
        String processorPathArg = options.get(PROCESSORPATH);
        if (processorPathArg == null)
            return null;

        return new Path().addFiles(processorPathArg);
    }

    /** The actual effective locations searched for sources */
    private Path sourceSearchPath;

    public Collection<File> sourceSearchPath() {
        if (sourceSearchPath == null) {
            lazy();
            Path sourcePath = getPathForLocation(SOURCE_PATH);
            Path userClassPath = getPathForLocation(CLASS_PATH);
            sourceSearchPath = sourcePath != null ? sourcePath : userClassPath;
        }
        return Collections.unmodifiableCollection(sourceSearchPath);
    }

    /** The actual effective locations searched for classes */
    private Path classSearchPath;

    public Collection<File> classSearchPath() {
        if (classSearchPath == null) {
            lazy();
            Path bootClassPath = getPathForLocation(PLATFORM_CLASS_PATH);
            Path userClassPath = getPathForLocation(CLASS_PATH);
            classSearchPath = new Path();
            classSearchPath.addAll(bootClassPath);
            classSearchPath.addAll(userClassPath);
        }
        return Collections.unmodifiableCollection(classSearchPath);
    }

    /** The actual effective locations for non-source, non-class files */
    private Path otherSearchPath;

    Collection<File> otherSearchPath() {
        if (otherSearchPath == null) {
            lazy();
            Path userClassPath = getPathForLocation(CLASS_PATH);
            Path sourcePath = getPathForLocation(SOURCE_PATH);
            if (sourcePath == null)
                otherSearchPath = userClassPath;
            else {
                otherSearchPath = new Path();
                otherSearchPath.addAll(userClassPath);
                otherSearchPath.addAll(sourcePath);
            }
        }
        return Collections.unmodifiableCollection(otherSearchPath);
    }

    /** Is this the name of an archive file? */
    private boolean isArchive(File file) {
        String n = file.getName().toLowerCase();
        return fsInfo.isFile(file)
            && (n.endsWith(".jar") || n.endsWith(".zip"));
    }
}
