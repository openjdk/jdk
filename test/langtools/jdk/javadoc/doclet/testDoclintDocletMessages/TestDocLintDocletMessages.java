/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8252717
 * @summary  Integrate/merge legacy standard doclet diagnostics and doclint
 * @library  ../../lib /tools/lib
 * @modules  jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestDocLintDocletMessages
 */


import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Some conditions may be detected by both DocLint and the Standard Doclet.
 * This test is to verify that in such cases, only one of those generates
 * a message.
 */
public class TestDocLintDocletMessages extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestDocLintDocletMessages tester = new TestDocLintDocletMessages();
        tester.runTests();
    }

    final ToolBox tb = new ToolBox();

    @Test
    public void testSyntaxError(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * Bad < HTML.
                 * End of comment.
                 */
                public class C {
                    private C() { }
                }
                """);

        var doclintResult = new Result(Exit.ERROR,"C.java:3: error: malformed HTML");
        var docletResult  = new Result(Exit.OK, "C.java:3: warning: invalid input: '<'");

        testSingle(base, "syntax", doclintResult, docletResult);
    }

    @Test
    public void testReferenceNotFoundError(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 * @see DoesNotExist
                 */
                public class C {
                    private C() { }
                }
                """);

        var doclintResult = new Result(Exit.ERROR, "C.java:3: error: reference not found");
        var docletResult  = new Result(Exit.OK, "C.java:3: warning: Tag @see: reference not found: DoesNotExist");

        testSingle(base, "reference", doclintResult, docletResult);
    }

    @Test
    public void testParamNotFoundError(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 */
                public class C {
                    /**
                     * Comment.
                     * @param y wrong name
                     */
                    public C(int x) { }
                }
                """);

        var doclintResult = new Result(Exit.ERROR, "C.java:7: error: @param name not found");
        var docletResult  = new Result(Exit.OK, "C.java:7: warning: @param argument \"y\" is not a parameter name.");

        testSingle(base, "reference", doclintResult, docletResult);
    }

    @Test
    public void testParamDuplicateError(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 */
                public class C {
                    /**
                     * Comment.
                     * @param x first
                     * @param x second
                     */
                    public C(int x) { }
                }
                """);

        var doclintResult = new Result(Exit.OK, "C.java:8: warning: @param \"x\" has already been specified");
        var docletResult  = new Result(Exit.OK, "C.java:8: warning: Parameter \"x\" is documented more than once.");

        testSingle(base, "reference", doclintResult, docletResult);
    }

    @Test
    public void testReturnOnVoid(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Comment.
                 */
                public class C {
                    private C() { }
                    /**
                     * Comment.
                     * @return nothing
                     */
                    public void m() { }
                }
                """);

        var doclintResult = new Result(Exit.ERROR, "C.java:8: error: invalid use of @return");
        var docletResult  = new Result(Exit.OK, "C.java:10: warning: @return tag cannot be used in method with void return type.");

        testSingle(base, "reference", doclintResult, docletResult);
    }

    /** Captures an expected exit code and diagnostic message. */
    record Result(Exit exit, String message) { }

    void testSingle(Path base, String group, Result doclintResult, Result docletResult) {
        int index = 1;

        // test options that should trigger the doclint message
        for (String o : List.of("", "-Xdoclint", "-Xdoclint:" + group)) {
            testSingle(base, index++, o.isEmpty() ? List.of() : List.of(o), doclintResult, docletResult);
        }

        // test options that should trigger the doclet message
        for (String o : List.of("-Xdoclint:none", "-Xdoclint:all,-" + group)) {
            testSingle(base, index++, List.of(o), docletResult, doclintResult);
        }
    }

    void testSingle(Path base, int index, List<String> options, Result expect, Result doNotExpect) {
        var allOptions = new ArrayList<String>();
        allOptions.addAll(List.of("-d", base.resolve("out-" + index).toString()));
        allOptions.addAll(options);
        allOptions.addAll(List.of("-noindex", "-nohelp")); // omit unnecessary files
        allOptions.add(base.resolve("src").resolve("C.java").toString());

        javadoc(allOptions.toArray(String[]::new));
        checkExit(expect.exit);

        checkOutput(Output.OUT, true, expect.message);

        // allow that the "other" result might be the same as the main result
        if (!doNotExpect.message.equals(expect.message)) {
            checkOutput(Output.OUT, false, doNotExpect.message);
        }
    }
}