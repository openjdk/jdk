/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8202624
 * @summary javadoc generates references to enum constructors, which are not documented
 * @library /tools/lib ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main TestEnumConstructor
 */


import java.nio.file.Path;
import java.nio.file.Paths;

import toolbox.ToolBox;

public class TestEnumConstructor extends JavadocTester {

    final ToolBox tb;

    public static void main(String... args) throws Exception {
        TestEnumConstructor tester = new TestEnumConstructor();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    TestEnumConstructor() {
        tb = new ToolBox();
    }

    @Test
    void test1(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createEnum(srcDir);

        Path outDir = base.resolve("out");
        javadoc("-d", outDir.toString(),
                "-private",
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);
        checkOrder("pkg/TestEnum.html",
                "Constructor Summary",
                "Modifier", "Constructor",
                "private", "<a href=\"#%3Cinit%3E(int)\">TestEnum</a></span>&#8203;(int&nbsp;val)");
        checkOutput("index-all.html", true,
                "<a href=\"pkg/TestEnum.html#%3Cinit%3E(int)\">TestEnum(int)</a>");

    }

    @Test
    void test2(Path base) throws Exception {
        Path srcDir = base.resolve("src");
        createEnum(srcDir);

        Path outDir = base.resolve("out");
        javadoc("-d", outDir.toString(),
                "-package",
                "-sourcepath", srcDir.toString(),
                "pkg");

        checkExit(Exit.OK);
        checkOutput("pkg/TestEnum.html", false, "Constructor Summary");
        checkOutput("index-all.html", false,
                "<a href=\"pkg/TestEnum.html#%3Cinit%3E(int)\">TestEnum(int)</a>");
    }

    void createEnum(Path srcDir) throws Exception {
        tb.writeJavaFiles(srcDir,
                "package pkg;\n"
                + "public enum TestEnum{\n"
                + "CONST1(100),CONST2(50),CONST3(10);\n"
                + "private int val;\n"
                + "TestEnum(int val){this.val=val;}\n"
                + "}");
    }
}
