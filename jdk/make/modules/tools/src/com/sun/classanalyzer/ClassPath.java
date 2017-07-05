/*
 * Copyright 2009 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */
package com.sun.classanalyzer;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 *
 * @author mchung
 */
public class ClassPath {

    public class FileInfo {

        File file;
        JarFile jarfile;
        int classCount;
        long filesize;

        FileInfo(File f) throws IOException {
            this.file = f;
            this.classCount = 0;
            if (file.getName().endsWith(".jar")) {
                this.filesize = file.length();
                jarfile = new JarFile(f);
            }
        }

        File getFile() {
            return file;
        }

        JarFile getJarFile() {
            return jarfile;
        }

        String getName() throws IOException {
            return file.getCanonicalPath();
        }
    }
    private List<FileInfo> fileList = new ArrayList<FileInfo>();
    private static ClassPath instance = new ClassPath();

    static List<FileInfo> getFileInfos() {
        return instance.fileList;
    }

    static ClassPath setJDKHome(String jdkhome) throws IOException {
        List<File> files = new ArrayList<File>();
        File jre = new File(jdkhome, "jre");
        File lib = new File(jdkhome, "lib");
        if (jre.exists() && jre.isDirectory()) {
            listFiles(new File(jre, "lib"), ".jar", files);
        } else if (lib.exists() && lib.isDirectory()) {
            // either a JRE or a jdk build image
            listFiles(lib, ".jar", files);

            File classes = new File(jdkhome, "classes");
            if (classes.exists() && classes.isDirectory()) {
                // jdk build outputdir
                instance.add(classes);
            }
        } else {
            throw new RuntimeException("\"" + jdkhome + "\" not a JDK home");
        }

        for (File f : files) {
            instance.add(f);
        }
        return instance;
    }

    static ClassPath setClassPath(String path) throws IOException {
        if (path.endsWith(".class")) {
            // one class file
            File f = new File(path);
            if (!f.exists()) {
                throw new RuntimeException("Classfile \"" + f + "\" doesn't exist");
            }

            instance.add(f);
        } else {
            List<File> jarFiles = new ArrayList<File>();
            String[] locs = path.split(File.pathSeparator);
            for (String p : locs) {
                File f = new File(p);
                if (!f.exists()) {
                    throw new RuntimeException("\"" + f + "\" doesn't exist");
                }

                if (f.isDirectory()) {
                    instance.add(f);  // add the directory to look up .class files
                    listFiles(f, ".jar", jarFiles);
                } else if (p.endsWith(".jar")) {
                    // jar files
                    jarFiles.add(f);
                } else {
                    throw new RuntimeException("Invalid file \"" + f);
                }
            }
            // add jarFiles if any
            for (File f : jarFiles) {
                instance.add(f);
            }
        }

        return instance;
    }

    private void add(File f) throws IOException {
        fileList.add(new FileInfo(f));
    }

    public static InputStream open(String pathname) throws IOException {
        for (FileInfo fi : instance.fileList) {
            if (fi.getName().endsWith(".jar")) {
                String path = pathname.replace(File.separatorChar, '/');
                JarEntry e = fi.jarfile.getJarEntry(path);
                if (e != null) {
                    return fi.jarfile.getInputStream(e);
                }
            } else if (fi.getFile().isDirectory()) {
                File f = new File(fi.getFile(), pathname);
                if (f.exists()) {
                    return new FileInputStream(f);
                }
            } else if (fi.file.isFile()) {
                if (fi.getName().endsWith(File.separator + pathname)) {
                    return new FileInputStream(fi.file);
                }
            }
        }
        return null;
    }

    static ClassFileParser parserForClass(String classname) throws IOException {
        String pathname = classname.replace('.', File.separatorChar) + ".class";

        ClassFileParser cfparser = null;
        for (FileInfo fi : instance.fileList) {
            if (fi.getName().endsWith(".class")) {
                if (fi.getName().endsWith(File.separator + pathname)) {
                    cfparser = ClassFileParser.newParser(fi.getFile(), true);
                    break;
                }
            } else if (fi.getName().endsWith(".jar")) {
                JarEntry e = fi.jarfile.getJarEntry(classname.replace('.', '/') + ".class");
                if (e != null) {
                    cfparser = ClassFileParser.newParser(fi.jarfile.getInputStream(e), e.getSize(), true);
                    break;
                }
            } else if (fi.getFile().isDirectory()) {
                File f = new File(fi.getFile(), pathname);
                if (f.exists()) {
                    cfparser = ClassFileParser.newParser(f, true);
                    break;
                }
            }
        }
        return cfparser;
    }

    public static void parseAllClassFiles() throws IOException {
        instance.parseFiles();
    }

    private void parseFiles() throws IOException {
        Set<Klass> classes = new HashSet<Klass>();

        int count = 0;
        for (FileInfo fi : fileList) {
            // filter out public generated classes (i.e. not public API)
            // javax.management.remote.rmi._RMIConnectionImpl_Tie
            // javax.management.remote.rmi._RMIServerImpl_Tie
            if (fi.getName().endsWith(".class")) {
                parseClass(fi);
            } else if (fi.getName().endsWith(".jar")) {
                Enumeration<JarEntry> entries = fi.jarfile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry e = entries.nextElement();
                    if (e.getName().endsWith(".class")) {
                        ClassFileParser cfparser = ClassFileParser.newParser(fi.jarfile.getInputStream(e), e.getSize(), true);
                        cfparser.parseDependency(false);
                        fi.classCount++;
                    } else if (!e.isDirectory() && ResourceFile.isResource(e.getName())) {
                        ResourceFile.addResource(e.getName(), fi.jarfile.getInputStream(e));
                    }
                }
            } else if (fi.getFile().isDirectory()) {
                List<File> files = new ArrayList<File>();
                listFiles(fi.getFile(), "", files);
                for (File f : files) {
                    if (f.getName().endsWith(".class")) {
                        parseClass(fi, f);
                    } else if (!f.isDirectory() && ResourceFile.isResource(f.getCanonicalPath())) {
                        String pathname = f.getCanonicalPath();
                        String dir = fi.getName();
                        if (!pathname.startsWith(dir)) {
                            throw new RuntimeException("Incorrect pathname " + pathname);
                        }
                        String name = pathname.substring(dir.length() + 1, pathname.length());
                        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
                        try {
                            ResourceFile.addResource(name, in);
                        } finally {
                            in.close();
                        }
                    }
                }
            } else {
                // should not reach here
                throw new RuntimeException("Unexpected class path: " + fi.getFile());
            }
        }
    }

    private void parseClass(FileInfo fi) throws IOException {
        parseClass(fi, fi.getFile());
    }

    private void parseClass(FileInfo fi, File f) throws IOException {
        ClassFileParser cfparser = ClassFileParser.newParser(f, true);
        cfparser.parseDependency(false);
        fi.classCount++;
        // need to update the filesize for this directory
        fi.filesize += fi.getFile().length();

    }

    public static void listFiles(File path, String suffix, List<File> result) {
        if (path.isDirectory()) {
            File[] children = path.listFiles();
            for (File c : children) {
                listFiles(c, suffix, result);
            }

        } else {
            if (suffix.isEmpty() || path.getName().endsWith(suffix)) {
                result.add(path);
            }
        }
    }
}
