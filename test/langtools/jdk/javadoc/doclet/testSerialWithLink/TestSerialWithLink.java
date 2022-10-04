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
 * @bug 8288624
 * @summary Test at-serial with {at-link}
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build javadoc.tester.* toolbox.ToolBox
 * @run main TestSerialWithLink
 */

import java.nio.file.Path;

import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestSerialWithLink extends JavadocTester {
    public static void main(String... args) throws Exception {
        TestSerialWithLink tester = new TestSerialWithLink();
        tester.runTests();
    }

    private final ToolBox tb;

    TestSerialWithLink() {
        tb = new ToolBox();
    }

    @Test
    public void testSerialWithLink(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src,
                """
                        package p;
                        import java.io.Serializable;
                        import java.io.ObjectStreamField;
                        /** Comment, */
                        public class C implements Serializable {
                            /** Comment. */
                            C() { }
                            /**
                             * The serial persistent fields for this class.
                             * @serialField item Item An {@link Item} to be serialized.
                             */
                            private static final ObjectStreamField[] serialPersistentFields =
                                { new ObjectStreamField("item", Item.class) };
                            /**
                             * An item that is described in serialPersistentFields.
                             */
                            private Item item;

                            /** A dummy item, not described in serialPersistentFields. */
                            private int dummy;
                        }
                        """, """
                        package p;
                        import java.io.Serializable;
                        /** Comment. */
                        public class Item implements Serializable {
                            /**
                             * Comment.
                             */
                            Item() { }
                        }
                        """);

        javadoc("-d", base.resolve("api").toString(),
                "-sourcepath", src.toString(),
                "--no-platform-links",
                "p");

        checkOutput("serialized-form.html", true,
                """
                    <section class="detail">
                    <h4>Serialized Fields</h4>
                    <ul class="block-list">
                    <li class="block-list">
                    <h5>item</h5>
                    <pre><a href="p/Item.html" title="class in p">Item</a> item</pre>
                    <div class="block">An <a href="p/Item.html" title="class in p"><code>Item</code></a> to be serialized.</div>
                    </li>
                    </ul>
                    </section>""");

    }
}
