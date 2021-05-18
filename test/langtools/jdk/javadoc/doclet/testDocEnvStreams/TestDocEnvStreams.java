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
 * @bug      8267204
 * @summary  Expose access to underlying streams in DocletEnvironment
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.* MyTaglet
 * @run main TestDocEnvStreams
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestDocEnvStreams extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestDocEnvStreams tester = new TestDocEnvStreams();
        tester.runTests(m -> new Object[] { Path.of(m.getName() )});
    }

    ToolBox tb = new ToolBox();

    /**
     * Tests the entry point used by the DocumentationTool API and JavadocTester, in which
     * all output is written to a single specified writer.
     */
    @Test
    public void testSingleStream(Path base) throws IOException {
        test(base, false, Output.OUT, Output.OUT);
    }

    /**
     * Tests the entry point used by the launcher, in which output is written to
     * writers that wrap {@code System.out} and {@code System.err}.
     */
    @Test
    public void testStandardStreams(Path base) throws IOException {
        test(base, true, Output.STDOUT, Output.STDERR);
    }

    void test(Path base, boolean useStdStreams, Output stdOut, Output stdErr) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * First sentence.
                 * abc {@myTaglet} def.
                 */
                public class C { }
                """);

        String testClasses = System.getProperty("test.classes");

        setUseStandardStreams(useStdStreams);
        javadoc("-d", base.resolve("out").toString(),
                "-tagletpath", testClasses,
                "-taglet", "MyTaglet",
                src.resolve("C.java").toString()
        );
        checkExit(Exit.OK);
        checkOutput(stdOut, true,
                "writing to the standard writer");
        checkOutput(stdErr, true,
                "writing to the diagnostic writer");
    }
}