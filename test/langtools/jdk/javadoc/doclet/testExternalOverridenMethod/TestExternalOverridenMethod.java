/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4857717 8025633 8026567
 * @summary Test to make sure that externally overriden and implemented methods
 * are documented properly.  The method should still include "implements" or
 * "overrides" documentation even though the method is external.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester TestExternalOverridenMethod
 * @run main TestExternalOverridenMethod
 */

public class TestExternalOverridenMethod extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestExternalOverridenMethod tester = new TestExternalOverridenMethod();
        tester.runTests();
    }

    @Test
    void test() {
        String uri = "http://java.sun.com/j2se/1.4.1/docs/api";
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-linkoffline", uri, testSrc,
                "pkg");
        checkExit(Exit.OK);

        checkOutput("pkg/XReader.html", true,
                "<dt><span class=\"overrideSpecifyLabel\">Overrides:</span></dt>\n"
                + "<dd><code><a href=\"" + uri + "/java/io/FilterReader.html?is-external=true#read--\" "
                + "title=\"class or interface in java.io\">read</a></code>&nbsp;in class&nbsp;<code>"
                + "<a href=\"" + uri + "/java/io/FilterReader.html?is-external=true\" "
                + "title=\"class or interface in java.io\">FilterReader</a></code></dd>",
                "<dt><span class=\"overrideSpecifyLabel\">Specified by:</span></dt>\n"
                + "<dd><code><a href=\"" + uri + "/java/io/DataInput.html?is-external=true#readInt--\" "
                + "title=\"class or interface in java.io\">readInt</a></code>&nbsp;in interface&nbsp;<code>"
                + "<a href=\"" + uri + "/java/io/DataInput.html?is-external=true\" "
                + "title=\"class or interface in java.io\">DataInput</a></code></dd>"
        );
    }
}
