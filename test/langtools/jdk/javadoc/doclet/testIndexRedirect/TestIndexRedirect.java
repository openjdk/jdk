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
 * @bug 8322874
 * @summary Redirection loop in index.html
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main TestIndexRedirect
 */


import java.nio.file.Path;

import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestIndexRedirect extends JavadocTester {

    final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        var tester = new TestIndexRedirect();
        tester.runTests();
    }

    @Test
    public void test(Path base) throws Exception {
        Path src = base.resolve("src");
        Path api = base.resolve("api");

        tb.writeJavaFiles(src,
                "/**  Module m. */ module m { requires java.se; }");

        javadoc("-d", api.toString(),
                "--source-path", src.toString(),
                "--module", "m");
        checkExit(Exit.OK);

        checkOutput("index.html", true,
                "<script type=\"text/javascript\">window.location.replace('m/module-summary.html')</script>",
                "<meta http-equiv=\"Refresh\" content=\"0;m/module-summary.html\">"
                );
    }
}
