/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8286101
 * @summary Support formatting in at-value tag
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build javadoc.tester.*
 * @run main TestValueFormats
 */


import javadoc.tester.JavadocTester;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestValueFormats extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        var tester = new TestValueFormats();
        tester.runTests();
    }

    TestValueFormats() {
        tb = new ToolBox();
    }

    @Test
    public void testValid(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        tb.writeJavaFiles(srcDir,
                """
                    package p;
                    /**
                     * Comment.
                     */
                    public class C {
                        /** The value {@value} is {@value %4x} or {@value "0x%04x"}. */
                        public static final int i65535 = 65535;
                        /** The value {@value} is {@value %5.2f}. */
                        public static final double pi = 3.1415926525;
                    }""");

        Path outDir = base.resolve("out");
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "p");

        checkExit(Exit.OK);

        checkOutput("p/C.html", true,
                """
                    <h3>i65535</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static final</span>&nbsp;<span clas\
                    s="return-type">int</span>&nbsp;<span class="element-name">i65535</span></div>
                    <div class="block">The value 65535 is ffff or 0xffff.</div>""",
                """
                    <h3>pi</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static final</span>&nbsp;<span class="return-type">double</span>&nbsp;<span class="element-name">pi</span></div>
                    <div class="block">The value 3.1415926525 is %5.2f.</div>""".formatted(3.14));
    }

    @Test
    public void testBadFormat(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        tb.writeJavaFiles(srcDir,
                """
                    package p;
                    /**
                     * Comment.
                     */
                    public class C {
                        /** The value {@value} is {@value %a}. */
                        public static final int i65535 = 65535;
                    }""");

        Path outDir = base.resolve("out");
        javadoc("-d", outDir.toString(),
                "-sourcepath", srcDir.toString(),
                "p");

        checkExit(Exit.ERROR);

        checkOutput("p/C.html", true,
                """
                    <h3>i65535</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public static final</span>&nbsp;<span class="return-type">int</span>&nbsp;<span class="element-name">i65535</span></div>
                    <div class="block">The value 65535 is <span class="invalid-tag">invalid format: %a</span>.</div>""");
    }
}
