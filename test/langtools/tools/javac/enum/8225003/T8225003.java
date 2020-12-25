/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8225003
 * @summary NPE in Attr.attribIdentAsEnumType
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main T8225003
 */

import java.util.List;
import java.util.Arrays;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;

public class T8225003 extends TestRunner {
    ToolBox tb;

    public T8225003() {
        super(System.err);
        tb = new ToolBox();
    }

    public static void main(String[] args) throws Exception {
        T8225003 t = new T8225003();
        t.runTests();
    }

    @Test
    public void testCyclicEnumNullPointer() throws Exception {
        String processorCode = """
                import java.util.Set;
                import javax.annotation.processing.AbstractProcessor;
                import javax.annotation.processing.RoundEnvironment;
                import javax.lang.model.element.TypeElement;
                public class EmptyProcessor extends AbstractProcessor {
                    @Override
                    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
                        return false;
                    }
                }""";

        String errorCode = """
                public enum T8225003Enum implements T8225003Enum {
                    MISTAKE;
                }""";

        new JavacTask(tb)
                .sources(processorCode)
                .classpath(".")
                .run()
                .writeAll();

        List<String> output = new JavacTask(tb)
                .sources(errorCode)
                .classpath(".")
                .options("-XDrawDiagnostics", "-processor", "EmptyProcessor")
                .run(Task.Expect.FAIL)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);
        List<String> expected = Arrays.asList(
                "T8225003Enum.java:1:37: compiler.err.intf.expected.here",
                "T8225003Enum.java:1:8: compiler.err.cyclic.inheritance: T8225003Enum",
                "T8225003Enum.java:2:5: compiler.err.mod.not.allowed.here: public,static",
                "3 errors");
        tb.checkEqual(expected, output);
    }
}
