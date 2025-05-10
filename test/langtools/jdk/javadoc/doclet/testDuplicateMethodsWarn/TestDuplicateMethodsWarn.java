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
 * @summary Test to check for duplicate methods
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestDuplicateMethodsWarn
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestDuplicateMethodsWarn extends JavadocTester {

    public static void main(String... args) throws Exception {
        var tester = new TestDuplicateMethodsWarn();
        tester.runTests();
    }

    ToolBox tb = new ToolBox();
    Path src = Path.of("src");


    TestDuplicateMethodsWarn() throws IOException {
        tb.writeJavaFiles(src, """
                package p;
                 interface A {
                    /**
                    * JavaDoc for method in class A.
                    */
                    abstract void testA ( );
                 }""","""
                 package p;
                 interface B extends A {
                    /**
                    * JavaDoc for method in class B.
                    */
                    abstract void testB ( );
                 }""", """
                 package p;
                 abstract class C implements A {
                    /**
                    * Inherited JavaDoc for method in class C.
                    */
                    public final void testA ( ) {
                       // Do nothing.
                    }
                 }""","""
                 package p;
                 public final class D extends C implements B {
                    /**
                    * Inherited JavaDoc
                    */
                    public final void testB ( ) {
                       // Do nothing.
                    }
                 }
                 """);

    }

    @Test
    public void testDuplicateMethodWarning(Path base) {
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);
        checkOutput("p/D.html", true, """
                <div class="block">Inherited JavaDoc for method in class C.</div>""","""
                <div class="horizontal-scroll">
                <div class="member-signature"><span class="modifiers">public final</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="element-name">testA</span>()</div>
                <div class="block">Inherited JavaDoc for method in class C.</div>""");
        checkOutput("p/D.html", false, """
                <div class="block">JavaDoc for method in class A.</div>""", """
                <div class="member-signature"><span class="return-type">void</span>&nbsp;<span class="element-name">testA</span>()</div>
                <div class="block">JavaDoc for method in class A.</div>""");
    }
}
