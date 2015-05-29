/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4914724
 * @summary Test to make sure that "see" tag and "serialField" tag handle supplementary
 *    characters correctly.  This test case needs to be run in en_US locale.
 * @author Naoto Sato
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestSupplementary
 */

import java.util.Locale;

public class TestSupplementary extends JavadocTester {

    public static void main(String... args) throws Exception {
        Locale saveLocale = Locale.getDefault();
        try {
            TestSupplementary tester = new TestSupplementary();
            tester.runTests();
        } finally {
            Locale.setDefault(saveLocale);
        }
    }

    @Test
    void test() {
        javadoc("-locale", "en_US",
                "-d", "out",
                testSrc("C.java"));
        checkExit(Exit.FAILED);

        checkOutput(Output.WARNING, true,
            "C.java:38: warning - Tag @see:illegal character: \"119040\" in \"C#method\ud834\udd00()",
            "C.java:44: warning - illegal character \ud801 in @serialField tag: field\ud801\ud801 int.",
            "C.java:44: warning - illegal character \ud834\udd7b in @serialField tag: \ud834\udd7bfield int.");

        // TODO: do we need to specify the file and line number in these messages?
        checkOutput(Output.WARNING,  false,
            "C.java:14: warning - Tag @see:illegal character",
            "C.java:19: warning - Tag @see:illegal character",
            "C.java:24: warning - Tag @see:illegal character",
            "C.java:31: warning - illegal character");
    }
}
