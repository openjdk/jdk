/*
 * Copyright 2009 Google, Inc.  All Rights Reserved.
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      5093723 8202617
 * @summary  REGRESSION: ClassCastException in SingleIndexWriter
 *           Check that links to undocumented members are not generated
 * @library  /tools/lib ../../lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main T5093723
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;


public class T5093723 extends JavadocTester {

    ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        var tester = new T5093723();
        tester.runTests();
    }

    @Test
    public void test(Path base) throws IOException {

        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /** A documented class. */
                public class DocumentedClass extends UndocumentedClass {
                  /** {@link #method} */
                  public void m1() {}
                  /** {@link #publicMethod} */
                  public void m2() {}
                  /** {@link #protectedMethod} */
                  public void m3() {}
                  /** {@link #privateMethod} */
                  public void m4() {}
                }
                """, """
                package p;
                class UndocumentedClass {
                    void method() {}
                    public void publicMethod() {}
                    protected void protectedMethod() {}
                    private void privateMethod() {}
                }
                """);

        javadoc("-d", "out",
                "-Xdoclint:none",
                "-sourcepath",
                src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/DocumentedClass.html",
                true,
                "<div class=\"block\"><code>UndocumentedClass.method()</code></div>",
                "<div class=\"block\"><code>UndocumentedClass.privateMethod()</code></div>");

        checkOutput("p/DocumentedClass.html",
                false,
                "<div class=\"block\"><a href=\"#method()\"><code>method()</code></a></div>",
                "<div class=\"block\"><a href=\"#privateMethod()\"><code>privateMethod()</code></a></div>");

        checkOutput(Output.OUT, true,
                "warning: reference not accessible: privateMethod()",
                "/** {@link #privateMethod} */",
                "warning: reference not accessible: method()",
                "/** {@link #method} */");

        checkOutput(Output.OUT, false,
                "warning: reference not accessible: publicMethod()",
                "warning: reference not accessible: protectedMethod()",
                "/** {@link #publicMethod} */",
                "/** {@link #protectedMethod} */");
    }
}
