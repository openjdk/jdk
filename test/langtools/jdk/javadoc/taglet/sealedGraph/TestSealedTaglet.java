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
 * @summary General tests for SealedGraph block tag
 * @bug 8380913
 * @library /tools/lib /jdk/javadoc/lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox builder.ClassBuilder
 * @run main ${test.main.class}
 */

import java.nio.file.Path;

import builder.ClassBuilder;
import javadoc.tester.JavadocTester;
import javadoc.tester.JdkTaglets;
import toolbox.ToolBox;

public class TestSealedTaglet extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        var tester = new TestSealedTaglet();
        tester.runTests();
    }

    TestSealedTaglet() {
        tb = new ToolBox();
    }

    @Test
    public void testInvisibleInMiddle(Path base) throws Exception {
        var builtTaglet = JdkTaglets.buildTaglet(tb, base, "SealedGraph");

        Path srcDir = base.resolve("src");
        Path outDir = base.resolve("out");

        tb.writeFile(srcDir.resolve("module-info.java"),
                """
                module test {
                    exports pkg;
                }
                """);
        new ClassBuilder(tb, "pkg.A")
                .setModifiers("public", "abstract", "sealed", "class")
                .setComments("@sealedGraph")
                .addPermits("pkg.B")
                .write(srcDir);
        new ClassBuilder(tb, "pkg.B")
                .setModifiers("abstract", "sealed", "class")
                .addPermits("pkg.C")
                .write(srcDir);
        new ClassBuilder(tb, "pkg.C")
                .setModifiers("public", "final", "class")
                .write(srcDir);

        System.setProperty("sealedDotOutputDir", outDir.toString());
        setAutomaticCheckLinks(false); // Don't check for missing svg
        javadoc("-tagletpath", builtTaglet.toString(),
                "-taglet", "build.tools.taglet.SealedGraph",
                "-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);
        // C is displayed as a direct subtype of A, bypassing B
        checkOutput("test_pkg.A.dot", true, "\"pkg.C\" -> \"pkg.A\";");
    }
}
