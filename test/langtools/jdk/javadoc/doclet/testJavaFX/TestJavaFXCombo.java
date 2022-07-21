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
 * @bug 8270195
 * @summary Add missing links between methods of JavaFX properties
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestJavaFXCombo
 */

import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

/**
 * Combo-test for JavaFX properties and related methods.
 * The test generates instances of a class with various combinations of
 * a property field, property method, getter method and setter method,
 * each in combinations of with and without doc comments.
 * For each instance, it runs javadoc and verifies the generated
 * code and any diagnostics are as expected.
 */
public class TestJavaFXCombo extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestJavaFXCombo tester = new TestJavaFXCombo(args);
        tester.runTests(m -> new Object[] { Path.of(m.getName())});
    }

    ToolBox tb = new ToolBox();
    enum Kind { NONE, NO_COMMENT, COMMENT }

    private final Set<Kind> fieldValues = EnumSet.allOf(Kind.class);
    private final Set<Kind> propertyMethodValues = EnumSet.allOf(Kind.class);
    private final Set<Kind> getterMethodValues = EnumSet.allOf(Kind.class);
    private final Set<Kind> setterMethodValues = EnumSet.allOf(Kind.class);

    TestJavaFXCombo(String... args) {
        // for testing, allow subsets of combinations to be specified
        for (int i = 0; i < args.length; i++) {
            String arg = args[1];
            switch (arg) {
                case "-f" -> set(fieldValues, args[++i]);
                case "-p" -> set(propertyMethodValues, args[++i]);
                case "-g" -> set(getterMethodValues, args[++i]);
                case "-s" -> set(setterMethodValues, args[++i]);
            }
        }

        // A property method is always required for any property,
        propertyMethodValues.remove(Kind.NONE);

    }

    private void set(Set<Kind> set, String values) {
        set.clear();
        for (String v : values.split("[, ]")) {
            set.add(Kind.valueOf(v));
        }
    }

    @Test
    public void test(Path base) throws IOException {
        for (Kind pk : propertyMethodValues) {
            for (Kind fk : fieldValues) {
                for (Kind gk : getterMethodValues) {
                    for (Kind sk: setterMethodValues) {
                        test(base, fk, pk, gk, sk);
                    }
                }
            }
        }
    }

    void test(Path base, Kind fk, Kind pk, Kind gk, Kind sk) throws IOException {
        String description = "Field:" + fk + " Property:" + pk + " Getter:" + gk + " Setter:" + sk;
        out.println("Test: " + description);
        Path sub = base.resolve(String.format("%s-%s-%s-%s", abbrev(fk), abbrev(pk), abbrev(gk), abbrev(sk)));
        Path src = sub.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /** Dummy property class. */
                public class BooleanProperty { }
                """, """
                package p;
                /** Class comment. ## */
                public class C {
                """.replace("##", description)
                + getFieldText(fk)
                + getPropertyMethodText(pk)
                + getGetterMethodText(gk)
                + getSetterMethodText(sk)
                + """
                }
                """
        );

        javadoc("-d", sub.resolve("api").toString(),
                "-javafx",
                "--disable-javafx-strict-checks",
                "-Xdoclint:all,-missing",
                "-nohelp", "-noindex",
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);
        checkField(fk, pk, gk, sk);
        checkGetter(fk, pk, gk, sk);
        checkSetter(fk, pk, gk, sk);
        checkPropertyMethod(fk, pk, gk, sk);
        checkDiags(fk, pk, gk, sk);
    }

    void checkField(Kind fk, Kind pk, Kind gk, Kind sk) {
        // the field is private and so should never show up
        checkOutput("p/C.html", false,
                "field.detail");
    }

    void checkGetter(Kind fk, Kind pk, Kind gk, Kind sk) {
        switch (gk) {
            case NONE ->
                    checkOutput("p/C.html", false,
                            "getExample");

            case NO_COMMENT ->
                    // comment gets auto-created
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="getExample()">
                                <h3>getExample</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type">boolean</span>&nbsp;<span class="element-name">getExample</span>()</div>
                                <div class="block">Gets the value of the <code>example</code> property.</div>
                                <dl class="notes">
                                <dt>Property description:</dt>
                                #DESC#
                                <dt>Returns:</dt>
                                <dd>the value of the <code>example</code> property</dd>
                                #SEE#
                                </dl>
                                </section>
                                """
                                .replace("#DESC#", getPropertyDescription(fk, pk))
                                .replace("#SEE#", getSee(pk, null, sk))
                                .replaceAll("\n\n", "\n")
                            );

            case COMMENT ->
                    // existing comments do not get modified
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="getExample()">
                                <h3>getExample</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type">boolean</span>&nbsp;<span class="element-name">getExample</span>()</div>
                                <div class="block">Getter method description. More getter method description.</div>
                                <dl class="notes">
                                <dt>Returns:</dt>
                                <dd>the <code>example</code> property</dd>
                                </dl>
                                </section>
                                    """);
        }
    }

    void checkSetter(Kind fk, Kind pk, Kind gk, Kind sk) {
        switch (sk) {
            case NONE ->
                    checkOutput("p/C.html", false,
                            "setExample");

            case NO_COMMENT ->
                    // comment gets auto-created
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="setExample(boolean)">
                                <h3>setExample</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="element-name">setExample</span><wbr><span class="parameters">(boolean&nbsp;b)</span></div>
                                <div class="block">Sets the value of the <code>example</code> property.</div>
                                <dl class="notes">
                                <dt>Property description:</dt>
                                #DESC#
                                <dt>Parameters:</dt>
                                <dd><code>b</code> - the value for the <code>example</code> property</dd>
                                #SEE#
                                </dl>
                                </section>
                                """
                                .replace("#DESC#", getPropertyDescription(fk, pk))
                                .replace("#SEE#", getSee(pk, gk, null))
                                .replaceAll("\n\n", "\n"));

            case COMMENT ->
                    // existing comments do not get modified
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="setExample(boolean)">
                                <h3>setExample</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type">void</span>&nbsp;<span class="element-name">setExample</span><wbr><span class="parameters">(boolean&nbsp;b)</span></div>
                                <div class="block">Setter method description. More setter method description.</div>
                                <dl class="notes">
                                <dt>Parameters:</dt>
                                <dd><code>b</code> - the new value for the property</dd>
                                </dl>
                                </section>
                                """);
        }
    }

    void checkPropertyMethod(Kind fk, Kind pk, Kind gk, Kind sk) {
        switch (pk) {
            case NONE ->
                    // should not happen; there is always a property method
                    throw new IllegalArgumentException();

            case NO_COMMENT ->
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="exampleProperty()">
                                <h3>exampleProperty</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type"><a href="BooleanProperty.html" title="class in p">BooleanProperty</a></span>&nbsp;<span class="element-name">exampleProperty</span>()</div>
                                #PCOMM#
                                <dl class="notes">
                                <dt>Returns:</dt>
                                <dd>the <code>example</code> property</dd>
                                #SEE#
                                </dl>
                                </section>
                                """
                                .replace("#PCOMM#", getPropertyMethodComment(fk, pk))
                                .replace("#SEE#", getSee(null, gk, sk))
                                .replaceAll("\n\n", "\n"));

            case COMMENT ->
                    // @see tags are added to an existing method if it is the primary source of info
                    // for the property (i.e. there is no comment on a corresponding property field.
                    checkOutput("p/C.html", true,
                            """
                                <section class="detail" id="exampleProperty()">
                                <h3>exampleProperty</h3>
                                <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span class="return-type"><a href="BooleanProperty.html" title="class in p">BooleanProperty</a></span>&nbsp;<span class="element-name">exampleProperty</span>()</div>
                                <div class="block">Property method description. More property method description.</div>
                                <dl class="notes">
                                <dt>Returns:</dt>
                                <dd>the <code>example</code> property</dd>
                                #SEE#
                                </dl>
                                </section>
                                """
                                .replace("#SEE#", (fk == Kind.COMMENT ? "" : getSee(null, gk, sk)))
                                .replaceAll("\n\n", "\n"));
        }
    }

    void checkDiags(Kind fk, Kind pk, Kind gk, Kind sk) {
        // A warning is generated if there is a comment on both the property field and property method
        checkOutput(Output.OUT, (fk == Kind.COMMENT && pk == Kind.COMMENT),
                "warning: Duplicate comment for property",
                "Remove the comment on the property field or on this method to suppress this warning.");
    }

    String getPropertyComment(Kind fk, Kind pk) {
        return switch (fk) {
            case NONE, NO_COMMENT ->
                    switch (pk) {
                        case NONE, NO_COMMENT ->
                                "";

                        case COMMENT ->
                                "Property method description. More property method description.";
                    };

            case COMMENT ->
                    "Field description. More field description.";
        };
    }

    String getPropertyDescription(Kind fk, Kind pk) {
        String s = getPropertyComment(fk, pk);
        return s.isEmpty() ? s : "<dd>" + s + "</dd>";
    }

    String getPropertyMethodComment(Kind fk, Kind pk) {
        String s = getPropertyComment(fk, pk);
        return s.isEmpty() ? s : "<div class=\"block\">" + s + "</div>";
    }

    String getSee(Kind pk, Kind gk, Kind sk) {
        StringBuilder sb = new StringBuilder();
        if (gk != null && gk != Kind.NONE) {
            sb.append("""
                <li><a href="#getExample()"><code>getExample()</code></a></li>
                """);
        }
        if (sk != null && sk != Kind.NONE) {
            sb.append("""
                <li><a href="#setExample(boolean)"><code>setExample(boolean)</code></a></li>
                """);
        }
        if (pk != null && pk != Kind.NONE) {
            sb.append("""
                <li><a href="#exampleProperty()"><code>exampleProperty()</code></a></li>
                """);
        }
        return sb.isEmpty() ? "" : """
                <dt>See Also:</dt>
                <dd>
                <ul class="see-list">
                """ + sb + """
                </ul>
                </dd>""";
    }

    String abbrev(Kind k) {
        return k.name().substring(0, 4);
    }

    String getFieldText(Kind fk) {
        return switch (fk) {
            case NONE -> """
                    // no field declaration
                    """;

            case NO_COMMENT -> """
                    // no field comment
                    private BooleanProperty example;
                    """;

            case COMMENT -> """
                    /** Field description. More field description. */
                    private BooleanProperty example;
                    """;
        };
    }

    String getPropertyMethodText(Kind fk) {
        return switch (fk) {
            case NONE -> """
                    // no property method declaration
                    """;

            case NO_COMMENT -> """
                    // no property method comment
                    public BooleanProperty exampleProperty();
                    """;

            case COMMENT -> """
                    /**
                     * Property method description. More property method description.
                     * @return the {@code example} property
                     */
                    public BooleanProperty exampleProperty();
                    """;
        };
    }

    String getGetterMethodText(Kind fk) {
        return switch (fk) {
            case NONE -> """
                    // no getter method declaration
                    """;

            case NO_COMMENT -> """
                    // no getter method comment
                    public boolean getExample();
                    """;

            case COMMENT -> """
                    /**
                     * Getter method description. More getter method description.
                     * @return the {@code example} property
                     */
                    public boolean getExample();
                    """;
        };
    }

    String getSetterMethodText(Kind fk) {
        return switch (fk) {
            case NONE -> """
                    // no setter method declaration
                    """;

            case NO_COMMENT -> """
                    // no setter method comment
                    public void setExample(boolean b);
                    """;

            case COMMENT -> """
                    /**
                     * Setter method description. More setter method description.
                     * @param b the new value for the property
                     */
                    public void setExample(boolean b);
                    """;
        };
    }

}