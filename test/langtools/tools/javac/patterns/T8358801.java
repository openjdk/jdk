/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8358801
 * @summary Verify variables introduced by let expressions are correctly undefined
 * @library /tools/lib
 */

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import javax.tools.JavaFileObject;
import javax.tools.ToolProvider;

import com.sun.source.util.JavacTask;
import java.lang.classfile.ClassFile;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.tools.JavaCompiler;

import toolbox.JavaTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class T8358801 extends TestRunner {
    private ToolBox tb;

    public static void main(String... args) throws Exception {
        new T8358801().runTests();
    }

    T8358801() {
        super(System.err);
        tb = new ToolBox();
    }

    public void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testPatternsInJava(Path base) throws Exception {
        Path classes = base.resolve("classes");

        List<JavaFileObject> files = new ArrayList<>();
        files.add(new ToolBox.JavaSource(
            """
            public class Main {
                private boolean test(String s, int i) {
                    if (s.subSequence(0, 1) instanceof Runnable r) {
                        return true;
                    }

                    switch (i) {
                        case 0:
                            String clashing1 = null;
                            String clashing2 = null;
                            String clashing3 = null;
                            String clashing4 = null;
                            return true;
                        default:
                            System.out.println("correct");
                            return true;
                    }
                }

                public static void main(String[] args) {
                    new Main().test("hello", 1);
                }
            }
            """
        ));

        if (Files.exists(classes)) {
            tb.cleanDirectory(classes);
        } else {
            Files.createDirectories(classes);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Iterable<String> options = Arrays.asList("-d", classes.toString());
        JavacTask task = (JavacTask) compiler.getTask(null, null, null, options, null, files);

        task.generate();

        List<VerifyError> errors = ClassFile.of().verify(classes.resolve("Main.class"));

        if (!errors.isEmpty()) {
            throw new AssertionError("verify errors found: " + errors);
        }

        List<String> log =
            new JavaTask(tb).classpath(classes.toString())
                            .className("Main")
                            .run()
                            .writeAll()
                            .getOutputLines(Task.OutputKind.STDOUT);
        List<String> expected = List.of("correct");

        if (!Objects.equals(log, expected)) {
            throw new AssertionError("Incorrect result, expected: " + expected +
                                     ", got: " + log);
        }
    }

}
