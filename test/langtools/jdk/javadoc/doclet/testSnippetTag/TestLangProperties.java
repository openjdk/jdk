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
 * @bug 8266666
 * @summary Implementation for snippets
 * @library /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox toolbox.ModuleBuilder builder.ClassBuilder
 * @run main TestLangProperties
 */

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TestLangProperties extends SnippetTester {

    public static void main(String... args) throws Exception {
        new TestLangProperties().runTests(m -> new Object[]{Paths.get(m.getName())});
    }

    @Test
    public void testPositiveOuterMarkup(Path base) throws Exception {
        var testCases = new ArrayList<TestSnippetMarkup.TestCase>();
        for (String whitespace1 : List.of("", " ", "\t"))
            for (String commentIndicator1 : List.of("#", "!"))
                for (String whitespace2 : List.of("", " ", "\t")) {
                    String markup = whitespace1 + commentIndicator1
                            + whitespace2 + "@highlight :";
                    var t = new TestSnippetMarkup.TestCase(
                            """
                                    %s
                                    coffee=espresso
                                    tea=black
                                    """.formatted(markup),
                            """

                                    <span class="bold">coffee=espresso
                                    </span>tea=black
                                    """);
                    testCases.add(t);
                }
        testPositive(base, testCases);
    }

    @Test
    public void testPositiveInnerMarkup(Path base) throws Exception {
        var testCases = new ArrayList<TestSnippetMarkup.TestCase>();
        for (String whitespace1 : List.of("", " ", "\t"))
            for (String commentIndicator1 : List.of("#", "!"))
                for (String whitespace2 : List.of("", " ", "\t"))
                    for (String unrelatedComment : List.of("a comment"))
                        for (String whitespace3 : List.of("", " "))
                            for (String commentIndicator2 : List.of("#", "!")) {
                                String payload = whitespace1 + commentIndicator1 + whitespace2 + unrelatedComment;
                                String markup = payload + whitespace3 + commentIndicator2 + "@highlight :";
                                var t = new TestSnippetMarkup.TestCase(
                                        """
                                                %s
                                                coffee=espresso
                                                tea=black
                                                """.formatted(markup),
                                        """
                                                %s
                                                <span class="bold">coffee=espresso
                                                </span>tea=black
                                                """.formatted(payload));
                                testCases.add(t);
                            }
        testPositive(base, testCases);
    }

    @Test
    public void testPositiveIneffectiveOuterMarkup(Path base) throws Exception {
        var testCases = new ArrayList<TestSnippetMarkup.TestCase>();
        for (String whitespace1 : List.of("", " ", "\t"))
            for (String commentIndicator1 : List.of("#", "!"))
                for (String whitespace2 : List.of("", " ", "\t")) {
                    String ineffectiveMarkup = whitespace1
                            + commentIndicator1 + whitespace2
                            + "@highlight :";
                    var t = new TestSnippetMarkup.TestCase(
                            """
                                    coffee=espresso%s
                                    tea=black
                                    """.formatted(ineffectiveMarkup),
                            """
                                    coffee=espresso%s
                                    tea=black
                                    """.formatted(ineffectiveMarkup));
                    testCases.add(t);
                }
        testPositive(base, testCases);
    }

    @Test
    public void testPositiveIneffectiveInnerMarkup(Path base) throws Exception {
        var testCases = new ArrayList<TestSnippetMarkup.TestCase>();
        for (String whitespace1 : List.of("", " ", "\t"))
            for (String commentIndicator1 : List.of("#", "!"))
                for (String whitespace2 : List.of("", " ", "\t"))
                    for (String unrelatedComment : List.of("a comment"))
                        for (String whitespace3 : List.of("", " "))
                            for (String commentIndicator2 : List.of("#", "!")) {
                                String ineffectiveMarkup = whitespace1
                                        + commentIndicator1 + whitespace2
                                        + unrelatedComment + whitespace3
                                        + commentIndicator2 + "@highlight :";
                                var t = new TestSnippetMarkup.TestCase(
                                        """
                                                coffee=espresso%s
                                                tea=black
                                                """.formatted(ineffectiveMarkup),
                                        """
                                                coffee=espresso%s
                                                tea=black
                                                """.formatted(ineffectiveMarkup));
                                testCases.add(t);
                            }
        testPositive(base, testCases);
    }

    private void testPositive(Path base, List<TestSnippetMarkup.TestCase> testCases)
            throws IOException {
        StringBuilder methods = new StringBuilder();
        forEachNumbered(testCases, (i, n) -> {
            String r = i.region().isBlank() ? "" : "region=" + i.region();
            var methodDef = """

                    /**
                    {@snippet lang="properties" %s:
                    %s}*/
                    public void case%s() {}
                    """.formatted(r, i.input(), n);
            methods.append(methodDef);
        });
        var classDef = """
                public class A {
                %s
                }
                """.formatted(methods.toString());
        Path src = Files.createDirectories(base.resolve("src"));
        tb.writeJavaFiles(src, classDef);
        javadoc("-d", base.resolve("out").toString(),
                "-sourcepath", src.toString(),
                src.resolve("A.java").toString());
        checkExit(Exit.OK);
        checkNoCrashes();
        forEachNumbered(testCases, (t, index) -> {
            String html = """
                    <span class="element-name">case%s</span>()</div>
                    <div class="block">
                    %s
                    </div>""".formatted(index, getSnippetHtmlRepresentation("A.html",
                    t.expectedOutput(), Optional.of("properties")));
            checkOutput("A.html", true, html);
        });
    }
}
