/*
 * Copyright (c) 2001, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4232882 8014636
 * @summary Javadoc strips all of the leading spaces when the comment
 *    does not begin with a star.  This RFE allows users to
 *    begin their comment without a leading star without leading
 *    spaces stripped
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main LeadingSpaces
 */

public class LeadingSpaces extends JavadocTester {
    /**
     * The entry point of the test.
     * @param args the array of command line arguments.
     * @throws Exception if the test fails
     */
    public static void main(String... args) throws Exception {
        LeadingSpaces tester = new LeadingSpaces();
        tester.runTests();
    }

    @Test
    void testLeadingSpaces() {
        javadoc("-d", "out", "-sourcepath", testSrc,
                testSrc("LeadingSpaces.java"));
        checkExit(Exit.OK);
        checkOutput("LeadingSpaces.html", true,
                  "        1\n"
                + "          2\n"
                + "            3\n"
                + "              4\n"
                + "                5\n"
                + "                  6\n"
                + "                    7");
    }

    /**
       This leading spaces in the &lt;pre&gt; block below should be
       preserved.
       <pre>
        1
          2
            3
              4
                5
                  6
                    7
       </pre>
     */
    public void method(){}

}
