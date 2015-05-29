/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4652655 4857717 8025633 8026567
 * @summary This test verifies that class cross references work properly.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @build TestClassCrossReferences
 * @run main TestClassCrossReferences
 */

public class TestClassCrossReferences extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestClassCrossReferences tester = new TestClassCrossReferences();
        tester.runTests();
    }

    @Test
    void test() {
        final String uri = "http://java.sun.com/j2se/1.4/docs/api/";

        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-linkoffline", uri, testSrc,
                testSrc("C.java"));
        checkExit(Exit.OK);

        checkOutput("C.html", true,
                "<a href=\"" + uri + "java/math/package-summary.html?is-external=true\">"
                + "<code>Link to math package</code></a>",
                "<a href=\"" + uri + "javax/swing/text/AbstractDocument.AttributeContext.html?is-external=true\" "
                + "title=\"class or interface in javax.swing.text\"><code>Link to AttributeContext innerclass</code></a>",
                "<a href=\"" + uri + "java/math/BigDecimal.html?is-external=true\" "
                + "title=\"class or interface in java.math\"><code>Link to external class BigDecimal</code></a>",
                "<a href=\"" + uri + "java/math/BigInteger.html?is-external=true#gcd-java.math.BigInteger-\" "
                + "title=\"class or interface in java.math\"><code>Link to external member gcd</code></a>",
                "<dl>\n"
                + "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n"
                + "<dd><code>toString</code>&nbsp;in class&nbsp;<code>java.lang.Object</code></dd>\n"
                + "</dl>");
    }

}
