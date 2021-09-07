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
 * @bug 8269774
 * @summary doclint reports missing javadoc comments for JavaFX properties if the docs are on the property method
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestJavaFXMissingPropComments
 */

import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestJavaFXMissingPropComments extends JavadocTester {

    public static void main(String... args) throws Exception {
        TestJavaFXMissingPropComments tester = new TestJavaFXMissingPropComments();
        tester.runTests(m -> new Object[] { Path.of(m.getName()) });
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testMissingFieldComments(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /** Class comment. */
                public class C {
                    /** Dummy class. */
                    public static class BooleanProperty { }

                    // no comment
                    private BooleanProperty value;

                    /**
                     * The value property (property method comment).
                     * @return the property object
                     */
                    public BooleanProperty valueProperty() {
                        return value;
                    }

                    // no comment
                    public boolean getValue() {
                        return true;
                    }
                }
                """);

        javadoc("-d", base.resolve("api").toString(),
                "--javafx",
                "--disable-javafx-strict-checks",
                "-sourcepath", src.toString(),
                "p"
                );
        checkExit(Exit.OK);

        // no warnings for any missing comments
        checkOutput(Output.OUT, false,
                "warning: no comment");

        checkOutput("p/C.html", true,
                """
                    <section class="detail" id="getValue()">
                    <h3>getValue</h3>
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type">boolean</span>&nbsp;<span class="element-name">getValue</span\
                    >()</div>
                    <div class="block">Gets the value of the property <code>value</code>.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>The value property (property method comment).</dd>
                    <dt>Returns:</dt>
                    <dd>the value of the property <code>value</code></dd>
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="see-list">
                    <li><a href="#valueProperty()"><code>valueProperty()</code></a></li>
                    </ul>
                    </dd>
                    </dl>
                    </section>"""
                );
    }

    @Test
    public void testWithFieldComments(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /** Class comment. */
                public class C {
                    /** Dummy class. */
                    public static class BooleanProperty { }

                    /** The value property (field comment). */
                    private BooleanProperty value;

                    /**
                     * The value property (property method comment).
                     * @return the property object
                     */
                    public BooleanProperty valueProperty() {
                        return value;
                    }

                    // no comment
                    public boolean getValue() {
                        return true;
                    }
                }
                """);

        javadoc("-d", base.resolve("api").toString(),
                "--javafx",
                "--disable-javafx-strict-checks",
                "-sourcepath", src.toString(),
                "p"
        );
        checkExit(Exit.OK);

        // no warnings for any missing comments
        checkOutput(Output.OUT, false,
                "warning: no comment");

        checkOutput("p/C.html", true,
                """
                    <section class="detail" id="getValue()">
                    <h3>getValue</h3>
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span c\
                    lass="return-type">boolean</span>&nbsp;<span class="element-name">getValue</span\
                    >()</div>
                    <div class="block">Gets the value of the property <code>value</code>.</div>
                    <dl class="notes">
                    <dt>Property description:</dt>
                    <dd>The value property (field comment).</dd>
                    <dt>Returns:</dt>
                    <dd>the value of the property <code>value</code></dd>
                    <dt>See Also:</dt>
                    <dd>
                    <ul class="see-list">
                    <li><a href="#valueProperty()"><code>valueProperty()</code></a></li>
                    </ul>
                    </dd>
                    </dl>
                    </section>"""
        );
    }
}
