/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8225055 8239804
 * @summary  Record types
 * @library  /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @compile --enable-preview --source ${jdk.version} TestRecordTypes.java
 * @run main/othervm --enable-preview TestRecordTypes
 */


import java.io.IOException;
import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestRecordTypes extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestRecordTypes tester = new TestRecordTypes();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    private final ToolBox tb = new ToolBox();

    // The following constants are set up for use with -linkoffline
    // (but note: JDK 11 does not include java.lang.Record, so expect
    // some 404 broken links until we can update this to a stable version.)
    private static final String externalDocs =
        "https://docs.oracle.com/en/java/javase/11/docs/api";
    private static final String localDocs =
        Path.of(testSrc).resolve("jdk11").toUri().toString();

    @Test
    public void testRecordKeywordUnnamedPackage(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "public record R(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                src.resolve("R.java").toString());
        checkExit(Exit.OK);

        checkOutput("R.html", true,
                "<h1 title=\"Record R\" class=\"title\">Record R</h1>",
                "public record <span class=\"type-name-label\">R</span>",
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E(int)\">R</a></span>&#8203;(int&nbsp;r1)</code>");
    }

    @Test
    public void testRecordKeywordNamedPackage(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public record R(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/R.html", true,
                "<h1 title=\"Record R\" class=\"title\">Record R</h1>",
                "public record <span class=\"type-name-label\">R</span>",
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E(int)\">R</a></span>&#8203;(int&nbsp;r1)</code>");
    }

    @Test
    public void testEmptyRecord(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; public record R() { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/R.html", true,
                "<h1 title=\"Record R\" class=\"title\">Record R</h1>",
                "public record <span class=\"type-name-label\">R</span>",
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E()\">R</a></span>()</code>");
    }

    @Test
    public void testAtParam(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                + " * @param r1 This is a component.\n"
                + " */\n"
                + "public record R(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/R.html", true,
                "<h1 title=\"Record R\" class=\"title\">Record R</h1>",
                "public record <span class=\"type-name-label\">R</span>",
                "<dl class=\"notes\">\n"
                + "<dt>Record Components:</dt>\n"
                + "<dd><code><span id=\"param-r1\">r1</span></code> - This is a component.</dd>\n"
                + "</dl>",
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E(int)\">R</a></span>&#8203;(int&nbsp;r1)</code>");
    }

    @Test
    public void testAtParamTyParam(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                + " * @param r1  This is a component.\n"
                + " * @param <T> This is a type parameter.\n"
                + " */\n"
                + "public record R<T>(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/R.html", true,
                "<h1 title=\"Record R\" class=\"title\">Record R&lt;T&gt;</h1>",
                "public record <span class=\"type-name-label\">R&lt;T&gt;</span>",
                "<dl class=\"notes\">\n"
                + "<dt>Type Parameters:</dt>\n"
                + "<dd><code>T</code> - This is a type parameter.</dd>\n"
                + "<dt>Record Components:</dt>\n"
                + "<dd><code><span id=\"param-r1\">r1</span></code> - This is a component.</dd>\n"
                + "</dl>",
                "<code><span class=\"member-name-link\"><a href=\"#%3Cinit%3E(int)\">R</a></span>&#8203;(int&nbsp;r1)</code>");
    }

    @Test
    public void testGeneratedComments(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                        + " * @param r1  This is a component.\n"
                        + " */\n"
                        + "public record R(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        // While we don't normally test values that just come from resource files,
        // in these cases, we want to verify that something non-empty was put into
        // the documentation for the generated members.
        checkOrder("p/R.html",
                "<section class=\"constructor-summary\" id=\"constructor.summary\">",
                "<a href=\"#%3Cinit%3E(int)\">R</a>",
                "Creates an instance of a <code>R</code> record.",
                "<section class=\"method-summary\" id=\"method.summary\">",
                "<a href=\"#equals(java.lang.Object)\">equals</a>",
                "Indicates whether some other object is \"equal to\" this one.",
                "<a href=\"#hashCode()\">hashCode</a>",
                "Returns a hash code value for this object.",
                "<a href=\"#r1()\">r1</a>",
                "Returns the value of the <a href=\"#param-r1\"><code>r1</code></a> record component.",
                "<a href=\"#toString()\">toString</a>",
                "Returns a string representation of this record.",
                "Method Details",
                "<span class=\"member-name\">toString</span>",
                "Returns a string representation of this record. The representation "
                + "contains the name of the type, followed by the name and value of "
                + "each of the record components.",
                "<span class=\"member-name\">hashCode</span>",
                "Returns a hash code value for this object. The value is derived "
                + "from the hash code of each of the record components.",
                "<span class=\"member-name\">equals</span>",
                "Indicates whether some other object is \"equal to\" this one. "
                + "The objects are equal if the other object is of the same class "
                + "and if all the record components are equal. All components "
                + "in this record are compared with '=='.",
                "<span class=\"member-name\">r1</span>",
                "Returns the value of the <a href=\"#param-r1\"><code>r1</code></a> "
                + "record component."
        );
    }

    @Test
    public void testGeneratedCommentsWithLinkOffline(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                        + " * @param r1  This is a component.\n"
                        + " */\n"
                        + "public record R(int r1) { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "-linkoffline", externalDocs, localDocs,
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        // While we don't normally test values that just come from resource files,
        // in these cases, we want to verify that something non-empty was put into
        // the documentation for the generated members.
        checkOrder("p/R.html",
                "<section class=\"constructor-summary\" id=\"constructor.summary\">",
                "<a href=\"#%3Cinit%3E(int)\">R</a>",
                "Creates an instance of a <code>R</code> record.",
                "<section class=\"method-summary\" id=\"method.summary\">",
                "<a href=\"#equals(java.lang.Object)\">equals</a>",
                "Indicates whether some other object is \"equal to\" this one.",
                "<a href=\"#hashCode()\">hashCode</a>",
                "Returns a hash code value for this object.",
                "<a href=\"#r1()\">r1</a>",
                "Returns the value of the <a href=\"#param-r1\"><code>r1</code></a> record component.",
                "<a href=\"#toString()\">toString</a>",
                "Returns a string representation of this record.",
                "Method Details",
                "<span class=\"member-name\">toString</span>",
                "Returns a string representation of this record. The representation "
                + "contains the name of the type, followed by the name and value of "
                + "each of the record components.",
                "<span class=\"member-name\">hashCode</span>",
                "Returns a hash code value for this object. The value is derived "
                + "from the hash code of each of the record components.",
                "<span class=\"member-name\">equals</span>",
                "Indicates whether some other object is \"equal to\" this one. "
                + "The objects are equal if the other object is of the same class "
                + "and if all the record components are equal. All components "
                + "in this record are compared with '=='.",
                "<span class=\"member-name\">r1</span>",
                "Returns the value of the <a href=\"#param-r1\"><code>r1</code></a> "
                + "record component."
        );
    }

    @Test
    public void testGeneratedEqualsPrimitive(Path base) throws IOException {
        testGeneratedEquals(base, "int a, int b",
             "All components in this record are compared with '=='.");
    }

    @Test
    public void testGeneratedEqualsReference(Path base) throws IOException {
        testGeneratedEquals(base, "Object a, Object b",
             "All components in this record are compared with <code>Objects::equals(Object,Object)</code>");
    }

    @Test
    public void testGeneratedEqualsMixed(Path base) throws IOException {
        testGeneratedEquals(base, "int a, Object b",
             "Reference components are compared with <code>Objects::equals(Object,Object)</code>; "
             + "primitive components are compared with '=='.");
    }

    private void testGeneratedEquals(Path base, String comps, String expect) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                        + " */\n"
                        + "public record R(" + comps + ") { }");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOrder("p/R.html", expect);
    }

    @Test
    public void testUserComments(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                "package p; /** This is record R. \n"
                + " * @param r1  This is a component.\n"
                + " */\n"
                + "public record R(int r1) {\n"
                + "/** User constructor. */ public R { }\n"
                + "/** User equals. */ public boolean equals(Object other) { return false; }\n"
                + "/** User hashCode. */ public int hashCode() { return 0; }\n"
                + "/** User toString. */ public String toString() { return \"\"; }\n"
                + "/** User accessor. */ public int r1() { return r1; }\n"
                + "}");

        javadoc("-d", base.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOrder("p/R.html",
                "<section class=\"constructor-summary\" id=\"constructor.summary\">",
                "<a href=\"#%3Cinit%3E(int)\">R</a>",
                "User constructor.",
                "<section class=\"method-summary\" id=\"method.summary\">",
                "<a href=\"#equals(java.lang.Object)\">equals</a>",
                "User equals.",
                "<a href=\"#hashCode()\">hashCode</a>",
                "User hashCode.",
                "<a href=\"#r1()\">r1</a>",
                "User accessor.",
                "<a href=\"#toString()\">toString</a>",
                "User toString."
        );
    }

    @Test
    public void testExamples(Path base) throws IOException {
        javadoc("-d", base.resolve("out-no-link").toString(),
                "-quiet", "-noindex",
                "-sourcepath", testSrc.toString(),
                "-linksource",
                "--enable-preview", "--source", thisRelease,
                "examples");

        checkExit(Exit.OK);
        javadoc("-d", base.resolve("out-with-link").toString(),
                "-quiet", "-noindex",
                "-sourcepath", testSrc.toString(),
                "-linksource",
                "-linkoffline", externalDocs, localDocs,
                "--enable-preview", "--source", thisRelease,
                "examples");
        checkExit(Exit.OK);
    }

    @Test
    @SuppressWarnings("preview")
    public void testAnnotations(Path base) throws IOException {
        ElementType[] types = {
                ElementType.FIELD,
                ElementType.METHOD,
                ElementType.PARAMETER,
                ElementType.RECORD_COMPONENT
        };
        for (int i = 0; i < (1 << types.length); i++) {
            Set<ElementType> set = EnumSet.noneOf(ElementType.class);
            for (int b = 0; b < types.length; b++) {
                if ((i & (1 << b)) != 0) {
                    set.add(types[b]);
                }
            }
            testAnnotations(base, set);
        }
    }

    void testAnnotations(Path base, Set<ElementType> types) throws IOException {
        out.println("test " + types);
        String name = types.isEmpty() ? "none" : types.stream()
                .map(k -> k.name().toLowerCase(Locale.US))
                .collect(Collectors.joining("-"));
        Path dir = base.resolve(name);
        Path src = dir.resolve("src");
        String target = types.isEmpty() ? "" : types.stream()
                .map(s -> "ElementType." + s)
                .collect(Collectors.joining(", ", "@Target({", "})"));
        tb.writeJavaFiles(src,
                "package p;\n"
                    + "import java.lang.annotation.*;\n"
                    + "@Documented\n"
                    + target + "\n"
                    + " public @interface Anno { }\n",
                "package p; public @interface UndocAnno { }",
                "package p; public record R(@Anno int i) { }\n");

        javadoc("-d", dir.resolve("out").toString(),
                "-quiet", "-noindex",
                "-sourcepath", src.toString(),
                "-private",
                "--enable-preview", "--source", thisRelease,
                "p");
        checkExit(Exit.OK);

        checkOutput("p/R.html", false,
                "UndocAnno");

        Set<ElementType> t = types.isEmpty() ? EnumSet.allOf(ElementType.class) : types;
        String anno = "<a href=\"Anno.html\" title=\"annotation in p\">@Anno</a>";
        String rcAnno = t.contains(ElementType.RECORD_COMPONENT) ? anno + " " : "";
        String fAnno = t.contains(ElementType.FIELD) ? "<span class=\"annotations\">" + anno + "\n</span>" : "";
        String pAnno = t.contains(ElementType.PARAMETER) ? anno + "\n" : "";
        String mAnno= t.contains(ElementType.METHOD) ? "<span class=\"annotations\">" + anno + "\n</span>" : "";

        checkOutput("p/R.html", true,
                "<pre>public record <span class=\"type-name-label\">R</span>("
                        + rcAnno
                        + "int&nbsp;i)\n" +
                        "extends java.lang.Record</pre>",
                "<div class=\"member-signature\">"
                        + fAnno
                        + "<span class=\"modifiers\">private final</span>&nbsp;<span class=\"return-type\">int</span>"
                        + "&nbsp;<span class=\"member-name\">i</span></div>",
                "<div class=\"member-signature\"><span class=\"modifiers\">public</span>&nbsp;<span class=\"member-name\">R</span>"
                        + "&#8203;(<span class=\"arguments\">"
                        + pAnno
                        + "int&nbsp;i)</span></div>",
                "<div class=\"member-signature\">"
                        + mAnno
                        + "<span class=\"modifiers\">public</span>&nbsp;<span class=\"return-type\">int</span>"
                        + "&nbsp;<span class=\"member-name\">i</span>()</div>");

    }
}
