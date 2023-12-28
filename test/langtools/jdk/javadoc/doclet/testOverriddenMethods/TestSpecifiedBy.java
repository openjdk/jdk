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
 * @bug 4318787
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestSpecifiedBy
 */

import java.nio.file.Path;
import java.util.List;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestSpecifiedBy extends JavadocTester {

    public static void main(String... args) throws Exception {
        new TestSpecifiedBy().runTests();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void test(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package pkg;

                public abstract class A {
                    public abstract void m();
                }
                """, """
                package pkg;

                public class B extends A {
                    public void m() { }
                }
                """, """
                package pkg;

                public abstract class C extends A {
                    public void m() { }
                }
                """, """
                package pkg;

                public abstract class D extends A {
                    public abstract void m();
                }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "pkg");

        checkExit(Exit.OK);
        // check that the terminology for an overridden abstract method of an
        // abstract class is the same as that of an overridden interface method;
        // no matter who the overrider is, the overridden method should be
        // listed under "Specified by", not "Overrides"
        for (var f : List.of("pkg/B.html", "pkg/C.html", "pkg/D.html"))
            checkOutput(f, true,
                    """
                    <dl class="notes">
                    <dt>Specified by:</dt>
                    <dd><code><a href="A.html#m()">m</a></code>&nbsp;in class&nbsp;\
                    <code><a href="A.html" title="class in pkg">A</a></code></dd>
                    </dl>
                    """);
    }
}
