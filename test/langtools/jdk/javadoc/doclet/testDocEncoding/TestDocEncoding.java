/*
 * Copyright (c) 2002, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * Portions Copyright (c) 2012 IBM Corporation
 */

/*
 * @test
 * @bug      8000743
 * @summary  Run tests on -docencoding to see if the value is
             used for stylesheet as well.
 * @author   jayashree viswanathan
 * @library  ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    JavadocTester
 * @run main TestDocEncoding
 */

import java.nio.charset.Charset;

public class TestDocEncoding extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestDocEncoding tester = new TestDocEncoding();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-docencoding", "Cp930",
                "-sourcepath", testSrc,
                "-notimestamp",
                "pkg");
        checkExit(Exit.OK);

        checkOutput("stylesheet.css", true,
                "body {\n"
                + "    background-color:#ffffff;");

        // reset the charset, for a negative test, that the -docencoding
        // was effective and that the output is not in UTF-8.
        charset = Charset.forName("UTF-8");
        checkOutput("stylesheet.css", false,
                "body {\n"
                + "    background-color:#ffffff;");
    }
}

