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
 * @bug 8260388
 * @summary Listing (sub)packages at package level of API documentation
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestRelatedPackages
 */

import toolbox.ToolBox;
import javadoc.tester.JavadocTester;

import java.nio.file.Path;
import java.nio.file.Paths;

public class TestRelatedPackages extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestRelatedPackages tester = new TestRelatedPackages();
        tester.runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    @Test
    public void testRelatedPackages(Path base) throws Exception {
        ToolBox tb = new ToolBox();
        tb.writeFile(base.resolve("p1/package-info.java"), "package p1;\n");
        tb.writeFile(base.resolve("p1/s1/A.java"), "package p1.s1; public class A {}\n");
        tb.writeFile(base.resolve("p1/s2/package-info.java"), "package p1.s1;\n");
        tb.writeFile(base.resolve("p1/s3/B.java"), "package p1.s3; public class B {}\n");
        tb.writeFile(base.resolve("p1/s3/t1/package-info.java"), "package p1.s3.t1;\n");
        tb.writeFile(base.resolve("p1/s3/t2/C.java"), "package p1.s3.t2; public class C {}\n");

        javadoc("-d", "out",
                "-sourcepath", base.toString(),
                "-subpackages", "p1");
        checkExit(Exit.OK);
        checkOutput("p1/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="s1/package-summary.html">p1.s1</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="s2/package-summary.html">p1.s2</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    <div class="col-first even-row-color"><a href="s3/package-summary.html">p1.s3</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    </div>""");
        checkOutput("p1/s1/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="../package-summary.html">p1</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="../s2/package-summary.html">p1.s2</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    <div class="col-first even-row-color"><a href="../s3/package-summary.html">p1.s3</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    </div>""");
        checkOutput("p1/s2/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="../package-summary.html">p1</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="../s1/package-summary.html">p1.s1</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    <div class="col-first even-row-color"><a href="../s3/package-summary.html">p1.s3</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    </div>""");
        checkOutput("p1/s3/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="../package-summary.html">p1</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="t1/package-summary.html">p1.s3.t1</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    <div class="col-first even-row-color"><a href="t2/package-summary.html">p1.s3.t2</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="../s1/package-summary.html">p1.s1</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    <div class="col-first even-row-color"><a href="../s2/package-summary.html">p1.s2</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    </div>""");
        checkOutput("p1/s3/t1/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="../package-summary.html">p1.s3</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="../t2/package-summary.html">p1.s3.t2</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    </div>""");
        checkOutput("p1/s3/t2/package-summary.html", true,
                """
                    <div class="caption"><span>Related Packages</span></div>
                    <div class="summary-table two-column-summary">
                    <div class="table-header col-first">Package</div>
                    <div class="table-header col-last">Description</div>
                    <div class="col-first even-row-color"><a href="../package-summary.html">p1.s3</a></div>
                    <div class="col-last even-row-color">&nbsp;</div>
                    <div class="col-first odd-row-color"><a href="../t1/package-summary.html">p1.s3.t1</a></div>
                    <div class="col-last odd-row-color">&nbsp;</div>
                    </div>""");
    }

}
