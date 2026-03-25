/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8308590
 * @summary Test basic modeling for value classes
 * @library /tools/lib /tools/javac/lib
 * @modules
 *     jdk.compiler/com.sun.tools.javac.api
 *     jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask JavacTestingAbstractProcessor
 * @run main TestValueClasses
 */

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import java.time.*;

import toolbox.JavacTask;
import toolbox.Task;
import toolbox.Task.Mode;
import toolbox.Task.OutputKind;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class TestValueClasses extends TestRunner {

    protected ToolBox tb;

    TestValueClasses() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String... args) throws Exception {
        new TestValueClasses().runTests();
    }

    /**
     * Run all methods annotated with @Test, and throw an exception if any
     * errors are reported..
     *
     * @throws Exception if any errors occurred
     */
    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    Path[] findJavaFiles(Path... paths) throws IOException {
        return tb.findJavaFiles(paths);
    }

    void checkOutputContains(String log, String... expect) throws Exception {
        for (String e : expect) {
            if (!log.contains(e)) {
                throw new Exception("expected output not found: " + e);
            }
        }
    }

    @Test
    public void testValueClassesProcessor(Path base) throws Exception {
        Path src = base.resolve("src");
        Path r = src.resolve("Test");

        Path classes = base.resolve("classes");

        Files.createDirectories(classes);

        tb.writeJavaFiles(r,
                """
                interface Interface {}

                value class ValueClass {}

                class IdentityClass {}

                value record ValueRecord() {}
                """
        );

        List<String> expected = List.of(
                "- compiler.note.proc.messager: visiting: Interface Modifiers: [abstract]",
                "- compiler.note.proc.messager: visiting: ValueClass Modifiers: [value, final]",
                "- compiler.note.proc.messager:     constructor modifiers: []",
                "- compiler.note.proc.messager: visiting: IdentityClass Modifiers: []",
                "- compiler.note.proc.messager:     constructor modifiers: []",
                "- compiler.note.proc.messager: visiting: ValueRecord Modifiers: [value, final]",
                "- compiler.note.proc.messager:     constructor modifiers: []",
                "- compiler.note.preview.filename: Interface.java, DEFAULT",
                "- compiler.note.preview.recompile"
        );

        for (Mode mode : new Mode[] {Mode.API}) {
            List<String> log = new JavacTask(tb, mode)
                    .options("--enable-preview", "-source", String.valueOf(Runtime.version().feature()), "-processor", ValueClassesProcessor.class.getName(),
                            "-XDrawDiagnostics")
                    .files(findJavaFiles(src))
                    .outdir(classes)
                    .run()
                    .writeAll()
                    .getOutputLines(Task.OutputKind.DIRECT);

            System.out.println("log:" +log);

            if (!expected.equals(log)) {
                if (expected.size() == log.size()) {
                    for (int i = 0; i < expected.size(); i++) {
                        if (!expected.get(i).equals(log.get(i))) {
                            System.err.println("failing at line " + (i + 1));
                            System.err.println("    expecting " + expected.get(i));
                            System.err.println("    found " + log.get(i));
                        }
                    }
                } else {
                    System.err.println("expected and log lists differ in length");
                }
                throw new AssertionError("Unexpected output: " + log);
            }
        }
    }

    public static final class ValueClassesProcessor extends JavacTestingAbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            if (!roundEnv.processingOver()) {
                Messager messager = processingEnv.getMessager();
                ElementScanner scanner = new ValueClassesScanner(messager);
                for(Element rootElement : roundEnv.getRootElements()) {
                    scanner.visit(rootElement);
                }
            }
            return true;
        }

        class ValueClassesScanner extends ElementScanner<Void, Void> {

            Messager messager;

            public ValueClassesScanner(Messager messager) {
                this.messager = messager;
            }

            @Override
            public Void visitType(TypeElement element, Void p) {
                messager.printNote("visiting: " + element.getSimpleName() + " Modifiers: " + element.getModifiers());
                List<? extends Element> enclosedElements = element.getEnclosedElements();
                for (Element elem : enclosedElements) {
                    System.out.println("visiting " + elem.getSimpleName());
                    switch (elem.getSimpleName().toString()) {
                        case "<init>":
                            messager.printNote("    constructor modifiers: " + elem.getModifiers());
                            break;
                        default:
                            break;
                    }
                }
                return super.visitType(element, p);
            }
        }
    }
}
