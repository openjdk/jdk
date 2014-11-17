/*
 * Copyright (c) 2008, 2014, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6508981
 * @summary cleanup file separator handling in JavacFileManager
 * (This test is specifically to test the new impl of inferBinaryName)
 * @library /tools/lib
 * @build ToolBox p.A
 * @run main TestInferBinaryName
 */

import java.io.*;
import java.util.*;
import javax.tools.*;

import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Options;

import static javax.tools.JavaFileObject.Kind.*;
import static javax.tools.StandardLocation.*;


/**
 * Verify the various implementations of inferBinaryName, but configuring
 * different instances of a file manager, getting a file object, and checking
 * the impl of inferBinaryName for that file object.
 */
public class TestInferBinaryName {
    static final boolean IGNORE_SYMBOL_FILE = false;
    static final boolean USE_SYMBOL_FILE = true;
    static final boolean DONT_USE_ZIP_FILE_INDEX = false;
    static final boolean USE_ZIP_FILE_INDEX = true;

    public static void main(String... args) throws Exception {
        new TestInferBinaryName().run();
    }

    void run() throws Exception {
        //System.err.println(System.getProperties());
        testDirectory();
        testSymbolArchive();

        File testJar = createJar();

        testZipArchive(testJar);
        testZipFileIndexArchive(testJar);
        testZipFileIndexArchive2(testJar);
        if (errors > 0)
            throw new Exception(errors + " error found");
    }

    File createJar() throws IOException {
        File f = new File("test.jar");
        try (JavaFileManager fm = new JavacFileManager(new Context(), false, null)) {
            ToolBox tb = new ToolBox();
            tb.new JarTask(f.getPath())
                .files(fm, StandardLocation.PLATFORM_CLASS_PATH, "java.lang.*")
                .run();
        }
        return f;
    }

    void testDirectory() throws IOException {
        String testClassName = "p.A";
        List<File> testClasses = Arrays.asList(new File(System.getProperty("test.classes")));
        try (JavaFileManager fm =
                getFileManager(testClasses, USE_SYMBOL_FILE, USE_ZIP_FILE_INDEX)) {
            test("testDirectory",
                fm, testClassName, "com.sun.tools.javac.file.RegularFileObject");
        }
    }

    void testSymbolArchive() throws IOException {
        String testClassName = "java.lang.String";
        List<File> path = getPath(System.getProperty("sun.boot.class.path"));
        try (JavaFileManager fm =
                getFileManager(path, USE_SYMBOL_FILE, DONT_USE_ZIP_FILE_INDEX)) {
            test("testSymbolArchive",
                    fm, testClassName, "com.sun.tools.javac.file.SymbolArchive$SymbolFileObject");
        }
    }

    void testZipArchive(File testJar) throws IOException {
        String testClassName = "java.lang.String";
        List<File> path = Arrays.asList(testJar);
        try (JavaFileManager fm =
                getFileManager(path, IGNORE_SYMBOL_FILE, DONT_USE_ZIP_FILE_INDEX)) {
            test("testZipArchive",
                 fm, testClassName, "com.sun.tools.javac.file.ZipArchive$ZipFileObject");
        }
    }

    void testZipFileIndexArchive(File testJar) throws IOException {
        String testClassName = "java.lang.String";
        List<File> path = Arrays.asList(testJar);
        try (JavaFileManager fm =
                getFileManager(path, USE_SYMBOL_FILE, USE_ZIP_FILE_INDEX)) {
            test("testZipFileIndexArchive",
                 fm, testClassName, "com.sun.tools.javac.file.ZipFileIndexArchive$ZipFileIndexFileObject");
        }
    }

    void testZipFileIndexArchive2(File testJar) throws IOException {
        String testClassName = "java.lang.String";
        List<File> path = Arrays.asList(testJar);
        try (JavaFileManager fm =
                getFileManager(path, IGNORE_SYMBOL_FILE, USE_ZIP_FILE_INDEX)) {
            test("testZipFileIndexArchive2",
                 fm, testClassName, "com.sun.tools.javac.file.ZipFileIndexArchive$ZipFileIndexFileObject");
        }
    }

    /**
     * @param testName for debugging
     * @param fm suitably configured file manager
     * @param testClassName the classname to test
     * @param implClassName the expected classname of the JavaFileObject impl,
     *     used for checking that we are checking the expected impl of
     *     inferBinaryName
     */
    void test(String testName,
              JavaFileManager fm, String testClassName, String implClassName) throws IOException {
        JavaFileObject fo = fm.getJavaFileForInput(CLASS_PATH, testClassName, CLASS);
        if (fo == null) {
            System.err.println("Can't find " + testClassName);
            errors++;
            return;
        }

        String cn = fo.getClass().getName();
        String bn = fm.inferBinaryName(CLASS_PATH, fo);
        System.err.println(testName + " " + cn + " " + bn);
        check(cn, implClassName);
        check(bn, testClassName);
        System.err.println("OK");
    }

    JavaFileManager getFileManager(List<File> path,
                                   boolean symFileKind,
                                   boolean zipFileIndexKind)
            throws IOException {
        Context ctx = new Context();
        Options options = Options.instance(ctx);
        options.put("useOptimizedZip",
                Boolean.toString(zipFileIndexKind == USE_ZIP_FILE_INDEX));

        if (symFileKind == IGNORE_SYMBOL_FILE)
            options.put("ignore.symbol.file", "true");
        JavacFileManager fm = new JavacFileManager(ctx, false, null);
        fm.setLocation(CLASS_PATH, path);
        return fm;
    }

    List<File> getPath(String s) {
        List<File> path = new ArrayList<File>();
        for (String f: s.split(File.pathSeparator)) {
            if (f.length() > 0)
                path.add(new File(f));
        }
        //System.err.println("path: " + path);
        return path;
    }

    void check(String found, String expect) {
        if (!found.equals(expect)) {
            System.err.println("Expected: " + expect);
            System.err.println("   Found: " + found);
            errors++;
        }
    }

    private int errors;
}

class A { }

