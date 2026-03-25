/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8308590
 * @summary  value classes
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestValueClasses
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestValueClasses extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestValueClasses();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void testConcreteValueClass(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public value class ValueClass {}");

        javadoc("-d", base.resolve("out").toString(),
                "--enable-preview", "-source", String.valueOf(Runtime.version().feature()),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/ValueClass.html", true,
                """
                <div class="type-signature"><span class="modifiers">public value final class </span><span class="element-name type-name-label">ValueClass</span>
                """);
    }

    @Test
    public void testAbstractValueClass(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public abstract value class ValueClass {}");

        javadoc("-d", base.resolve("out").toString(),
                "--enable-preview", "-source", String.valueOf(Runtime.version().feature()),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/ValueClass.html", true,
                """
                <div class="type-signature"><span class="modifiers">public abstract value class </span><span class="element-name type-name-label">ValueClass</span>
                """);
    }

    @Test
    public void testValueRecord(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public value record ValueRecord() {}");

        javadoc("-d", base.resolve("out").toString(),
                "--enable-preview", "-source", String.valueOf(Runtime.version().feature()),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/ValueRecord.html", true,
                """
                <div class="type-signature"><span class="modifiers">public value record </span><span class="element-name type-name-label">ValueRecord</span>()
                """);
    }
}
