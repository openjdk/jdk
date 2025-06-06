/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8296420
 * @summary Verify command line help output does not exceed maximum column width
 * @library /tools/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 * @build toolbox.ToolBox toolbox.JavacTask
 * @run main HelpOutputColumnWidthTest
*/

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import toolbox.TestRunner;
import toolbox.ToolBox;
import toolbox.JavacTask;
import toolbox.Task;

public class HelpOutputColumnWidthTest extends TestRunner {

    public static final int MAX_COLUMNS = 84;

    protected ToolBox tb;

    public HelpOutputColumnWidthTest() {
        super(System.err);
        tb = new ToolBox();
    }

    protected void runTests() throws Exception {
        runTests(m -> new Object[] { Paths.get(m.getName()) });
    }

    @Test
    public void testHelp(Path base) throws Exception {
        this.checkColumnWidth("--help");
    }

    @Test
    public void testHelpExtra(Path base) throws Exception {
        this.checkColumnWidth("--help-extra");
    }

    private void checkColumnWidth(String... args) throws Exception {

        // Compile source
        List<String> log = new JavacTask(tb, Task.Mode.CMDLINE)
                .options(args)
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        // Check column width
        final String tooLongLines = log.stream()
          .filter(line -> line.length() > MAX_COLUMNS)
          .map(String::trim)
          .collect(Collectors.joining("]\n    ["));
        if (!tooLongLines.isEmpty())
            throw new Exception("output line(s) too long:\n    [" + tooLongLines + "]");
    }

    public static void main(String... args) throws Exception {
        new HelpOutputColumnWidthTest().runTests();
    }
}
