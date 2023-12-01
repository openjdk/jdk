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

                   import jdk.internal.javac.PreviewFeature;
                   import jdk.internal.javac.PreviewFeature.Feature;
                   import jdk.internal.javac.Restricted;

                   /**
                    * Interface containing restricted methods.
                    * @see #restrictedMethod()
                    * @see #restrictedPreviewMethod()
                    */
                   public interface I {

                       /**
                        * Restricted method.
                        */
                       @Restricted
                       public void restrictedMethod();

                       /**
                        * Restricted preview method.
                        */
                       @PreviewFeature(feature=Feature.TEST)
                       @Restricted
                       public int restrictedPreviewMethod();
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
                <ul class="tag-list-long">
                <li><a href="#restrictedMethod()"><code>restrictedMethod()</code></a><sup><a href="\
                #restricted-restrictedMethod()">RESTRICTED</a></sup></li>
                <li><a href="#restrictedPreviewMethod()"><code>restrictedPreviewMethod()</code></a>\
                <sup><a href="#preview-restrictedPreviewMethod()">PREVIEW</a></sup>&nbsp;<sup><a hr\
                ef="#restricted-restrictedPreviewMethod()">RESTRICTED</a></sup></li>""",
                        """
                <div class="block"><span class="restricted-label">Restricted.</span></div>
                <div class="block">Restricted method.</div>""",
                        """
                <div class="block"><span class="preview-label">Preview.</span></div>
                <div class="block"><span class="restricted-label">Restricted.</span></div>
                <div class="block">Restricted preview method.</div>""",
                        """
                <h3>restrictedMethod</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">sealed</span>&nbsp;<span clas\
                s="return-type">void</span>&nbsp;<span class="element-name">restrictedMethod</span>\
                ()</div>
                <div class="restricted-block" id="restricted-restrictedMethod()"><span class="restr\
                icted-label"><code>restrictedMethod</code> is a restricted method of the Java platf\
                orm.</span>
                <div class="restricted-comment">Programs can only use <code>restrictedMethod</code>\
                 when access to restricted methods is enabled.</div>
                <div class="restricted-comment">Restricted methods are unsafe, and, if used incorre\
                ctly, might crash the JVM or result in memory corruption.</div>
                </div>""",
                        """
                <h3>restrictedPreviewMethod</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">sealed</span>&nbsp;<span clas\
                s="return-type">int</span>&nbsp;<span class="element-name">restrictedPreviewMethod<\
                /span>()</div>
                <div class="preview-block" id="preview-restrictedPreviewMethod()"><span class="prev\
                iew-label"><code>restrictedPreviewMethod</code> is a preview API of the Java platfo\
                rm.</span>
                <div class="preview-comment">Programs can only use <code>restrictedPreviewMethod</c\
                ode> when preview features are enabled.</div>
                <div class="preview-comment">Preview features may be removed in a future release, o\
                r upgraded to permanent features of the Java platform.</div>
                </div>
                <div class="restricted-block" id="restricted-restrictedPreviewMethod()"><span class\
                ="restricted-label"><code>restrictedPreviewMethod</code> is a restricted method of \
                the Java platform.</span>
                <div class="restricted-comment">Programs can only use <code>restrictedPreviewMethod\
                </code> when access to restricted methods is enabled.</div>
                <div class="restricted-comment">Restricted methods are unsafe, and, if used incorre\
                ctly, might crash the JVM or result in memory corruption.</div>
                </div>""");

        // Test link on index page
        checkOutput("index-all.html", true,
                        """
                <a href="restricted-list.html">Restricted&nbsp;Methods</a>""");

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
                <div class="col-summary-item-name even-row-color"><a href="pkg/I.html#restrictedMet\
                hod()">pkg.I.restrictedMethod()</a><sup><a href="pkg/I.html#restricted-restrictedMe\
                thod()">RESTRICTED</a></sup></div>
                <div class="col-last even-row-color">
                <div class="block">Restricted method.</div>
                </div>
                <div class="col-summary-item-name odd-row-color"><a href="pkg/I.html#restrictedPrev\
                iewMethod()">pkg.I.restrictedPreviewMethod()</a><sup><a href="pkg/I.html#preview-re\
                strictedPreviewMethod()">PREVIEW</a></sup>&nbsp;<sup><a href="pkg/I.html#restricted\
                -restrictedPreviewMethod()">RESTRICTED</a></sup></div>
                <div class="col-last odd-row-color">
                <div class="block">Restricted preview method.</div>
                </div>""");
    }
}
