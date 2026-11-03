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
 * @bug 8389654
 * @summary Test --preview-mode option for javap
 * @library /tools/lib
 * @modules jdk.jdeps/com.sun.tools.javap
 * @build toolbox.ToolBox toolbox.JavapTask
 * @run main ${test.main.class}
 */

import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.JavapTask;
import toolbox.Task;
import toolbox.TestRunner;
import toolbox.ToolBox;

public class PreviewModeTest extends TestRunner {
    ToolBox tb = new ToolBox();

    PreviewModeTest() {
        super(System.err);
    }

    public static void main(String... args) throws Exception {
        PreviewModeTest tester = new PreviewModeTest();
        tester.runTests();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testSystemPreviewClass(Path base) throws Exception {
        String outputHeaderLine = new JavapTask(tb)
                .options("--preview-mode", "true", "java.lang.Integer")
                .run()
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT)
                .get(1); // Skip "Compiled from" line
        if (!outputHeaderLine.contains("final value class java.lang.Integer ")) {
            throw new AssertionError(String.format("unexpected output class header line:\n %s", outputHeaderLine));
        }
    }

    @Test
    public void testFailOnBadArgument(Path base) throws Exception {
        Task.Result result = new JavapTask(tb)
                .options("--preview-mode", "java.lang.Integer")
                .run(Task.Expect.FAIL);
        String output = result.getOutput(Task.OutputKind.DIRECT);
        if (!output.contains("invalid use of option: --preview-mode")) {
            throw new AssertionError(String.format("unexpected output:\n %s", output));
        }
    }
}
