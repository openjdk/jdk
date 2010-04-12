/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
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

package com.sun.tools.javac.nio;


import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.Attributes;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.lang.model.SourceVersion;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.JavaFileObject.Kind;
import javax.tools.StandardLocation;

import static java.nio.file.FileVisitOption.*;
import static javax.tools.StandardLocation.*;

import com.sun.tools.javac.file.Paths;
import com.sun.tools.javac.util.BaseFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.util.ListBuffer;

import static com.sun.tools.javac.main.OptionName.*;


// NOTE the imports carefully for this compilation unit.
//
// Path:  java.nio.file.Path -- the new NIO type for which this file manager exists
//
// Paths: com.sun.tools.javac.file.Paths -- legacy javac type for handling path options
//      The other Paths (java.nio.file.Paths) is not used

// NOTE this and related classes depend on new API in JDK 7.
// This requires special handling while bootstrapping the JDK build,
// when these classes might not yet have been compiled. To workaround
// this, the build arranges to make stubs of these classes available
// when compiling this and related classes. The set of stub files
// is specified in make/build.properties.

/**
 *  Implementation of PathFileManager: a JavaFileManager based on the use
 *  of java.nio.file.Path.
 *
 *  <p>Just as a Path is somewhat analagous to a File, so too is this
 *  JavacPathFileManager analogous to JavacFileManager, as it relates to the
 *  support of FileObjects based on File objects (i.e. just RegularFileObject,
 *  not ZipFileObject and its variants.)
 *
 *  <p>The default values for the standard locations supported by this file
 *  manager are the same as the default values provided by JavacFileManager --
 *  i.e. as determined by the javac.file.Paths class. To override these values,
 *  call {@link #setLocation}.
 *
 *  <p>To reduce confusion with Path objects, the locations such as "class path",
 *  "source path", etc, are generically referred to here as "search paths".
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class JavacPathFileManager extends BaseFileManager implements PathFileManager {
    protected FileSystem defaultFileSystem;

    /**
     * Create a JavacPathFileManager using a given context, optionally registering
     * it as the JavaFileManager for that context.
     */
    public JavacPathFileManager(Context context, boolean register, Charset charset) {
        super(charset);
        if (register)
            context.put(JavaFileManager.class, this);
        pathsForLocation = new HashMap<Location, PathsForLocation>();
        fileSystems = new HashMap<Path,FileSystem>();
        setContext(context);
    }

    /**
     * Set the context for JavacPathFileManager.
     */
    @Override
    protected void setContext(Context context) {
        super.setContext(context);
        searchPaths = Paths.instance(context);
    }

    @Override
    public FileSystem getDefaultFileSystem() {
        if (defaultFileSystem == null)
            defaultFileSystem = FileSystems.getDefault();
        return defaultFileSystem;
    }

    @Override
    public void setDefaultFileSystem(FileSystem fs) {
        defaultFileSystem = fs;
    }

    @Override
    public void flush() throws IOException {
        contentCache.clear();
    }

    @Override
    public void close() throws IOException {
        for (FileSystem fs: fileSystems.values())
            fs.close();
    }

    @Override
    public ClassLoader getClassLoader(Location location) {
        nullCheck(location);
        Iterable<? extends Path> path = getLocation(location);
        if (path == null)
            return null;
        ListBuffer<URL> lb = new ListBuffer<URL>();
        for (Path p: path) {
            try {
                lb.append(p.toUri().toURL());
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }

        return getClassLoader(lb.toArray(new URL[lb.size()]));
    }

    // <editor-fold defaultstate="collapsed" desc="Location handling">

    public boolean hasLocation(Location location) {
        return (getLocation(location) != null);
    }

    public Iterable<? extends Path> getLocation(Location location) {
        nullCheck(location);
        lazyInitSearchPaths();
        PathsForLocation path = pathsForLocation.get(location);
        if (path == null && !pathsForLocation.containsKey(location)) {
            setDefaultForLocation(location);
            path = pathsForLocation.get(location);
        }
        return path;
    }

    private Path getOutputLocation(Location location) {
        Iterable<? extends Path> paths = getLocation(location);
        return (paths == null ? null : paths.iterator().next());
    }

    public void setLocation(Location location, Iterable<? extends Path> searchPath)
            throws IOException
    {
        nullCheck(location);
        lazyInitSearchPaths();
        if (searchPath == null) {
            setDefaultForLocation(location);
        } else {
            if (location.isOutputLocation())
                checkOutputPath(searchPath);
            PathsForLocation pl = new PathsForLocation();
            for (Path p: searchPath)
                pl.add(p);  // TODO -Xlint:path warn if path not found
            pathsForLocation.put(location, pl);
        }
    }

    private void checkOutputPath(Iterable<? extends Path> searchPath) throws IOException {
        Iterator<? extends Path> pathIter = searchPath.iterator();
        if (!pathIter.hasNext())
            throw new IllegalArgumentException("empty path for directory");
        Path path = pathIter.next();
        if (pathIter.hasNext())
            throw new IllegalArgumentException("path too long for directory");
        if (!path.exists())
            throw new FileNotFoundException(path + ": does not exist");
        else if (!isDirectory(path))
            throw new IOException(path + ": not a directory");
    }

    private void setDefaultForLocation(Location locn) {
        Collection<File> files = null;
        if (locn instanceof StandardLocation) {
            switch ((StandardLocation) locn) {
                case CLASS_PATH:
                    files = searchPaths.userClassPath();
                    break;
                case PLATFORM_CLASS_PATH:
                    files = searchPaths.bootClassPath();
                    break;
                case SOURCE_PATH:
                    files = searchPaths.sourcePath();
                    break;
                case CLASS_OUTPUT: {
                    String arg = options.get(D);
                    files = (arg == null ? null : Collections.singleton(new File(arg)));
                    break;
                }
                case SOURCE_OUTPUT: {
                    String arg = options.get(S);
                    files = (arg == null ? null : Collections.singleton(new File(arg)));
                    break;
                }
            }
        }

        PathsForLocation pl = new PathsForLocation();
        if (files != null) {
            for (File f: files)
                pl.add(f.toPath());
        }
        pathsForLocation.put(locn, pl);
    }

    private void lazyInitSearchPaths() {
        if (!inited) {
            setDefaultForLocation(PLATFORM_CLASS_PATH);
            setDefaultForLocation(CLASS_PATH);
            setDefaultForLocation(SOURCE_PATH);
            inited = true;
        }
    }
    // where
        private boolean inited = false;

    private Map<Location, PathsForLocation> pathsForLocation;
    private Paths searchPaths;

    private static class PathsForLocation extends LinkedHashSet<Path> {
        private static final long serialVersionUID = 6788510222394486733L;
    }

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="FileObject handling">

    @Override
    public Path getPath(FileObject fo) {
        nullCheck(fo);
        if (!(fo instanceof PathFileObject))
            throw new IllegalArgumentException();
        return ((PathFileObject) fo).getPath();
    }

    @Override
    public boolean isSameFile(FileObject a, FileObject b) {
        nullCheck(a);
        nullCheck(b);
        if (!(a instanceof PathFileObject))
            throw new IllegalArgumentException("Not supported: " + a);
        if (!(b instanceof PathFileObject))
            throw new IllegalArgumentException("Not supported: " + b);
        return ((PathFileObject) a).isSameFile((PathFileObject) b);
    }

    @Override
    public Iterable<JavaFileObject> list(Location location,
            String packageName, Set<Kind> kinds, boolean recurse)
            throws IOException {
        // validatePackageName(packageName);
        nullCheck(packageName);
        nullCheck(kinds);

        Iterable<? extends Path> paths = getLocation(location);
        if (paths == null)
            return List.nil();
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

        for (Path path : paths)
            list(path, packageName, kinds, recurse, results);

        return results.toList();
    }

    private void list(Path path, String packageName, final Set<Kind> kinds,
            boolean recurse, final ListBuffer<JavaFileObject> results)
            throws IOException {
        if (!path.exists())
            return;

        final Path pathDir;
        if (isDirectory(path))
            pathDir = path;
        else {
            FileSystem fs = getFileSystem(path);
            if (fs == null)
                return;
            pathDir = fs.getRootDirectories().iterator().next();
        }
        String sep = path.getFileSystem().getSeparator();
        Path packageDir = packageName.isEmpty() ? pathDir
                : pathDir.resolve(packageName.replace(".", sep));
        if (!packageDir.exists())
            return;

/* Alternate impl of list, superceded by use of Files.walkFileTree */
//        Deque<Path> queue = new LinkedList<Path>();
//        queue.add(packageDir);
//
//        Path dir;
//        while ((dir = queue.poll()) != null) {
//            DirectoryStream<Path> ds = dir.newDirectoryStream();
//            try {
//                for (Path p: ds) {
//                    String name = p.getName().toString();
//                    if (isDirectory(p)) {
//                        if (recurse && SourceVersion.isIdentifier(name)) {
//                            queue.add(p);
//                        }
//                    } else {
//                        if (kinds.contains(getKind(name))) {
//                            JavaFileObject fe =
//                                PathFileObject.createDirectoryPathFileObject(this, p, pathDir);
//                            results.append(fe);
//                        }
//                    }
//                }
//            } finally {
//                ds.close();
//            }
//        }
        int maxDepth = (recurse ? Integer.MAX_VALUE : 1);
        Set<FileVisitOption> opts = EnumSet.of(DETECT_CYCLES, FOLLOW_LINKS);
        Files.walkFileTree(packageDir, opts, maxDepth,
                new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir) {
                if (SourceVersion.isIdentifier(dir.getName().toString())) // JSR 292?
                    return FileVisitResult.CONTINUE;
                else
                    return FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile() && kinds.contains(getKind(file.getName().toString()))) {
                    JavaFileObject fe =
                        PathFileObject.createDirectoryPathFileObject(
                            JavacPathFileManager.this, file, pathDir);
                    results.append(fe);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjectsFromPaths(
        Iterable<? extends Path> paths) {
        ArrayList<PathFileObject> result;
        if (paths instanceof Collection<?>)
            result = new ArrayList<PathFileObject>(((Collection<?>)paths).size());
        else
            result = new ArrayList<PathFileObject>();
        for (Path p: paths)
            result.add(PathFileObject.createSimplePathFileObject(this, nullCheck(p)));
        return result;
    }

    @Override
    public Iterable<? extends JavaFileObject> getJavaFileObjects(Path... paths) {
        return getJavaFileObjectsFromPaths(Arrays.asList(nullCheck(paths)));
    }

    @Override
    public JavaFileObject getJavaFileForInput(Location location,
            String className, Kind kind) throws IOException {
        return getFileForInput(location, getRelativePath(className, kind));
    }

    @Override
    public FileObject getFileForInput(Location location,
            String packageName, String relativeName) throws IOException {
        return getFileForInput(location, getRelativePath(packageName, relativeName));
    }

    private JavaFileObject getFileForInput(Location location, String relativePath)
            throws IOException {
        for (Path p: getLocation(location)) {
            if (isDirectory(p)) {
                Path f = resolve(p, relativePath);
                if (f.exists())
                    return PathFileObject.createDirectoryPathFileObject(this, f, p);
            } else {
                FileSystem fs = getFileSystem(p);
                if (fs != null) {
                    Path file = getPath(fs, relativePath);
                    if (file.exists())
                        return PathFileObject.createJarPathFileObject(this, file);
                }
            }
        }
        return null;
    }

    @Override
    public JavaFileObject getJavaFileForOutput(Location location,
            String className, Kind kind, FileObject sibling) throws IOException {
        return getFileForOutput(location, getRelativePath(className, kind), sibling);
    }

    @Override
    public FileObject getFileForOutput(Location location, String packageName,
            String relativeName, FileObject sibling)
            throws IOException {
        return getFileForOutput(location, getRelativePath(packageName, relativeName), sibling);
    }

    private JavaFileObject getFileForOutput(Location location,
            String relativePath, FileObject sibling) {
        Path dir = getOutputLocation(location);
        if (dir == null) {
            if (location == CLASS_OUTPUT) {
                Path siblingDir = null;
                if (sibling != null && sibling instanceof PathFileObject) {
                    siblingDir = ((PathFileObject) sibling).getPath().getParent();
                }
                return PathFileObject.createSiblingPathFileObject(this,
                        siblingDir.resolve(getBaseName(relativePath)),
                        relativePath);
            } else if (location == SOURCE_OUTPUT) {
                dir = getOutputLocation(CLASS_OUTPUT);
            }
        }

        Path file;
        if (dir != null) {
            file = resolve(dir, relativePath);
            return PathFileObject.createDirectoryPathFileObject(this, file, dir);
        } else {
            file = getPath(getDefaultFileSystem(), relativePath);
            return PathFileObject.createSimplePathFileObject(this, file);
        }

    }

    @Override
    public String inferBinaryName(Location location, JavaFileObject fo) {
        nullCheck(fo);
        // Need to match the path semantics of list(location, ...)
        Iterable<? extends Path> paths = getLocation(location);
        if (paths == null) {
            return null;
        }

        if (!(fo instanceof PathFileObject))
            throw new IllegalArgumentException(fo.getClass().getName());

        return ((PathFileObject) fo).inferBinaryName(paths);
    }

    private FileSystem getFileSystem(Path p) throws IOException {
        FileSystem fs = fileSystems.get(p);
        if (fs == null) {
            fs = FileSystems.newFileSystem(p, Collections.<String,Void>emptyMap(), null);
            fileSystems.put(p, fs);
        }
        return fs;
    }

    private Map<Path,FileSystem> fileSystems;

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Utility methods">

    private static String getRelativePath(String className, Kind kind) {
        return className.replace(".", "/") + kind.extension;
    }

    private static String getRelativePath(String packageName, String relativeName) {
        return packageName.replace(".", "/") + relativeName;
    }

    private static String getBaseName(String relativePath) {
        int lastSep = relativePath.lastIndexOf("/");
        return relativePath.substring(lastSep + 1); // safe if "/" not found
    }

    private static boolean isDirectory(Path path) throws IOException {
        BasicFileAttributes attrs = Attributes.readBasicFileAttributes(path);
        return attrs.isDirectory();
    }

    private static Path getPath(FileSystem fs, String relativePath) {
        return fs.getPath(relativePath.replace("/", fs.getSeparator()));
    }

    private static Path resolve(Path base, String relativePath) {
        FileSystem fs = base.getFileSystem();
        Path rp = fs.getPath(relativePath.replace("/", fs.getSeparator()));
        return base.resolve(rp);
    }

    // </editor-fold>

}
