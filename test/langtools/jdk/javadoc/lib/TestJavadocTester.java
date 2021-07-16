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
 * @bug 8270836
 * @library /tools/lib ../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox
 * @run testng TestJavadocTester
 */

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.testng.Assert.expectThrows;

/*
 * ATTENTION: this test is run using @org.testng.annotations.Test,
 *            not javadoc.tester.JavadocTester.Test
 */
public class TestJavadocTester extends JavadocTester {

    private TestJavadocTester() { }

    @org.testng.annotations.Test
    public void testCheckOutput() throws IOException {
        List<List<String>> listOfStringArguments = List.of(
                List.of("abcd", "abc"),
                List.of("abcde", "a", "abc"),
                List.of("abc", "abc"),
                List.of("", "abcd")
        );
        new ToolBox().writeJavaFiles(Path.of("."), "/** First sentence. */ public class MyClass { }");
        javadoc("-d", "out", "MyClass.java");
        // (1) these checks must throw
        for (Output out : Output.values()) {
            for (boolean expect : new boolean[]{false, true}) {
                for (List<String> args : listOfStringArguments) {
                    String[] strs = args.toArray(new String[0]);
                    expectThrows(IllegalArgumentException.class, () -> checkOutput(out, expect, strs));
                }
            }
        }
        // these must throw too
        for (boolean expect : new boolean[]{false, true}) {
            for (List<String> args : listOfStringArguments) {
                String[] strings = args.toArray(new String[0]);
                expectThrows(IllegalArgumentException.class, () -> checkOutput("MyClass.html", expect, strings));
            }
        }
        // (2) sanity check: these won't throw anything
        for (Output out : Output.values()) {
            for (boolean expect : new boolean[]{false, true}) {
                checkOutput(out, expect, "abcd");
                checkOutput(out, expect, "a", "b");
                checkOutput(out, expect, "");
            }
        }
        // neither will these:
        for (boolean expect : new boolean[]{false, true}) {
            checkOutput("MyClass.html", expect, "abcd");
            checkOutput("MyClass.html", expect, "a", "b");
            checkOutput("MyClass.html", expect, "");
        }
        // checkOrder won't throw when used as a substitute for the above
        // cases of checkOutput that threw
        for (List<String> args : listOfStringArguments) {
            String[] strings = args.toArray(new String[0]);
            checkOrder("MyClass.html", strings);
        }
    }
}
