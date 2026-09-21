/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8186876
 * @summary Test binary to source mapping in completion
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.jdeps/com.sun.tools.javap
 *          jdk.jshell/jdk.jshell:open
 * @build toolbox.ToolBox toolbox.JarTask toolbox.JavacTask
 * @build KullaTesting TestingInputStream Compiler
 * @run junit/othervm/timeout=480 BinaryToSourceCodeMappingTest
 */

import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import jdk.jshell.JShell;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BinaryToSourceCodeMappingTest extends KullaTesting {

    private static Path classesDir;
    private static Path transientClassesDir;
    private static Path srcDir;
    private static Path srcZip;
    private static Path srcZipWithNestedPath;
    private static Path brokenSrcZip;
    private final TestInfo testInfo;
    private final AtomicBoolean closeCalled = new AtomicBoolean();

    public BinaryToSourceCodeMappingTest(TestInfo info) {
        this.testInfo = info;
    }

    @Test
    public void testNull() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      null""");
    }

    @Test
    public void testReturnsNull() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      null""");
    }

    @Test
    public void testSourcesAsDirectory() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      Class javadoc.""");
        assertJavadoc("test.inner.Test.test(|",
                      """
                      void test.inner.Test.test(int i)
                      Test method.
                      @param i param""");
    }

    @Test
    public void testSourcesAsZip() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      Class javadoc.""");
        assertJavadoc("test.inner.Test.test(|",
                      """
                      void test.inner.Test.test(int i)
                      Test method.
                      @param i param""");
    }

    @Test
    public void testSourcesDuplicate() {
        //src.zip and broken-src.zip both available,
        //only the first one should be used:
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      Class javadoc.""");
        assertJavadoc("test.inner.Test.test(|",
                      """
                      void test.inner.Test.test(int i)
                      Test method.
                      @param i param""");
    }

    @Test
    public void testSourcesAsZipNested() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      Class javadoc.""");
        assertJavadoc("test.inner.Test.test(|",
                      """
                      void test.inner.Test.test(int i)
                      Test method.
                      @param i param""");
    }

    @Test
    public void testClassPathModification() {
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      null""");
        getState().addToClasspath(classesDir.toString());
        assertFalse(closeCalled.get());
        assertJavadoc("test.inner.Test|",
                      """
                      test.inner.Test
                      Class javadoc.""");
        assertTrue(closeCalled.get());
        closeCalled.set(false);
    }

    @Test
    public void testClosingAPIUse() {
        getState().addToClasspath(classesDir.toString());
        String input = "test.inner.Test";
        AtomicReference<Supplier<String>> documentation = new AtomicReference<>();

        getAnalysis().completionSuggestions(input, input.length(), (state, suggestions) -> {
            Assertions.assertEquals(1, suggestions.size());
            documentation.set(suggestions.get(0).documentation());
            return List.of("");
        });
        //holding the documentation supplier, and using it later/outside of the convertor is OK;
        //will open the sources, and should also close them:
        assertFalse(closeCalled.get());
        Assertions.assertEquals("Class javadoc.", documentation.get().get());
        assertTrue(closeCalled.get());
        closeCalled.set(false);
    }

    @Override
    public void setUp(Consumer<JShell.Builder> bc) {
        super.setUp(bc.andThen(b -> {
            b.binarySourceMapping(switch (testInfo.getTestMethod().orElseThrow().getName()) {
                case "testNull" -> null;
                case "testReturnsNull" -> _ -> null;
                case "testSourcesAsDirectory" -> p -> classesDir.equals(p) ? List.of(srcDir) : List.of();
                case "testSourcesAsZip" ->
                        p -> classesDir.equals(p) ? List.of(srcZip) : List.of();
                case "testSourcesDuplicate" ->
                        p -> classesDir.equals(p) ? List.of(srcZip, brokenSrcZip) : List.of();
                case "testClassPathModification", "testSourcesAsZipNested", "testClosingAPIUse" -> p -> {
                    if (!classesDir.equals(p)) {
                        return List.of();
                    }
                    try {
                        FileSystem withNested = FileSystems.newFileSystem(srcZipWithNestedPath);
                        class CloseableIterable extends ArrayList<Path> implements AutoCloseable {
                            public CloseableIterable() {
                                super(List.of(withNested.getPath("root")));
                            }
                            @Override
                            public void close() throws Exception {
                                withNested.close();
                                closeCalled.set(true);
                            }
                        }

                        return new CloseableIterable();
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                };
                default -> throw new AssertionError();
            });
        }));
        if ("testClassPathModification".equals(testInfo.getTestMethod().orElseThrow().getName())) {
            addToClasspath(transientClassesDir);
        } else {
            addToClasspath(classesDir);
        }
    }

    @BeforeAll
    public static void beforeAll() {
        Compiler compiler = new Compiler();
        srcDir = Paths.get("src").toAbsolutePath();
        Path testOutDir = Paths.get("classes");
        String input =
                """
                package test.inner;
                ///Class javadoc.
                public class Test {
                    ///Test method.
                    ///@param i param
                    public static void test(int i) {
                    }
                }
                """;
        Path srcFile = srcDir.resolve("test").resolve("inner").resolve("Test.java");
        try {
            Files.createDirectories(srcFile.getParent());
            try (Writer w = Files.newBufferedWriter(srcFile)) {
                w.append(input);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        compiler.compile(testOutDir, input);
        classesDir = compiler.getPath(testOutDir);
        Path transientClasses = Paths.get("transientClasses");
        compiler.compile(transientClasses, input);
        transientClassesDir = compiler.getPath(transientClasses);
        srcZip = Paths.get("src.zip");
        compiler.jar(srcDir, srcZip, srcDir.relativize(srcFile).toString());
        srcZipWithNestedPath = Paths.get("srcWithNestedPath.zip");
        Path srcDir2 = Paths.get("src2").toAbsolutePath();
        Path srcFile2 = srcDir2.resolve("root").resolve("test").resolve("inner").resolve("Test.java");
        try {
            Files.createDirectories(srcFile2.getParent());
            try (Writer w = Files.newBufferedWriter(srcFile2)) {
                w.append(input);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        compiler.jar(srcDir2, srcZipWithNestedPath, srcDir2.relativize(srcFile2).toString());

        brokenSrcZip = Paths.get("broken-src.zip");

        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(brokenSrcZip))) {
            out.putNextEntry(new JarEntry("test/inner/Test.java"));
            out.write("""
                      package test.inner;
                      ///broken
                      public class Test {
                          ///broken
                          ///@param i broken
                          public static void test(int i) {
                          }
                      }
                      """.getBytes());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void tearDownDone() {
        switch(testInfo.getTestMethod().orElseThrow().getName()) {
            case "testSourcesAsZipNested" -> assertTrue(closeCalled.get());
            case "testClassPathModification", "testClosingAPIUse" -> assertFalse(closeCalled.get());
        }
        super.tearDownDone();
    }

    static {
        try {
            //disable reading of paramater names, to improve stability:
            Class<?> analysisClass = Class.forName("jdk.jshell.SourceCodeAnalysisImpl");
            Field params = analysisClass.getDeclaredField("COMPLETION_EXTRA_PARAMETERS");
            params.setAccessible(true);
            params.set(null, List.of());
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
