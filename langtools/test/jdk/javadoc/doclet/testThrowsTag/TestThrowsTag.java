/*
 * Copyright (c) 2004, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      4985072
 * @summary  Test to make sure that exceptions always show up in the
 *           correct order.
 * @author   jamieh
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestThrowsTag
 */

public class TestThrowsTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestThrowsTag tester = new TestThrowsTag();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "pkg");
        checkExit(Exit.FAILED);  // TODO: investigate why failed

        checkOutput("pkg/C.html", true,
            "<dd><code><a href=\"../pkg/T1.html\" title=\"class in pkg\">T1</a></code> - the first throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T2.html\" title=\"class in pkg\">T2</a></code> - the second throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T3.html\" title=\"class in pkg\">T3</a></code> - the third throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T4.html\" title=\"class in pkg\">T4</a></code> - the fourth throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T5.html\" title=\"class in pkg\">T5</a></code> - the first inherited throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T6.html\" title=\"class in pkg\">T6</a></code> - the second inherited throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T7.html\" title=\"class in pkg\">T7</a></code> - the third inherited throws tag.</dd>\n" +
            "<dd><code><a href=\"../pkg/T8.html\" title=\"class in pkg\">T8</a></code> - the fourth inherited throws tag.</dd>"
        );
    }
}
