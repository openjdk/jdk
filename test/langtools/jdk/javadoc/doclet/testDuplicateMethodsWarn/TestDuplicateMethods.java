/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8177100
 * @summary Test to check for duplicate methods across different inheritance patterns
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestDuplicateMethods
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestDuplicateMethods extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestDuplicateMethods();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path src = Path.of("src");


    TestDuplicateMethods() throws IOException {
        // Diamond class inheritance
        tb.writeJavaFiles(src, """
                package p;
                interface A {
                    /**
                     * JavaDoc for method in interface A.
                     */
                    abstract void testA( );
                }""", """
                package p;
                interface B extends A {
                    /**
                     * JavaDoc for method in interface B.
                     */
                    abstract void testB( );
                }""", """
                package p;
                abstract class C implements A {
                    /**
                     * Inherited JavaDoc for method in class C.
                     */
                    public final void testA( ) {
                        // Do nothing.
                    }
                }""","""
                package p;
                public final class D extends C implements B {
                    /**
                     * Inherited JavaDoc.
                     */
                    public final void testB() {
                        // Do nothing.
                    }
                }
                """);

        // Mirrors the implementation of StringBuilder
        tb.writeJavaFiles(src,
                """
                package sb;
                public interface I {
                    /**
                     * JavaDoc for method in public interface I.
                     */
                    void testI();
                }
                """, """
                package sb;
                abstract class P implements I {
                    /**
                     * Inherited JavaDoc for method in class P.
                     */
                    public final void testI() {
                        // Do nothing.
                    }
                }
                """, """
                package sb;
                public class U extends P implements I {
                    // No overrides
                }
                """
        );

        // Mirrors the implementation of HashMap
        tb.writeJavaFiles(src,
                """
                package hm;
                public interface J {
                    /**
                     * JavaDoc for method in public interface J.
                     */
                    void testJ();
                }
                """,
                """
                package hm;
                public abstract class PubJ implements J {
                    /**
                     * Inherited JavaDoc for method in public abstract class PubJ.
                     */
                    public final void testJ() {
                        // Do nothing.
                    }
                }
                """,
                """
                package hm;
                public class V extends PubJ implements J {
                    // No override
                }
                """
        );
    }

    @Test
    public void testDiamondInheritance(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);
        checkOutput("p/D.html", true,
                """
                <div class="block">Inherited JavaDoc for method in class C.</div>
                """, """
                <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="element-name">testA</span>()</div>
                <div class="block">Inherited JavaDoc for method in class C.</div>
                """
        );

        checkOutput("p/D.html", false, """
                <div class="block">JavaDoc for method in Interface A.</div>""", """
                <div class="member-signature"><span class="return-type">void</span>&nbsp;<span class="element-name">testA</span>()</div>
                <div class="block">JavaDoc for method in Interface A.</div>""");


        checkOutput("p/D.html", false,
                """
                <div class="block">JavaDoc for method in interface A.</div>
                """);
    }

    @Test
    public void testStringBuilderInheritance(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "sb");
        checkExit(Exit.OK);

        checkOutput("sb/U.html", false,
                """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-sb.I">Methods inherited from interface&nbsp;<a href="I.html#method-summary" title="interface in sb">I</a></h3>
                <code><a href="I.html#testI()" title="testI()">testI</a></code></div>
                """);

        checkOutput("sb/U.html", true,
                """
                <h3>testI</h3>
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="element-name">testI</span>()</div>
                <div class="block">Inherited JavaDoc for method in class P.</div>
                <dl class="notes">
                <dt>Specified by:</dt>
                <dd><code><a href="I.html#testI()">testI</a></code>&nbsp;in interface&nbsp;<code><a href="I.html" title="interface in sb">I</a></code></dd>
                </dl>""");
    }

    @Test
    public void testHashMapInheritance(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "hm");
        checkExit(Exit.OK);

        checkOutput("hm/V.html", false,
                """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-hm.J">Methods inherited from interface&nbsp;<a href="J.html#method-summary" title="interface in hm">J</a></h3>
                <code><a href="J.html#testJ()" title="testJ()">testJ</a></code></div>""");

        checkOutput("hm/V.html", true,
                """
                <div class="inherited-list">
                <h3 id="methods-inherited-from-class-hm.PubJ">Methods inherited from class&nbsp;<a href="PubJ.html#method-summary" title="class in hm">PubJ</a></h3>
                <code><a href="PubJ.html#testJ()" title="testJ()">testJ</a></code></div>
                """);
    }
}
