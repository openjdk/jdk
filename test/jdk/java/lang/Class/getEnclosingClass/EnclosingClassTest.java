/*
 * Copyright (c) 2004, 2021 Oracle and/or its affiliates. All rights reserved.
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
 * We have five kinds of classes:
 * a) Top level classes
 * b) Nested classes (static member classes)
 * c) Inner classes (non-static member classes)
 * d) Local classes (named classes declared within a method)
 * e) Anonymous classes
 *
 * Each one can be within a package or not.
 * Kinds b-e can/must be within kinds a-e.
 * This gives us a three dimensional space:
 * 1. dimension: b-e
 * 2. dimension: a-e
 * 3. dimension: packages
 *
 * We make a two dimensional matrix of (b-e)x(a-e) and change the
 * package configuration on that:
 *
 *   b c d e
 * a x x x x
 * b x x x x
 * c o x x x  where o means "not legal"
 * d o x x x
 * e o x x x
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import common.TestMe;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.util.FileUtils;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/*
* @test
* @bug 4992173 4992170
* @library /test/lib
* @modules jdk.compiler
* @build jdk.test.lib.process.* EnclosingClassTest
* @run testng/othervm EnclosingClassTest
* @summary Check getEnclosingClass and other methods
* @author Peter von der Ah\u00e9
*/


public class EnclosingClassTest {
    private static final String SRC_DIR = System.getProperty("test.src");
    private static final String ENCLOSING_CLASS_SRC = SRC_DIR + "/EnclosingClass.java";

    @BeforeClass
    public void createEnclosingClasses() throws Throwable {
        Path enclosingPath = Paths.get(ENCLOSING_CLASS_SRC);
        Path pkg1Dir = Paths.get(SRC_DIR + "/pkg1");
        Path pkg2Dir = Paths.get(SRC_DIR+"/pkg1/pkg2");
        Path pkg1File = Paths.get(SRC_DIR + "/pkg1/EnclosingClass.java");
        Path pkg2File = Paths.get(SRC_DIR + "/pkg1/pkg2/EnclosingClass.java");

        if(!Files.notExists(pkg1Dir)) {
            FileUtils.deleteFileTreeWithRetry(pkg1Dir);
        }
        Files.createDirectories(pkg2Dir);
        createAndWriteEnclosingClasses(enclosingPath, pkg1File, "pkg1");
        createAndWriteEnclosingClasses(enclosingPath, pkg2File, "pkg1.pkg2");

        String javacPath = JDKToolFinder.getJDKTool("javac");
        OutputAnalyzer outputAnalyzer = ProcessTools.executeCommand(javacPath, "-d", System.getProperty("test.classes", "."),
                SRC_DIR + "/EnclosingClass.java", SRC_DIR + "/pkg1/EnclosingClass.java", SRC_DIR + "/pkg1/pkg2/EnclosingClass.java");
        outputAnalyzer.shouldHaveExitValue(0);
    }

    @Test
    public void testEnclosingClasses() throws Throwable {
        test(Class.forName("EnclosingClass").getDeclaredConstructor().newInstance());
    }

    @Test
    public void testEnclosingClassesInPackage() throws Throwable {
        test(Class.forName("pkg1.EnclosingClass").getDeclaredConstructor().newInstance());
    }

    @Test
    public void testEnclosingClassesInNestedPackage() throws Throwable {
        test(Class.forName("pkg1.pkg2.EnclosingClass").getDeclaredConstructor().newInstance());
    }

    @AfterClass
    public void deleteEnclosingClasses() throws IOException {
        Path pkg1Dir = Paths.get(SRC_DIR + "/pkg1");
        FileUtils.deleteFileTreeWithRetry(pkg1Dir);
    }

    private void createAndWriteEnclosingClasses(final Path source, final Path target, final String packageName) throws IOException {
        String className = packageName + ".EnclosingClass";
        try (BufferedReader br = new BufferedReader(new FileReader(source.toFile()));
        PrintWriter bw = new PrintWriter(new FileWriter(target.toFile()))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("canonical=\"EnclosingClass")) {
                    line = line.replaceAll("canonical=\"EnclosingClass", "canonical=\"" + className);
                } else if (line.contains("\"class EnclosingClass")) {
                    line = line.replaceAll("\"class EnclosingClass", "\" class " + className);
                } else if (line.contains("//package")) {
                    line = line.replaceAll("//package", "package " + packageName + ";");
                }
                bw.println(line);
            }
        }
    }

    private void info(final Class<?> c, final Class<?> encClass, final String desc) {
        if (!"".equals(desc)) {
            System.out.println(desc + ":");
        }
        System.out.println(c);
        System.out.println("\tis enclosed by:\t\t" + encClass);
        System.out.println("\thas simple name:\t`" + c.getSimpleName() + "'");
        System.out.println("\thas canonical name:\t`" + c.getCanonicalName() + "'");
    }

    private void match(final String actual, final String expected) {
        System.out.println("actual:" + actual + "expected:" + expected);
        assert ((actual == null && expected == null) || actual.trim().equals(expected.trim()));
        System.out.println("\t`" + actual + "' matches expected `" + expected + "'");
    }

    private void check(final Class<?> c, final Class<?> enc,
               final String encName, final String encNameExpected,
               final String simpleName, final String simpleNameExpected,
               final String canonicalName, final String canonicalNameExpected) {
        match(encName, encNameExpected);
        match(simpleName, simpleNameExpected);
        match(canonicalName, canonicalNameExpected);
    }

    private void testClass(final Class<?> c, final TestMe annotation, final Field f) {
        if (Void.class.equals(c)) {
            return;
        }
        Class<?> encClass = c.getEnclosingClass();
        c.getEnclosingMethod(); // make sure it does not crash
        c.getEnclosingConstructor(); // make sure it does not crash
        info(c, encClass, annotation.desc());
        check(c, encClass, "" + encClass, annotation.encl(), c.getSimpleName(), annotation.simple(),
                c.getCanonicalName(), annotation.hasCanonical() ? annotation.canonical() : null);
        if (void.class.equals(c)) {
            return;
        }
        Class<?> array = java.lang.reflect.Array.newInstance(c, 0).getClass();
        check(array, array.getEnclosingClass(), "", "", array.getSimpleName(),
                annotation.simple() + "[]", array.getCanonicalName(), annotation.hasCanonical()
                        ? annotation.canonical() + "[]" : null);
    }

    private void test(final Object tests) {
        for (Field f : tests.getClass().getFields()) {
            TestMe annotation = f.getAnnotation(TestMe.class);
            if (annotation != null) {
                try {
                    testClass((Class<?>) f.get(tests), annotation, f);
                } catch (AssertionError ex) {
                    System.err.println("Error in " + tests.getClass().getName() + "." + f.getName());
                    throw ex;
                } catch (IllegalAccessException ex) {
                    ex.printStackTrace();
                    throw new RuntimeException(ex);
                }
            }
        }
    }
}

