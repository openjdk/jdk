/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6493690
 * @summary javadoc should have a javax.tools.Tool service provider
 * @modules jdk.javadoc
 * @build APITest
 * @run main GetTask_DocletClassTest
 * @key randomness
 */

import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;
import javax.tools.DocumentationTool;
import javax.tools.DocumentationTool.DocumentationTask;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.RootDoc;

/**
 * Tests for DocumentationTool.getTask  docletClass  parameter.
 */
public class GetTask_DocletClassTest extends APITest {
    public static void main(String... args) throws Exception {
        new GetTask_DocletClassTest().run();
    }

    /**
     * Verify that an alternate doclet can be specified.
     *
     * There is no standard interface or superclass for a doclet;
     * the only requirement is that it provides static methods that
     * can be invoked via reflection. So, for now, the doclet is
     * specified as a class.
     * Because we cannot create and use a unique instance of the class,
     * we verify that the doclet has been called by having it record
     * (in a static field!) the comment from the last time it was invoked,
     * which is randomly generated each time the test is run.
     */
    @Test
    public void testDoclet() throws Exception {
        Random r = new Random();
        int key = r.nextInt();
        JavaFileObject srcFile = createSimpleJavaFileObject(
                "pkg/C",
                "package pkg; /** " + key + "*/ public class C { }");
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null)) {
            File outDir = getOutDir();
            fm.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(outDir));
            Iterable<? extends JavaFileObject> files = Arrays.asList(srcFile);
            DocumentationTask t = tool.getTask(null, fm, null, TestDoclet.class, null, files);
            if (t.call()) {
                System.err.println("task succeeded");
                if (TestDoclet.lastCaller.equals(String.valueOf(key)))
                    System.err.println("found expected key: " + key);
                else
                    error("Expected key not found");
                checkFiles(outDir, Collections.<String>emptySet());
            } else {
                throw new Exception("task failed");
            }
        }
    }

    public static class TestDoclet {
        static String lastCaller;
        public static boolean start(RootDoc root) {
            lastCaller = root.classNamed("pkg.C").commentText().trim();
            return true;
        }

        public static int optionLength(String option) {
            return 0;  // default is option unknown
        }

        public static boolean validOptions(String options[][],
                DocErrorReporter reporter) {
            return true;  // default is options are valid
        }

        public static LanguageVersion languageVersion() {
            return LanguageVersion.JAVA_1_1;
        }
    }

    /**
     * Verify that exceptions from a doclet are thrown as expected.
     */
    @Test
    public void testBadDoclet() throws Exception {
        JavaFileObject srcFile = createSimpleJavaFileObject();
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager fm = tool.getStandardFileManager(null, null, null)) {
            File outDir = getOutDir();
            fm.setLocation(DocumentationTool.Location.DOCUMENTATION_OUTPUT, Arrays.asList(outDir));
            Iterable<? extends JavaFileObject> files = Arrays.asList(srcFile);
            DocumentationTask t = tool.getTask(null, fm, null, BadDoclet.class, null, files);
            try {
                t.call();
                error("call completed without exception");
            } catch (RuntimeException e) {
                e.printStackTrace();
                Throwable c = e.getCause();
                if (c.getClass() == UnexpectedError.class)
                    System.err.println("exception caught as expected: " + c);
                else
                    throw e;
            }
        }
    }

    public static class UnexpectedError extends Error { }

    public static class BadDoclet {
        public static boolean start(RootDoc root) {
            throw new UnexpectedError();
        }

        public static int optionLength(String option) {
            return 0;  // default is option unknown
        }

        public static boolean validOptions(String options[][],
                DocErrorReporter reporter) {
            return true;  // default is options are valid
        }

        public static LanguageVersion languageVersion() {
            return LanguageVersion.JAVA_1_1;
        }
    }

}

