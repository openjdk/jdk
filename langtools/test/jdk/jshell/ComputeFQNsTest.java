/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8131027
 * @summary Test Get FQNs
 * @library /tools/lib
 * @build KullaTesting TestingInputStream ToolBox Compiler
 * @run testng ComputeFQNsTest
 */

import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import jdk.jshell.SourceCodeAnalysis.QualifiedNames;
import static org.testng.Assert.*;
import org.testng.annotations.Test;

@Test
public class ComputeFQNsTest extends KullaTesting {

    private final Compiler compiler = new Compiler();
    private final Path outDir = Paths.get("ComputeFQNsTest");

    public void testAddImport() throws Exception {
        compiler.compile(outDir, "package test1; public class TestClass { }", "package test2; public class TestClass { }");
        String jarName = "test.jar";
        compiler.jar(outDir, jarName, "test1/TestClass.class", "test2/TestClass.class");
        addToClasspath(compiler.getPath(outDir).resolve(jarName));

        assertInferredFQNs("LinkedList", "java.util.LinkedList");
        assertInferredFQNs("ArrayList", "java.util.ArrayList");
        assertInferredFQNs("TestClass", "test1.TestClass", "test2.TestClass");
        assertInferredFQNs("CharSequence", "CharSequence".length(), true, "java.lang.CharSequence");
        assertInferredFQNs("unresolvable");
        assertInferredFQNs("void test(ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
        assertInferredFQNs("void test(ArrayList l) throws InvocationTargetException", "InvocationTargetException".length(), false, "java.lang.reflect.InvocationTargetException");
        assertInferredFQNs("void test(ArrayList l) { ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
        assertInferredFQNs("<T extends ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
        assertInferredFQNs("Object l = Arrays", "Arrays".length(), false, "java.util.Arrays");
        assertInferredFQNs("class X<T extends ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
        assertInferredFQNs("class X extends ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
        assertInferredFQNs("class X extends java.util.ArrayList<TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X extends java.util.ArrayList<TypeMirror, TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X implements TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X implements TypeMirror, TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X implements java.util.List<TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X implements java.util.List<TypeMirror, TypeElement", "TypeElement".length(), false, "javax.lang.model.element.TypeElement");
        assertInferredFQNs("class X { ArrayList", "ArrayList".length(), false, "java.util.ArrayList");
    }

    @Test(enabled = false) //JDK-8150860
    public void testSuspendIndexing() throws Exception {
        compiler.compile(outDir, "package test; public class FQNTest { }");
        String jarName = "test.jar";
        compiler.jar(outDir, jarName, "test/FQNTest.class");
        Path continueMarkFile = outDir.resolve("continuemark").toAbsolutePath();
        Files.createDirectories(continueMarkFile.getParent());
        try (Writer w = Files.newBufferedWriter(continueMarkFile)) {}

        Path runMarkFile = outDir.resolve("runmark").toAbsolutePath();
        Files.deleteIfExists(runMarkFile);

        getState().sourceCodeAnalysis();

        new Thread() {
            @Override public void run() {
                assertEval("{new java.io.FileOutputStream(\"" + runMarkFile.toAbsolutePath().toString() + "\").close();" +
                           " while (java.nio.file.Files.exists(java.nio.file.Paths.get(\"" + continueMarkFile.toString() + "\"))) Thread.sleep(100); }");
            }
        }.start();

        while (!Files.exists(runMarkFile))
            Thread.sleep(100);

        addToClasspath(compiler.getPath(outDir).resolve(jarName));

        String code = "FQNTest";

        QualifiedNames candidates = getAnalysis().listQualifiedNames(code, code.length());

        assertEquals(candidates.getNames(), Arrays.asList(), "Input: " + code + ", candidates=" + candidates.getNames());
        assertEquals(candidates.isUpToDate(), false, "Input: " + code + ", up-to-date=" + candidates.isUpToDate());

        Files.delete(continueMarkFile);

        waitIndexingFinished();

        candidates = getAnalysis().listQualifiedNames(code, code.length());

        assertEquals(candidates.getNames(), Arrays.asList("test.FQNTest"), "Input: " + code + ", candidates=" + candidates.getNames());
        assertEquals(true, candidates.isUpToDate(), "Input: " + code + ", up-to-date=" + candidates.isUpToDate());
    }

}
