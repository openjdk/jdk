/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4636655 8196202
 * @summary  Add title attribute to <FRAME> tags for accessibility
 * @author dkramer
 * @library ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build JavadocTester
 * @run main AccessFrameTitle
 */

public class AccessFrameTitle extends JavadocTester {

    public static void main(String... args) throws Exception {
        AccessFrameTitle tester = new AccessFrameTitle();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "--frames",
                "-sourcepath", testSrc,
                "p1", "p2");
        checkExit(Exit.OK);

        // Testing only for the presence of the title attributes.
        // To make this test more robust, only
        // the initial part of each title string is tested for,
        // in case the ending part of the string later changes
        checkOutput("index.html", true,
                "title=\"All classes and interfaces (except non-static nested types)\"",
                "title=\"All Packages\"",
                "title=\"Package, class and interface descriptions\"");
    }
}
