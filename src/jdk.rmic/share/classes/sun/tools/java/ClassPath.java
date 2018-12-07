/*
 * Copyright (c) 1994, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.java;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.zip.*;
import java.util.Enumeration;
import java.util.Map;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Set;
import java.util.LinkedHashSet;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.ProviderNotFoundException;
import java.nio.file.spi.FileSystemProvider;

/**
 * This class is used to represent a class path, which can contain both
 * directories and zip files.
 *
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public
class ClassPath {
    private FileSystem getJrtFileSystem() {
        return FileSystems.getFileSystem(URI.create("jrt:/"));
    }

    static final char dirSeparator = File.pathSeparatorChar;

    /**
     * The original class path string
     */
    String pathstr;

    /**
     * List of class path entries
     */
    private ClassPathEntry[] path;

    /**
     * Build a class path from the specified path string
     */
    public ClassPath(String pathstr) {
        init(pathstr);
    }

    /**
     * Build a class path from the specified array of class path
     * element strings.  This constructor, and the corresponding
     * "init" method, were added as part of the fix for 6473331, which
     * adds support for Class-Path manifest entries in JAR files to
     * rmic.  It is conceivable that the value of a Class-Path
     * manifest entry will contain a path separator, which would cause
     * incorrect behavior if the expanded path were passed to the
     * previous constructor as a single path-separator-delimited
     * string; use of this constructor avoids that problem.
     */
    public ClassPath(String[] patharray) {
        init(patharray);
    }

    /**
     * Build a default class path from the path strings specified by
     * the properties sun.boot.class.path and env.class.path, in that
     * order.
     */
    public ClassPath() {
        // though this property is removed. Check for null and use only
        // if it is not null (when bootstrap JDK is used).
        String syscp = System.getProperty("sun.boot.class.path");
        String envcp = System.getProperty("env.class.path");
        if (envcp == null) envcp = ".";

        // add syscp only if not null!
        String cp = syscp == null? envcp : (syscp + File.pathSeparator + envcp);
        init(cp);
    }

    private void init(String pathstr) {
        int i, j, n;
        // Save original class path string
        this.pathstr = pathstr;

        if (pathstr.length() == 0) {
            this.path = new ClassPathEntry[0];
        }

        // Count the number of path separators
        i = n = 0;
        while ((i = pathstr.indexOf(dirSeparator, i)) != -1) {
            n++; i++;
        }
        // Build the class path
        ClassPathEntry[] path = new ClassPathEntry[n+1];

        int len = pathstr.length();
        for (i = n = 0; i < len; i = j + 1) {
            if ((j = pathstr.indexOf(dirSeparator, i)) == -1) {
                j = len;
            }
            if (i == j) {
                path[n++] = new DirClassPathEntry(new File("."));
            } else {
                String filename = pathstr.substring(i, j);
                File file = new File(filename);
                if (file.isFile()) {
                    try {
                        ZipFile zip = new ZipFile(file);
                        path[n++] = new ZipClassPathEntry(zip);
                    } catch (ZipException e) {
                    } catch (IOException e) {
                        // Ignore exceptions, at least for now...
                    }
                } else {
                    path[n++] = new DirClassPathEntry(file);
                }
            }
        }

        // add jrt file system at the end
        try {
            FileSystem fs = getJrtFileSystem();
            path[n++] = new JrtClassPathEntry(fs);
        } catch (ProviderNotFoundException ignored) {
            // this could happen during jdk build with earlier JDK as bootstrap
        }

        // Trim class path to exact size
        this.path = new ClassPathEntry[n];
        System.arraycopy((Object)path, 0, (Object)this.path, 0, n);
    }

    private void init(String[] patharray) {
        // Save original class path string
        if (patharray.length == 0) {
            this.pathstr = "";
        } else {
            StringBuilder sb = new StringBuilder(patharray[0]);
            for (int i = 1; i < patharray.length; i++) {
                sb.append(File.pathSeparatorChar);
                sb.append(patharray[i]);
            }
            this.pathstr = sb.toString();
        }

        // Build the class path
        ClassPathEntry[] path = new ClassPathEntry[patharray.length + 1];
        int n = 0;
        for (String name : patharray) {
            File file = new File(name);
            if (file.isFile()) {
                try {
                    ZipFile zip = new ZipFile(file);
                    path[n++] = new ZipClassPathEntry(zip);
                } catch (ZipException e) {
                } catch (IOException e) {
                    // Ignore exceptions, at least for now...
                }
            } else {
                path[n++] = new DirClassPathEntry(file);
            }
        }

        // add jrt file system at the end
        try {
            FileSystem fs = getJrtFileSystem();
            path[n++] = new JrtClassPathEntry(fs);
        } catch (ProviderNotFoundException ignored) {
            // this could happen with earlier version of JDK used as bootstrap
        }

        // Trim class path to exact size
        this.path = new ClassPathEntry[n];
        System.arraycopy((Object)path, 0, (Object)this.path, 0, n);
    }

    /**
     * Find the specified directory in the class path
     */
    public ClassFile getDirectory(String name) {
        return getFile(name, true);
    }

    /**
     * Load the specified file from the class path
     */
    public ClassFile getFile(String name) {
        return getFile(name, false);
    }

    private final String fileSeparatorChar = "" + File.separatorChar;

    private ClassFile getFile(String name, boolean isDirectory) {
        String subdir = name;
        String basename = "";
        if (!isDirectory) {
            int i = name.lastIndexOf(File.separatorChar);
            subdir = name.substring(0, i + 1);
            basename = name.substring(i + 1);
        } else if (!subdir.isEmpty()
                   && !subdir.endsWith(fileSeparatorChar)) {
            // zip files are picky about "foo" vs. "foo/".
            // also, the getFiles caches are keyed with a trailing /
            subdir = subdir + File.separatorChar;
            name = subdir;      // Note: isDirectory==true & basename==""
        }
        for (int i = 0; i < path.length; i++) {
            ClassFile cf = path[i].getFile(name, subdir, basename, isDirectory);
            if (cf != null) {
                return cf;
            }
        }
        return null;
    }

    /**
     * Returns list of files given a package name and extension.
     */
    public Enumeration<ClassFile> getFiles(String pkg, String ext) {
        Hashtable<String, ClassFile> files = new Hashtable<>();
        for (int i = path.length; --i >= 0; ) {
            path[i].fillFiles(pkg, ext, files);
        }
        return files.elements();
    }

    /**
     * Release resources.
     */
    public void close() throws IOException {
        for (int i = path.length; --i >= 0; ) {
            path[i].close();
        }
    }

    /**
     * Returns original class path string
     */
    public String toString() {
        return pathstr;
    }
}

