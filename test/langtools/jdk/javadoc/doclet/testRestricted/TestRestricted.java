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
 * @bug 8316972
 * @summary Add javadoc support for restricted methods
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestRestricted
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestRestricted extends JavadocTester {

    public final ToolBox tb;
    public static void main(String... args) throws Exception {
        var tester = new TestRestricted();
        tester.runTests();
    }

    public TestRestricted() {
        tb = new ToolBox();
    }

    @Test
    public void testRestricted(Path base) throws IOException {
        var src = base.resolve("src");
        tb.writeJavaFiles(src, """
                   package pkg;
                   
                   import jdk.internal.javac.Restricted;
                                            
                   /**
                    * Interface containing restricted methods.
                    */
                   public interface I {
                       
                       /**
                        * First restricted method.
                        */
                       @Restricted
                       public void firstRestrictedMethod();
                       
                       /**
                        * Second restricted method.
                        */
                       @Restricted
                       public int secondRestrictedMethod();
                   }
                   """);

        javadoc("--enable-preview", "-source", System.getProperty("java.specification.version"),
                "--add-exports", "java.base/jdk.internal.javac=ALL-UNNAMED",
                "-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "pkg");
        checkExit(Exit.OK);

        // Test restricted method note in class documentation
        checkOutput("pkg/I.html", true,
                """
                <div class="block"><span class="restricted-label">Restricted.</span></div>
                <div class="block">First restricted method.</div>""",
                        """
                <div class="block"><span class="restricted-label">Restricted.</span></div>
                <div class="block">Second restricted method.</div>""",
                """
                <h3>firstRestrictedMethod</h3>
                <div class="member-signature"><span class="modifiers">sealed</span>&nbsp;<span clas\
                s="return-type">void</span>&nbsp;<span class="element-name">firstRestrictedMethod</\
                span>()</div>
                <div class="restricted-block" id="restricted-firstRestrictedMethod()"><span class="\
                restricted-label"><code>firstRestrictedMethod</code> is a restricted API of the Jav\
                a platform.</span>
                <div class="restricted-comment">Programs can only use <code>firstRestrictedMethod</\
                code> when access to restricted method is enabled.</div>
                <div class="restricted-comment">Restricted methods are unsafe, and, if used incorre\
                ctly, they might crash the JVM or result in memory corruption.</div>
                </div>""",
                """
                <h3>secondRestrictedMethod</h3>
                <div class="member-signature"><span class="modifiers">sealed</span>&nbsp;<span clas\
                s="return-type">int</span>&nbsp;<span class="element-name">secondRestrictedMethod</\
                span>()</div>
                <div class="restricted-block" id="restricted-secondRestrictedMethod()"><span class=\
                "restricted-label"><code>secondRestrictedMethod</code> is a restricted API of the J\
                ava platform.</span>
                <div class="restricted-comment">Programs can only use <code>secondRestrictedMethod<\
                /code> when access to restricted method is enabled.</div>
                <div class="restricted-comment">Restricted methods are unsafe, and, if used incorre\
                ctly, they might crash the JVM or result in memory corruption.</div>
                </div>""");

        // Test restricted methods list
        checkOutput("restricted-list.html", true,
                        """
                <h1 title="Restricted Methods" class="title">Restricted Methods</h1>
                </div>
                <ul class="block-list">
                <li>
                <div id="method">
                <div class="caption"><span>Methods</span></div>
                <div class="summary-table two-column-summary">
                <div class="table-header col-first">Method</div>
                <div class="table-header col-last">Description</div>
                <div class="col-summary-item-name even-row-color"><a href="pkg/I.html#firstRestrict\
                edMethod()">pkg.I.firstRestrictedMethod()</a><sup><a href="pkg/I.html#restricted-fi\
                rstRestrictedMethod()">RESTRICTED</a></sup></div>
                <div class="col-last even-row-color">
                <div class="block">First restricted method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color"><a href="pkg/I.html#secondRestrict\
                edMethod()">pkg.I.secondRestrictedMethod()</a><sup><a href="pkg/I.html#restricted-s\
                econdRestrictedMethod()">RESTRICTED</a></sup></div>
                <div class="col-last odd-row-color">
                <div class="block">Second restricted method.</div>
                </div>""");
    }
}
