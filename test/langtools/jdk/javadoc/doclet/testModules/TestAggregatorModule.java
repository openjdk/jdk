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

/*
 * @test
 * @bug 8322865
 * @summary JavaDoc fails on aggregator modules
 * @modules jdk.javadoc/jdk.javadoc.internal.api
 *          jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @library ../../lib /tools/lib
 * @build toolbox.ToolBox toolbox.ModuleBuilder javadoc.tester.*
 * @run main TestAggregatorModule
 */

import java.nio.file.Files;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.*;

public class TestAggregatorModule extends JavadocTester {
    public static void main(String... args) throws Exception {
        new TestAggregatorModule().runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testSimple_1(Path base) throws Exception {
        Path src = base.resolve("src");
        Path api = base.resolve("api");

        tb.writeJavaFiles(src,
                "/** Module m. */ module m { requires java.se; }");

        javadoc("-d", api.toString(),
                "-sourcepath", src.toString(), // override default sourcepath set by JavadocTester
                src.resolve("module-info.java").toString());
        checkExit(Exit.OK);

        checkOutput(Output.OUT, false,
                "No public");
        checkFiles(true,
                "m/module-summary.html");
    }

    /*
     * This is a variant of testSimple_1 that uses JavadocTask instead of direct use
     * of JavadocTester.javadoc, to avoid setting any value for the source path.
     * In other words, test:  `javadoc -d api path/to/module-info.java`
     */
    @Test
    public void testSimple_2(Path base) throws Exception {
        Path src = base.resolve("src");
        Path api = base.resolve("api");
        Files.createDirectories(api);

        tb.writeJavaFiles(src,
                "/** Module m. */ module m { requires java.se; }");

        var outputLines = new JavadocTask(tb)
                .outdir(api)
                .files(src.resolve("module-info.java"))
                .run(Task.Expect.SUCCESS)
                .writeAll()
                .getOutputLines(Task.OutputKind.DIRECT);

        out.println("Checking for error message");
        if (outputLines.stream().anyMatch(l -> l.contains("No public"))) {
            throw new Exception("unexpected error message");
        }

        out.println("Checking for generated file");
        if (!Files.exists(api.resolve("m").resolve("module-summary.html"))) {
            throw new Exception("expected file not found");
        }
    }
}
