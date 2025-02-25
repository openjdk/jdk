/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8332497
 * @summary error: javac prints an AssertionError when annotation processing runs on program with module imports
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.JavacTask toolbox.ToolBox toolbox.Task
 * @run main ModuleImportProcessingTest
 */

import toolbox.*;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.nio.file.Path;
import java.util.Set;


public class ModuleImportProcessingTest extends TestRunner {
    final toolbox.ToolBox tb = new ToolBox();
    final String processedSource = """
        import module java.base;
        import java.lang.annotation.*;
        public class Main {
            public static void main(String[] args) {
                List.of();
            }
            @Ann
            private void test() {}
            @Retention(RetentionPolicy.RUNTIME)
            @Target(ElementType.METHOD)
            public @interface Ann {}
        }
        """;

    public ModuleImportProcessingTest() {
        super(System.err);
    }

    public static void main(String[] args) throws Exception {
        new ModuleImportProcessingTest().runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    @Test
    public void test(Path base) throws Exception {
        tb.writeJavaFiles(base, processedSource);
        new toolbox.JavacTask(tb)
                .options(
                        "-processor", AP.class.getName(),
                        "--enable-preview",
                        "-source", Integer.toString(Runtime.version().feature()),
                        "-proc:only"
                )
                .files(tb.findJavaFiles(base))
                .run();
    }

    @SupportedAnnotationTypes("*")
    public static final class AP extends AbstractProcessor {

        @Override
        public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
            return false;
        }

        @Override
        public SourceVersion getSupportedSourceVersion() {
            return SourceVersion.latest();
        }

    }
}