/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8201533
 * @library /tools/lib ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox
 * @run testng TestJavadocTester
 */

import javadoc.tester.JavadocTester;

import static org.testng.Assert.expectThrows;

public class TestJavadocTester extends JavadocTester {

    private TestJavadocTester() { }

    @org.testng.annotations.Test
    public void testCheckOutput() {
        javadoc("--version"); // generate one of the smallest possible outputs for resource's sake
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, true, "abcde", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, true, "abcde", "a", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, true, "abc", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, true, "", "abcd"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, false, "abcde", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, false, "abcde", "a", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, false, "abc", "abc"));
        expectThrows(IllegalArgumentException.class, () -> checkOutput(Output.OUT, false, "", "abcd"));

        // for consistency make sure these won't throw anything
        checkOutput(Output.OUT, false, "abcd");
        checkOutput(Output.OUT, true, "abcd");
    }
}