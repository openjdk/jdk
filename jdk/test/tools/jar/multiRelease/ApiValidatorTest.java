/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests for API validator.
 * @library /test/lib /lib/testlibrary
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.jartool
 * @build jdk.test.lib.JDKToolFinder jdk.test.lib.Utils jdk.test.lib.process.*
 * @build jdk.testlibrary.FileUtils
 * @build MRTestBase
 * @run testng ApiValidatorTest
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.testlibrary.FileUtils;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ApiValidatorTest extends MRTestBase {

    @Test(dataProvider = "signatureChange")
    public void changeMethodSignature(String sigBase, String sigV10,
                                      boolean isAcceptable,
                                      Method method) throws Throwable {
        Path root = Paths.get(method.getName());
        Path classes = root.resolve("classes");

        String METHOD_SIG = "#SIG";
        String classTemplate =
                "public class C { \n" +
                        "    " + METHOD_SIG + "{ throw new RuntimeException(); };\n" +
                        "}\n";
        String base = classTemplate.replace(METHOD_SIG, sigBase);
        String v10 = classTemplate.replace(METHOD_SIG, sigV10);

        compileTemplate(classes.resolve("base"), base);
        compileTemplate(classes.resolve("v10"), v10);

        String jarfile = root.resolve("test.jar").toString();
        OutputAnalyzer result = jar("cf", jarfile,
                "-C", classes.resolve("base").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(),
                ".");
        if (isAcceptable) {
            result.shouldHaveExitValue(SUCCESS)
                    .shouldBeEmpty();
        } else {
            result.shouldNotHaveExitValue(SUCCESS)
                    .shouldContain("contains a class with different api from earlier version");
        }

        FileUtils.deleteFileTreeWithRetry(root);
    }

    @DataProvider
    Object[][] signatureChange() {
        return new Object[][]{
                {"public int m()", "protected int m()", false},
                {"protected int m()", "public int m()", false},
                {"public int m()", "int m()", false},
                {"protected int m()", "private int m()", false},
                {"private int m()", "int m()", true},
                {"int m()", "private int m()", true},
                {"int m()", "private int m(boolean b)", true},
                {"public int m()", "public int m(int i)", false},
                {"public int m()", "public int k()", false},
                {"public int m()", "private int k()", false},
// @ignore JDK-8172147   {"public int m()", "public boolean m()", false},
// @ignore JDK-8172147   {"public boolean", "public Boolean", false},
// @ignore JDK-8172147   {"public <T> T", "public <T extends String> T", false},
        };
    }

    @Test(dataProvider = "publicAPI")
    public void introducingPublicMembers(String publicAPI,
                                         Method method) throws Throwable {
        Path root = Paths.get(method.getName());
        Path classes = root.resolve("classes");

        String API = "#API";
        String classTemplate =
                "public class C { \n" +
                        "    " + API + "\n" +
                        "    public void method(){ };\n" +
                        "}\n";
        String base = classTemplate.replace(API, "");
        String v10 = classTemplate.replace(API, publicAPI);

        compileTemplate(classes.resolve("base"), base);
        compileTemplate(classes.resolve("v10"), v10);

        String jarfile = root.resolve("test.jar").toString();
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(), ".")
                .shouldNotHaveExitValue(SUCCESS)
                .shouldContain("contains a class with different api from earlier version");

        FileUtils.deleteFileTreeWithRetry(root);
    }

    @DataProvider
    Object[][] publicAPI() {
        return new Object[][]{
// @ignore JDK-8172148  {"protected class Inner { public void m(){ } } "}, // protected inner class
// @ignore JDK-8172148  {"public class Inner { public void m(){ } }"},  // public inner class
// @ignore JDK-8172148  {"public enum E { A; }"},  // public enum
                {"public void m(){ }"}, // public method
                {"protected void m(){ }"}, // protected method
        };
    }

    @Test(dataProvider = "privateAPI")
    public void introducingPrivateMembers(String privateAPI,
                                          Method method) throws Throwable {
        Path root = Paths.get(method.getName());
        Path classes = root.resolve("classes");

        String API = "#API";
        String classTemplate =
                "public class C { \n" +
                        "    " + API + "\n" +
                        "    public void method(){ };\n" +
                        "}\n";
        String base = classTemplate.replace(API, "");
        String v10 = classTemplate.replace(API, privateAPI);

        compileTemplate(classes.resolve("base"), base);
        compileTemplate(classes.resolve("v10"), v10);

        String jarfile = root.resolve("test.jar").toString();
        jar("cf", jarfile, "-C", classes.resolve("base").toString(), ".",
                "--release", "10", "-C", classes.resolve("v10").toString(), ".")
                .shouldHaveExitValue(SUCCESS);
        // add release
        jar("uf", jarfile,
                "--release", "11", "-C", classes.resolve("v10").toString(), ".")
                .shouldHaveExitValue(SUCCESS);
        // replace release
        jar("uf", jarfile,
                "--release", "11", "-C", classes.resolve("v10").toString(), ".")
                .shouldHaveExitValue(SUCCESS);

        FileUtils.deleteFileTreeWithRetry(root);
    }

    @DataProvider
    Object[][] privateAPI() {
        return new Object[][]{
                {"private class Inner { public void m(){ } } "}, // private inner class
                {"class Inner { public void m(){ } }"},  // package private inner class
                {"enum E { A; }"},  // package private enum
                // Local class and private method
                {"private void m(){ class Inner { public void m(){} } Inner i = null; }"},
                {"void m(){ }"}, // package private method
        };
    }

    private void compileTemplate(Path classes, String template) throws Throwable {
        Path classSourceFile = Files.createDirectories(
                classes.getParent().resolve("src").resolve(classes.getFileName()))
                .resolve("C.java");
        Files.write(classSourceFile, template.getBytes());
        javac(classes, classSourceFile);
    }
}