/**
 * A class path entry, which can either be a directory or an open zip file or an open jimage filesystem.
 */
abstract class ClassPathEntry {
    abstract ClassFile getFile(String name, String subdir, String basename, boolean isDirectory);
    abstract void fillFiles(String pkg, String ext, Hashtable<String, ClassFile> files);
    abstract void close() throws IOException;
}

// a ClassPathEntry that represents a directory
final class DirClassPathEntry extends ClassPathEntry {
    private final File dir;

    DirClassPathEntry(File dir) {
        this.dir = dir;
    }

    private final Hashtable<String, String[]> subdirs = new Hashtable<>(29); // cache of sub-directory listings:
    private String[] getFiles(String subdir) {
        String files[] = subdirs.get(subdir);
        if (files == null) {
            files = computeFiles(subdir);
            subdirs.put(subdir, files);
        }
        return files;
    }

    private String[] computeFiles(String subdir) {
        File sd = new File(dir.getPath(), subdir);
        String[] files = null;
        if (sd.isDirectory()) {
            files = sd.list();
            if (files == null) {
                // should not happen, but just in case, fail silently
                files = new String[0];
            }
            if (files.length == 0) {
                String nonEmpty[] = { "" };
                files = nonEmpty;
            }
        } else {
            files = new String[0];
        }
        return files;
    }

    ClassFile getFile(String name,  String subdir, String basename, boolean isDirectory) {
        File file = new File(dir.getPath(), name);
        String list[] = getFiles(subdir);
        if (isDirectory) {
            if (list.length > 0) {
                return ClassFile.newClassFile(file);
            }
        } else {
            for (int j = 0; j < list.length; j++) {
                if (basename.equals(list[j])) {
                    // Don't bother checking !file.isDir,
                    // since we only look for names which
                    // cannot already be packages (foo.java, etc).
                    return ClassFile.newClassFile(file);
                }
            }
        }
        return null;
    }

