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
 * @bug 4695326 4750173 4920381 8026567
 * @summary Test the declaration of simple tags using -tag. Verify that
 * "-tag name" is a shortcut for "-tag name:a:Name:".  Also verity that
 * you can escape the ":" character with a back slash so that it is not
 * considered a separator when parsing the simple tag argument.
 * @author jamieh
 * @library ../lib
 * @modules jdk.javadoc
 * @build JavadocTester
 * @run main TestSimpleTag
 */

public class TestSimpleTag extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestSimpleTag tester = new TestSimpleTag();
        tester.runTests();
    }

    @Test
    void test() {
        javadoc("-d", "out",
                "-sourcepath", testSrc,
                "-tag", "todo",
                "-tag", "ejb\\:bean:a:EJB Beans:",
                "-tag", "regular:a:Regular Tag:",
                "-tag", "back-slash\\:tag\\\\:a:Back-Slash-Tag:",
                testSrc("C.java"));
        // doclint fails because '\' is not allowed in tag name
        checkExit(Exit.FAILED);

        checkOutput("C.html", true,
                "<span class=\"simpleTagLabel\">Todo:</span>",
                "<span class=\"simpleTagLabel\">EJB Beans:</span>",
                "<span class=\"simpleTagLabel\">Regular Tag:</span>",
                "<span class=\"simpleTagLabel\">Back-Slash-Tag:</span>");
    }
}
