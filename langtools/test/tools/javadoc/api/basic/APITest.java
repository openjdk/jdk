/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;


/*
 * Superclass with utility methods for API tests.
 */
class APITest {
    protected APITest() { }

    /** Marker annotation for test cases. */
    @Retention(RetentionPolicy.RUNTIME)
    @interface Test { }

    /** Invoke all methods annotated with @Test. */
    protected void run() throws Exception {
        for (Method m: getClass().getDeclaredMethods()) {
            Annotation a = m.getAnnotation(Test.class);
            if (a != null) {
                testCount++;
                testName = m.getName();
                System.err.println("test: " + testName);
                try {
                    m.invoke(this, new Object[] { });
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    throw (cause instanceof Exception) ? ((Exception) cause) : e;
                }
                System.err.println();
            }
        }

        if (testCount == 0)
            error("no tests found");

        StringBuilder summary = new StringBuilder();
        if (testCount != 1)
            summary.append(testCount).append(" tests");
        if (errorCount > 0) {
            if (summary.length() > 0) summary.append(", ");
            summary.append(errorCount).append(" errors");
        }
        System.err.println(summary);
        if (errorCount > 0)
            throw new Exception(errorCount + " errors found");
    }

    /**
     * Create a directory in which to store generated doc files.
     * Avoid using the default (current) directory, so that we can
     * be sure that javadoc is writing in the intended location,
     * not a default location.
     */
    protected File getOutDir() {
        File dir = new File(testName);
        dir.mkdirs();
        return dir;
    }

    /**
     * Create a directory in which to store generated doc files.
     * Avoid using the default (current) directory, so that we can
     * be sure that javadoc is writing in the intended location,
     * not a default location.
     */
    protected File getOutDir(String path) {
        File dir = new File(testName, path);
        dir.mkdirs();
        return dir;
    }

    protected JavaFileObject createSimpleJavaFileObject() {
        return createSimpleJavaFileObject("pkg/C", "package pkg; public class C { }");
    }

    protected JavaFileObject createSimpleJavaFileObject(final String binaryName, final String content) {
        return new SimpleJavaFileObject(
                URI.create("myfo:///" + binaryName + ".java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncoding) {
                return content;
            }
        };
    }

    protected void checkFiles(File dir, Set<String> expectFiles) {
        Set<File> files = new HashSet<File>();
        listFiles(dir, files);
        Set<String> foundFiles = new HashSet<String>();
        URI dirURI = dir.toURI();
        for (File f: files)
            foundFiles.add(dirURI.relativize(f.toURI()).getPath());
        checkFiles(foundFiles, expectFiles, dir);
    }

    protected void checkFiles(Path dir, Set<String> expectFiles) throws IOException {
        Set<Path> files = new HashSet<Path>();
        listFiles(dir, files);
        Set<String> foundFiles = new HashSet<String>();
        for (Path f: files) {
            foundFiles.add(dir.relativize(f).toString().replace(f.getFileSystem().getSeparator(), "/"));
        }
        checkFiles(foundFiles, expectFiles, dir);
    }

    private void checkFiles(Set<String> foundFiles, Set<String> expectFiles, Object where) {
        if (!foundFiles.equals(expectFiles)) {
            Set<String> missing = new TreeSet<String>(expectFiles);
            missing.removeAll(foundFiles);
            if (!missing.isEmpty())
                error("the following files were not found in " + where + ": " + missing);
            Set<String> unexpected = new TreeSet<String>(foundFiles);
            unexpected.removeAll(expectFiles);
            if (!unexpected.isEmpty())
                error("the following unexpected files were found in " + where + ": " + unexpected);
        }
    }

    protected void listFiles(File dir, Set<File> files) {
        for (File f: dir.listFiles()) {
            if (f.isDirectory())
                listFiles(f, files);
            else if (f.isFile())
                files.add(f);
        }
    }

    private void listFiles(Path dir, Set<Path> files) throws IOException {
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path f: ds) {
                if (Files.isDirectory(f))
                    listFiles(f, files);
                else if (Files.isRegularFile(f))
                    files.add(f);
            }
        }
    }

    protected void error(String msg) {
        System.err.println("Error: " + msg);
        errorCount++;
    }

    protected int testCount;
    protected int errorCount;

    protected String testName;

    /**
     * Standard files generated by processing a documented class pkg.C.
     */
    protected static Set<String> standardExpectFiles = new HashSet<String>(Arrays.asList(
        "allclasses-frame.html",
        "allclasses-noframe.html",
        "constant-values.html",
        "deprecated-list.html",
        "help-doc.html",
        "index-all.html",
        "index.html",
        "overview-tree.html",
        "package-list",
        "pkg/C.html",
        "pkg/package-frame.html",
        "pkg/package-summary.html",
        "pkg/package-tree.html",
        "resources/background.gif",
        "resources/tab.gif",
        "resources/activetitlebar_end.gif",
        "resources/activetitlebar.gif",
        "resources/titlebar_end.gif",
        "resources/titlebar.gif",
        "script.js",
        "stylesheet.css"
    ));
}