    void fillFiles(String pkg, String ext, Hashtable<String, ClassFile> files) {
        String[] list = getFiles(pkg);
        for (int j = 0; j < list.length; j++) {
            String name = list[j];
            if (name.endsWith(ext)) {
                name = pkg + File.separatorChar + name;
                File file = new File(dir.getPath(), name);
                files.put(name, ClassFile.newClassFile(file));
            }
        }
    }

    void close() throws IOException {
    }
}

// a ClassPathEntry that represents a .zip or a .jar file
final class ZipClassPathEntry extends ClassPathEntry {
    private final ZipFile zip;

    ZipClassPathEntry(ZipFile zip) {
        this.zip = zip;
    }

    void close() throws IOException {
        zip.close();
    }

    ClassFile getFile(String name, String subdir, String basename, boolean isDirectory) {
        String newname = name.replace(File.separatorChar, '/');
        ZipEntry entry = zip.getEntry(newname);
        return entry != null? ClassFile.newClassFile(zip, entry) : null;
    }

    void fillFiles(String pkg, String ext, Hashtable<String, ClassFile> files) {
        Enumeration<? extends ZipEntry> e = zip.entries();
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry)e.nextElement();
            String name = entry.getName();
            name = name.replace('/', File.separatorChar);
            if (name.startsWith(pkg) && name.endsWith(ext)) {
                files.put(name, ClassFile.newClassFile(zip, entry));
            }
        }
    }
}

// a ClassPathEntry that represents jrt file system
final class JrtClassPathEntry extends ClassPathEntry {
    private final FileSystem fs;
    // package name to package directory path mapping (lazily filled)
    private final Map<String, Path> pkgDirs;

    JrtClassPathEntry(FileSystem fs) {
        this.fs = fs;
        this.pkgDirs = new HashMap<>();
    }

    void close() throws IOException {
    }

    // from pkgName (internal separator '/') to it's Path in jrtfs
    synchronized Path getPackagePath(String pkgName) throws IOException {
        // check the cache first
        if (pkgDirs.containsKey(pkgName)) {
            return pkgDirs.get(pkgName);
        }

        Path pkgLink = fs.getPath("/packages/" + pkgName.replace('/', '.'));
        // check if /packages/$PACKAGE directory exists
        if (Files.isDirectory(pkgLink)) {
           try (DirectoryStream<Path> stream = Files.newDirectoryStream(pkgLink)) {
                for (Path p : stream) {
                    // find first symbolic link to module directory
                    if (Files.isSymbolicLink(p)) {
                        Path modDir = Files.readSymbolicLink(p);
                        if (Files.isDirectory(modDir)) {
                            // get package subdirectory under /modules/$MODULE/
                            Path pkgDir = fs.getPath(modDir.toString() + "/" + pkgName);
                            if (Files.isDirectory(pkgDir)) {
                                // it is a package directory only if contains
                                // at least one .class file
                                try (DirectoryStream<Path> pstream =
                                        Files.newDirectoryStream(pkgDir)) {
                                    for (Path f : pstream) {
                                        if (Files.isRegularFile(f)
                                                && f.toString().endsWith(".class")) {
                                            pkgDirs.put(pkgName, pkgDir);
                                            return pkgDir;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    // fully qualified (internal) class name to it's Path in jrtfs
    Path getClassPath(String clsName) throws IOException {
        int index = clsName.lastIndexOf('/');
        if (index == -1) {
            return null;
        }
        Path pkgPath = getPackagePath(clsName.substring(0, index));
        return pkgPath == null? null : fs.getPath(pkgPath + "/" + clsName.substring(index + 1));
    }

    ClassFile getFile(String name, String subdir, String basename, boolean isDirectory) {
        try {
            name = name.replace(File.separatorChar, '/');
            Path cp = getClassPath(name);
            return cp == null? null : ClassFile.newClassFile(cp);
        } catch (IOException ioExp) {
            throw new UncheckedIOException(ioExp);
        }
    }

    void fillFiles(String pkg, String ext, Hashtable<String, ClassFile> files) {
        Path dir;
        try {
            dir = getPackagePath(pkg);
            if (dir == null) {
                return;
            }
        } catch (IOException ioExp) {
            throw new UncheckedIOException(ioExp);
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path p : stream) {
                String name = p.toString();
                name = name.replace('/', File.separatorChar);
                if (name.startsWith(pkg) && name.endsWith(ext)) {
                    files.put(name, ClassFile.newClassFile(p));
                }
            }
        } catch (IOException ioExp) {
            throw new UncheckedIOException(ioExp);
        }
    }
}
