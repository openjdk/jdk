/*
 * Copyright (c) 2009, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * @test
 * @bug 6410367 6411310
 * @summary FileObject should support user-friendly names via getName()
 */

import java.io.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import javax.tools.*;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;

// Test FileObject.getName returned from JavacFileManager and its support classes.

public class Test {
    public static void main(String... args) throws Exception {
        new Test().run();
    }

    Set<String> foundClasses = new TreeSet<String>();
    Set<String> foundJars = new TreeSet<String>();

    void run() throws Exception {
        File rt_jar = findRtJar();

        // names for entries to be created in directories and jar files
        String[] entries = { "p/A.java", "p/A.class", "p/resources/A-1.html" };

        // test various combinations of directories and jar files, intended to
        // cover all sources of file objects within JavacFileManager's support classes

        test(createFileManager(), createDir("dir", entries), "p", entries);
        test(createFileManager(), createDir("a b/dir", entries), "p", entries);

        for (boolean useJavaUtilZip: new boolean[] { false, true }) {
            test(createFileManager(useJavaUtilZip), createJar("jar", entries), "p", entries);
            test(createFileManager(useJavaUtilZip), createJar("jar jar", entries), "p", entries);

            for (boolean useSymbolFile: new boolean[] { false, true }) {
                test(createFileManager(useJavaUtilZip, useSymbolFile), rt_jar, "java.lang.ref", null);
            }
        }

        if (errors > 0)
            throw new Exception(errors + " errors found");

        // Verify that we hit all the impl classes we intended
        checkCoverage("classes", foundClasses,
                "RegularFileObject", "SymbolFileObject", "ZipFileIndexFileObject", "ZipFileObject");

        // Verify that we hit the jar files we intended, specifically ct.sym as well as rt.jar
        checkCoverage("jar files", foundJars,
                "ct.sym", "jar", "jar jar", "rt.jar");
    }

    // use a new file manager for each test
    void test(StandardJavaFileManager fm, File f, String pkg, String[] entries) throws Exception {
        System.err.println("Test " + f);
        try {
            if (f.isDirectory()) {
                for (File dir: new File[] { f, f.getAbsoluteFile() }) {
                    for (String e: entries) {
                        JavaFileObject fo = fm.getJavaFileObjects(new File(dir, e)).iterator().next();
                        test(fo, dir, e);
                    }
                }
            }

            fm.setLocation(StandardLocation.CLASS_PATH, Collections.singleton(f));
            fm.setLocation(StandardLocation.SOURCE_PATH, Collections.singleton(f.getAbsoluteFile()));
            for (StandardLocation l: EnumSet.of(StandardLocation.CLASS_PATH, StandardLocation.SOURCE_PATH)) {
                for (JavaFileObject fo: fm.list(l, pkg, EnumSet.allOf(JavaFileObject.Kind.class), true)) {
                    // we could use fm.getLocation but the following guarantees we preserve the original filename
                    File dir = (l == StandardLocation.CLASS_PATH ? f : f.getAbsoluteFile());
                    char sep = (dir.isDirectory() ? File.separatorChar : '/');
                    String b = fm.inferBinaryName(l, fo);
                    String e = fo.getKind().extension;
                    test(fo, dir, b.replace('.', sep) + e);
                }
            }
        } finally {
            fm.close();
        }
    }

    void test(JavaFileObject fo, File dir, String p) {
        System.err.println("Test: " + fo);
        String expect = dir.isDirectory() ? new File(dir, p).getPath() : (dir.getPath() + "(" + p + ")");
        String found = fo.getName();
        // if ct.sym is found, replace it with the equivalent rt.jar
        String found2 = found.replaceAll("lib([\\\\/])ct.sym\\(META-INF/sym/rt.jar/", "jre$1lib$1rt.jar(");
        if (!expect.equals(found2)) {
            System.err.println("expected: " + expect);
            System.err.println("   found: " + found);
            if (!found.equals(found2))
                System.err.println("  found2: " + found2);
            error("Failed: " + fo);
        }

        // record the file object class name for coverage checks later
        foundClasses.add(fo.getClass().getSimpleName());

        if (found.contains("(")) {
            // record access to the jar file for coverage checks later
            foundJars.add(new File(found.substring(0, found.indexOf("("))).getName());
        }
    }

    void checkCoverage(String label, Set<String> found, String... expect) throws Exception {
        Set<String> e = new TreeSet<String>(Arrays.asList(expect));
        if (!found.equals(e)) {
            e.removeAll(found);
            throw new Exception("expected " + label + " not used: " + e);
        }
    }

    JavacFileManager createFileManager() {
        return createFileManager(false, false);
    }

    JavacFileManager createFileManager(boolean useJavaUtilZip) {
        return createFileManager(useJavaUtilZip, false);
    }

    JavacFileManager createFileManager(boolean useJavaUtilZip, boolean useSymbolFile) {
        // javac should really not be using system properties like this
        // -- it should really be using (hidden) options -- but until then
        // take care to leave system properties as we find them, so as not
        // to adversely affect other tests that might follow.
        String prev = System.getProperty("useJavaUtilZip");
        boolean resetProperties = false;
        try {
            if (useJavaUtilZip) {
                System.setProperty("useJavaUtilZip", "true");
                resetProperties = true;
            } else if (System.getProperty("useJavaUtilZip") != null) {
                System.getProperties().remove("useJavaUtilZip");
                resetProperties = true;
            }

            Context c = new Context();
            if (!useSymbolFile) {
                Options options = Options.instance(c);
                options.put("ignore.symbol.file", "true");
            }

            return new JavacFileManager(c, false, null);
        } finally {
            if (resetProperties) {
                if (prev == null) {
                    System.getProperties().remove("useJavaUtilZip");
                } else {
                    System.setProperty("useJavaUtilZip", prev);
                }
            }
        }
    }

    File createDir(String name, String... entries) throws Exception {
        File dir = new File(name);
        if (!dir.mkdirs())
            throw new Exception("cannot create directories " + dir);
        for (String e: entries) {
            writeFile(new File(dir, e), e);
        }
        return dir;
    }

    File createJar(String name, String... entries) throws IOException {
        File jar = new File(name);
        OutputStream out = new FileOutputStream(jar);
        try {
            JarOutputStream jos = new JarOutputStream(out);
            for (String e: entries) {
                jos.putNextEntry(new ZipEntry(e));
                jos.write(e.getBytes());
            }
            jos.close();
        } finally {
            out.close();
        }
        return jar;
    }

    File findRtJar() throws Exception {
        File java_home = new File(System.getProperty("java.home"));
        if (java_home.getName().equals("jre"))
            java_home = java_home.getParentFile();
        File rt_jar = new File(new File(new File(java_home, "jre"), "lib"), "rt.jar");
        if (!rt_jar.exists())
            throw new Exception("can't find rt.jar");
        return rt_jar;
    }

    byte[] read(InputStream in) throws IOException {
        byte[] data = new byte[1024];
        int offset = 0;
        try {
            int n;
            while ((n = in.read(data, offset, data.length - offset)) != -1) {
                offset += n;
                if (offset == data.length)
                    data = Arrays.copyOf(data, 2 * data.length);
            }
        } finally {
            in.close();
        }
        return Arrays.copyOf(data, offset);
    }

    void writeFile(File f, String s) throws IOException {
        f.getParentFile().mkdirs();
        FileWriter out = new FileWriter(f);
        try {
            out.write(s);
        } finally {
            out.close();
        }
    }

    void error(String msg) {
        System.err.println(msg);
        errors++;
    }

    int errors;
}
