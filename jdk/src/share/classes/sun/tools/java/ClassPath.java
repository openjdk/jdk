/*
 * Copyright (c) 1994, 2007, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Enumeration;
import java.util.Hashtable;
import java.io.File;
import java.io.IOException;
import java.util.zip.*;

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
        String syscp = System.getProperty("sun.boot.class.path");
        String envcp = System.getProperty("env.class.path");
        if (envcp == null) envcp = ".";
        String cp = syscp + File.pathSeparator + envcp;
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
                path[n] = new ClassPathEntry();
                path[n++].dir = new File(".");
            } else {
                File file = new File(pathstr.substring(i, j));
                if (file.isFile()) {
                    try {
                        ZipFile zip = new ZipFile(file);
                        path[n] = new ClassPathEntry();
                        path[n++].zip = zip;
                    } catch (ZipException e) {
                    } catch (IOException e) {
                        // Ignore exceptions, at least for now...
                    }
                } else {
                    path[n] = new ClassPathEntry();
                    path[n++].dir = file;
                }
            }
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
                sb.append(File.separator);
                sb.append(patharray[i]);
            }
            this.pathstr = sb.toString();
        }

        // Build the class path
        ClassPathEntry[] path = new ClassPathEntry[patharray.length];
        int n = 0;
        for (String name : patharray) {
            File file = new File(name);
            if (file.isFile()) {
                try {
                    ZipFile zip = new ZipFile(file);
                    path[n] = new ClassPathEntry();
                    path[n++].zip = zip;
                } catch (ZipException e) {
                } catch (IOException e) {
                    // Ignore exceptions, at least for now...
                }
            } else {
                path[n] = new ClassPathEntry();
                path[n++].dir = file;
            }
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
        } else if (!subdir.equals("")
                   && !subdir.endsWith(fileSeparatorChar)) {
            // zip files are picky about "foo" vs. "foo/".
            // also, the getFiles caches are keyed with a trailing /
            subdir = subdir + File.separatorChar;
            name = subdir;      // Note: isDirectory==true & basename==""
        }
        for (int i = 0; i < path.length; i++) {
            if (path[i].zip != null) {
                String newname = name.replace(File.separatorChar, '/');
                ZipEntry entry = path[i].zip.getEntry(newname);
                if (entry != null) {
                    return new ClassFile(path[i].zip, entry);
                }
            } else {
                File file = new File(path[i].dir.getPath(), name);
                String list[] = path[i].getFiles(subdir);
                if (isDirectory) {
                    if (list.length > 0) {
                        return new ClassFile(file);
                    }
                } else {
                    for (int j = 0; j < list.length; j++) {
                        if (basename.equals(list[j])) {
                            // Don't bother checking !file.isDir,
                            // since we only look for names which
                            // cannot already be packages (foo.java, etc).
                            return new ClassFile(file);
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Returns list of files given a package name and extension.
     */
    public Enumeration getFiles(String pkg, String ext) {
        Hashtable files = new Hashtable();
        for (int i = path.length; --i >= 0; ) {
            if (path[i].zip != null) {
                Enumeration e = path[i].zip.entries();
                while (e.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry)e.nextElement();
                    String name = entry.getName();
                    name = name.replace('/', File.separatorChar);
                    if (name.startsWith(pkg) && name.endsWith(ext)) {
                        files.put(name, new ClassFile(path[i].zip, entry));
                    }
                }
            } else {
                String[] list = path[i].getFiles(pkg);
                for (int j = 0; j < list.length; j++) {
                    String name = list[j];
                    if (name.endsWith(ext)) {
                        name = pkg + File.separatorChar + name;
                        File file = new File(path[i].dir.getPath(), name);
                        files.put(name, new ClassFile(file));
                    }
                }
            }
        }
        return files.elements();
    }

    /**
     * Release resources.
     */
    public void close() throws IOException {
        for (int i = path.length; --i >= 0; ) {
            if (path[i].zip != null) {
                path[i].zip.close();
            }
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
 * A class path entry, which can either be a directory or an open zip file.
 */
class ClassPathEntry {
    File dir;
    ZipFile zip;

    Hashtable subdirs = new Hashtable(29); // cache of sub-directory listings
    String[] getFiles(String subdir) {
        String files[] = (String[]) subdirs.get(subdir);
        if (files == null) {
            // search the directory, exactly once
            File sd = new File(dir.getPath(), subdir);
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
            subdirs.put(subdir, files);
        }
        return files;
    }

}
