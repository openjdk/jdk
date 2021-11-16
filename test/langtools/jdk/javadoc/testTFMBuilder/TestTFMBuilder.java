/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8276892
 * @summary Provide a way to emulate exceptional situations in FileManager when using JavadocTester
 * @library /tools/lib/ ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestTFMBuilder
 */


import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.nio.file.Path;
import java.util.List;

import javadoc.tester.JavadocTester;
import javadoc.tester.TestJavaFileManagerBuilder;
import toolbox.ToolBox;

/**
 * Tests the {@link TestJavaFileManagerBuilder class}.
 *
 */
// The use of the contraction TFMBuilder is deliberate, to avoid using
// the confusing but otherwise logical name of TestTestJavaFileManagerBuilder
public class TestTFMBuilder extends JavadocTester {
    public static class TestException extends RuntimeException {
        TestException(JavaFileObject jfo) {
            this(jfo.getName());
        }

        TestException(String msg) {
            super(msg);
        }
    }

    public static void main(String... args) throws Exception {
        TestTFMBuilder tester = new TestTFMBuilder();
        tester.setup().runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    private Path srcDir = Path.of("src");
    private Class<?> thisClass = TestTFMBuilder.class;
    private String thisClassName = thisClass.getName();

    TestTFMBuilder setup() throws Exception {
        ToolBox tb = new ToolBox();
        tb.writeJavaFiles(srcDir, """
                package p;
                /** Dummy class, to be read by javadoc. */
                public class C {
                    private C() { }
                }""");
        return this;
    }

    StandardJavaFileManager getFileManager() {
        DocumentationTool dt = ToolProvider.getSystemDocumentationTool();
        return dt.getStandardFileManager(null, null, null);
    }

    @Test
    public void testSimpleDirectUse(Path base) throws Exception {
        try (StandardJavaFileManager fm = getFileManager()) {
            fm.setLocation(StandardLocation.SOURCE_PATH, List.of(Path.of(testSrc).toFile()));

            // obtain a normal file object from the standard file manager
            JavaFileObject someFileObject =
                    fm.getJavaFileForInput(StandardLocation.SOURCE_PATH, thisClassName, JavaFileObject.Kind.SOURCE);

            // build a file manager that throws an exception when someFileObject is read
            StandardJavaFileManager tfm = new TestJavaFileManagerBuilder(fm)
                    .handle(jfo -> jfo.equals(someFileObject),
                            JavaFileObject.class.getMethod("getCharContent", boolean.class),
                            (fo, args) -> {
                                throw new TestException((JavaFileObject) fo);
                            })
                    .build();

            // access the "same" file object via the test file manager
            JavaFileObject someTestFileObject =
                    tfm.getJavaFileForInput(StandardLocation.SOURCE_PATH, thisClassName, JavaFileObject.Kind.SOURCE);

            checking("non-trapped method");
            try {
                out.println("someTestFileObject.getName: " + someTestFileObject.getName());
                passed("method returned normally, as expected");
            } catch (Throwable t) {
                failed("method threw unexpected exception: " + t);
            }

            checking ("trapped method");
            try {
                someTestFileObject.getCharContent(true);
                failed("method returned normally, without throwing an exception");
            } catch (TestException e) {
                String expect = someFileObject.getName();
                String found = e.getMessage();
                if (found.equals(expect)) {
                    passed("method threw exception as expected");
                } else {
                    failed("method throw exception with unexpected message:\n"
                            + "expected: " + expect + "\n"
                            + "   found: " + found);
                }
            } catch (Throwable t) {
                failed("method threw unexpected exception: " + t);
            }
        }
    }

    @Test
    public void testFileManagerRead(Path base) throws Exception {
        try (StandardJavaFileManager fm = getFileManager()) {

            // build a file manager that throws an exception when any *.java is read
            StandardJavaFileManager tfm = new TestJavaFileManagerBuilder(fm)
                    .handle(jfo -> jfo.getName().endsWith(".java"),
                            JavaFileObject.class.getMethod("getCharContent", boolean.class),
                            (fo, args) -> {
                                throw new TestException((JavaFileObject) fo);
                            })
                    .build();

            try {
                setFileManager(tfm);
                javadoc("-d", base.resolve("api").toString(),
                        "-sourcepath", srcDir.toString(),
                        "p");
                checkExit((Exit.ABNORMAL));
                checkOutput(Output.OUT, true,
                        """
                            Loading source files for package p...
                            error: fatal error encountered: ##EXC##: ##FILE##
                            error: Please file a bug against the javadoc tool via the Java bug reporting page"""
                                .replace("##EXC##", TestException.class.getName())
                                .replace("##FILE##", srcDir.resolve("p").resolve("C.java").toString()));
            } finally {
                setFileManager(null);
            }
        }
    }

    @Test
    public void testFileManagerWrite(Path base) throws Exception {
        try (StandardJavaFileManager fm = getFileManager()) {
            Path outDir = base.resolve("api");

            // build a file manager that throws an exception when any file is generated
            StandardJavaFileManager tfm = new TestJavaFileManagerBuilder(fm)
                    .handle(jfo -> fm.asPath(jfo).startsWith(outDir.toAbsolutePath())
                                    && jfo.getName().endsWith(".html"),
                            JavaFileObject.class.getMethod("openOutputStream"),
                            (fo, args) -> {
                                throw new TestException((JavaFileObject) fo);
                            })
                    .build();

            try {
                setFileManager(tfm);
                javadoc("-d", outDir.toString(),
                        "-sourcepath", srcDir.toString(),
                        "p");
                checkExit((Exit.ERROR));
                checkOutput(Output.OUT, true,
                        """
                            Generating ##FILE##...
                            error: An internal exception has occurred.
                              \t(##EXC##: ##FILE##)
                            1 error"""
                                .replace("##EXC##", TestException.class.getName())
                                .replace("##FILE##", outDir.resolve("p").resolve("C.html").toString()));
            } finally {
                setFileManager(null);
            }
        }
    }
}