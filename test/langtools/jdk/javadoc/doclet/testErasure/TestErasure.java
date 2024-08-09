/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8297879
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestErasure
 */

import java.io.IOException;
import java.nio.file.Path;

import javadoc.tester.JavadocTester;
import toolbox.ToolBox;

public class TestErasure extends JavadocTester {

    private final ToolBox tb = new ToolBox();

    public static void main(String... args) throws Exception {
        new TestErasure().runTests();
    }

    /*
     * Create confusion between:
     *   - a constructor/method type parameter and a like-named class
     *   - similarly named but differently bounded constructor type parameters
     *   - similarly named but differently bounded type parameter in methods
     */
    @Test
    public void test1(Path base) throws IOException {
        Path src = base.resolve("src");
        // - put public class first so that writeJavaFiles is not confused
        //   on the name of the file it should create
        //
        // - an _abstract_ class is used only for convenience: like an interface,
        //   it allows to keep the test minimal, but unlike an interface, it
        //   allows to test constructors
        tb.writeJavaFiles(src, """
                public abstract class Foo {
                    public Foo(T arg) { }
                    public <T extends X> Foo(T arg) { }
                    public <T extends Y> Foo(T arg) { }
                    public abstract T m(T arg);
                    public abstract <T extends X> T m(T arg);
                    public abstract <T extends Y> T m(T arg);
                }
                class T { }
                class X { }
                class Y { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                src.resolve("Foo.java").toString());

        checkExit(Exit.OK);
        // constructors
        checkOutput("Foo.html", true, """
                <section class="constructor-summary" id="constructor-summary">
                <h2>Constructor Summary</h2>
                <div class="caption"><span>Constructors</span></div>
                <div class="summary-table three-column-summary">
                <div class="table-header col-first">Modifier</div>
                <div class="table-header col-second">Constructor</div>
                <div class="table-header col-last">Description</div>
                <div class="col-first even-row-color"><code>&nbsp;</code></div>
                <div class="col-constructor-name even-row-color"><code>\
                <a href="#%3Cinit%3E(T)" class="member-name-link">Foo</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last even-row-color">&nbsp;</div>
                <div class="col-first odd-row-color"><code>&nbsp;&lt;T extends X&gt;<br></code></div>
                <div class="col-constructor-name odd-row-color"><code>\
                <a href="#%3Cinit%3E(X)" class="member-name-link">Foo</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last odd-row-color">&nbsp;</div>
                <div class="col-first even-row-color"><code>&nbsp;&lt;T extends Y&gt;<br></code></div>
                <div class="col-constructor-name even-row-color"><code>\
                <a href="#%3Cinit%3E(Y)" class="member-name-link">Foo</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last even-row-color">&nbsp;</div>
                </div>
                </section>""");
        checkOutput("Foo.html", true, """
                <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                <ol class="toc-list">
                <li><a href="#%3Cinit%3E(T)" tabindex="0">Foo(T)</a></li>
                <li><a href="#%3Cinit%3E(X)" tabindex="0">Foo(T)</a></li>
                <li><a href="#%3Cinit%3E(Y)" tabindex="0">Foo(T)</a></li>
                </ol>
                </li>""");
        checkOutput("index-all.html", true, """
                <dt><a href="Foo.html#%3Cinit%3E(T)" class="member-name-link">Foo(T)</a>\
                 - Constructor for class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#%3Cinit%3E(X)" class="member-name-link">Foo(T)</a>\
                 - Constructor for class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#%3Cinit%3E(Y)" class="member-name-link">Foo(T)</a>\
                 - Constructor for class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>""");
        checkOutput("member-search-index.js", true, """
                {"p":"<Unnamed>","c":"Foo","l":"Foo(T)","u":"%3Cinit%3E(T)"},\
                {"p":"<Unnamed>","c":"Foo","l":"Foo(T)","u":"%3Cinit%3E(X)"},\
                {"p":"<Unnamed>","c":"Foo","l":"Foo(T)","u":"%3Cinit%3E(Y)"}""");
        // methods
        checkOutput("Foo.html", true, """
                <div class="col-first even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code>abstract T</code></div>
                <div class="col-second even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code><a href="#m(T)" class="member-name-link">m</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3">&nbsp;</div>
                <div class="col-first odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code>abstract &lt;T extends X&gt;<br>T</code></div>
                <div class="col-second odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code><a href="#m(X)" class="member-name-link">m</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3">&nbsp;</div>
                <div class="col-first even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code>abstract &lt;T extends Y&gt;<br>T</code></div>
                <div class="col-second even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code><a href="#m(Y)" class="member-name-link">m</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3">&nbsp;</div>""");
        checkOutput("Foo.html", true, """
                <li><a href="#method-detail" tabindex="0">Method Details</a>
                <ol class="toc-list">
                <li><a href="#m(T)" tabindex="0">m(T)</a></li>
                <li><a href="#m(X)" tabindex="0">m(T)</a></li>
                <li><a href="#m(Y)" tabindex="0">m(T)</a></li>
                </ol>
                </li>""");
        checkOutput("index-all.html", true, """
                <dt><a href="Foo.html#m(T)" class="member-name-link">m(T)</a>\
                 - Method in class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#m(X)" class="member-name-link">m(T)</a>\
                 - Method in class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#m(Y)" class="member-name-link">m(T)</a>\
                 - Method in class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>""");
        checkOutput("member-search-index.js", true, """
                {"p":"<Unnamed>","c":"Foo","l":"m(T)"},\
                {"p":"<Unnamed>","c":"Foo","l":"m(T)","u":"m(X)"},\
                {"p":"<Unnamed>","c":"Foo","l":"m(T)","u":"m(Y)"}""");
    }

    /*
     * Create confusion between the class type parameter
     * and a like-named constructor/method type parameter.
     */
    @Test
    public void test2(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                public abstract class Foo<T> {
                    public Foo(T arg) { }
                    public <T extends X> Foo(T arg) { }
                    public abstract T m(T arg);
                    public abstract <T extends X> T m(T arg);
                }
                class X { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                src.resolve("Foo.java").toString());

        checkExit(Exit.OK);
        // constructors
        checkOutput("Foo.html", true, """
                <section class="constructor-summary" id="constructor-summary">
                <h2>Constructor Summary</h2>
                <div class="caption"><span>Constructors</span></div>
                <div class="summary-table three-column-summary">
                <div class="table-header col-first">Modifier</div>
                <div class="table-header col-second">Constructor</div>
                <div class="table-header col-last">Description</div>
                <div class="col-first even-row-color"><code>&nbsp;</code></div>
                <div class="col-constructor-name even-row-color"><code>\
                <a href="#%3Cinit%3E(T)" class="member-name-link">Foo</a>\
                <wbr>(<a href="#type-param-T" title="type parameter in Foo">T</a>&nbsp;arg)</code></div>
                <div class="col-last even-row-color">&nbsp;</div>
                <div class="col-first odd-row-color"><code>&nbsp;&lt;T extends X&gt;<br></code></div>
                <div class="col-constructor-name odd-row-color"><code>\
                <a href="#%3Cinit%3E(X)" class="member-name-link">Foo</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last odd-row-color">&nbsp;</div>
                </div>
                </section>""");
        checkOutput("Foo.html", true, """
                <li><a href="#constructor-detail" tabindex="0">Constructor Details</a>
                <ol class="toc-list">
                <li><a href="#%3Cinit%3E(T)" tabindex="0">Foo(T)</a></li>
                <li><a href="#%3Cinit%3E(X)" tabindex="0">Foo(T)</a></li>
                </ol>
                </li>""");
        checkOutput("index-all.html", true, """
                <dt><a href="Foo.html#%3Cinit%3E(T)" class="member-name-link">Foo(T)</a>\
                 - Constructor for class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#%3Cinit%3E(X)" class="member-name-link">Foo(T)</a>\
                 - Constructor for class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>""");
        checkOutput("member-search-index.js", true, """
                {"p":"<Unnamed>","c":"Foo","l":"Foo(T)","u":"%3Cinit%3E(T)"},\
                {"p":"<Unnamed>","c":"Foo","l":"Foo(T)","u":"%3Cinit%3E(X)"}""");
        // methods
        checkOutput("Foo.html", true, """
                <div class="col-first even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code>abstract <a href="#type-param-T" title="type parameter in Foo">T</a></code></div>
                <div class="col-second even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code><a href="#m(T)" class="member-name-link">m</a>\
                <wbr>(<a href="#type-param-T" title="type parameter in Foo">T</a>&nbsp;arg)</code></div>
                <div class="col-last even-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3">&nbsp;</div>
                <div class="col-first odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code>abstract &lt;T extends X&gt;<br>T</code></div>
                <div class="col-second odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3"><code><a href="#m(X)" class="member-name-link">m</a><wbr>(T&nbsp;arg)</code></div>
                <div class="col-last odd-row-color method-summary-table method-summary-table-tab2 \
                method-summary-table-tab3">&nbsp;</div>""");
        checkOutput("Foo.html", true, """
                <li><a href="#method-detail" tabindex="0">Method Details</a>
                <ol class="toc-list">
                <li><a href="#m(T)" tabindex="0">m(T)</a></li>
                <li><a href="#m(X)" tabindex="0">m(T)</a></li>
                </ol>
                </li>""");
        checkOutput("index-all.html", true, """
                <dt><a href="Foo.html#m(T)" class="member-name-link">m(T)</a>\
                 - Method in class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>
                <dt><a href="Foo.html#m(X)" class="member-name-link">m(T)</a>\
                 - Method in class <a href="Foo.html" title="class in Unnamed Package">Foo</a></dt>
                <dd>&nbsp;</dd>""");
        checkOutput("member-search-index.js", true, """
                {"p":"<Unnamed>","c":"Foo","l":"m(T)"},\
                {"p":"<Unnamed>","c":"Foo","l":"m(T)","u":"m(X)"}""");
    }

    @Test
    public void testNewAndDeprecated(Path base) throws IOException {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                public abstract class Foo {
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public Foo(T arg) { }
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public <T extends X> Foo(T arg) { }
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public <T extends Y> Foo(T arg) { }
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public abstract T m(T arg);
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public abstract <T extends X> T m(T arg);
                    /** @since today */
                    @Deprecated(since="tomorrow")
                    public abstract <T extends Y> T m(T arg);
                }
                class T { }
                class X { }
                class Y { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--since", "today",
                src.resolve("Foo.java").toString());

        checkExit(Exit.OK);
        checkOutput("new-list.html", true, """
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#m(T)">Foo.m<wbr>(T)</a></div>
                <div class="col-second even-row-color">today</div>
                <div class="col-last even-row-color">&nbsp;</div>
                <div class="col-summary-item-name odd-row-color"><a href="Foo.html#m(X)">Foo.m<wbr>(T)</a></div>
                <div class="col-second odd-row-color">today</div>
                <div class="col-last odd-row-color">&nbsp;</div>
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#m(Y)">Foo.m<wbr>(T)</a></div>
                <div class="col-second even-row-color">today</div>
                <div class="col-last even-row-color">&nbsp;</div>""");
        checkOutput("new-list.html", true, """
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#%3Cinit%3E(T)">Foo<wbr>(T)</a></div>
                <div class="col-second even-row-color">today</div>
                <div class="col-last even-row-color">&nbsp;</div>
                <div class="col-summary-item-name odd-row-color"><a href="Foo.html#%3Cinit%3E(X)">Foo<wbr>(T)</a></div>
                <div class="col-second odd-row-color">today</div>
                <div class="col-last odd-row-color">&nbsp;</div>
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#%3Cinit%3E(Y)">Foo<wbr>(T)</a></div>
                <div class="col-second even-row-color">today</div>
                <div class="col-last even-row-color">&nbsp;</div>""");
        checkOutput("deprecated-list.html", true, """
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#m(T)">Foo.m<wbr>(T)</a></div>
                <div class="col-second even-row-color">tomorrow</div>
                <div class="col-last even-row-color"></div>
                <div class="col-summary-item-name odd-row-color"><a href="Foo.html#m(X)">Foo.m<wbr>(T)</a></div>
                <div class="col-second odd-row-color">tomorrow</div>
                <div class="col-last odd-row-color"></div>
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#m(Y)">Foo.m<wbr>(T)</a></div>
                <div class="col-second even-row-color">tomorrow</div>
                <div class="col-last even-row-color"></div>""");
        checkOutput("deprecated-list.html", true, """
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#%3Cinit%3E(T)">Foo<wbr>(T)</a></div>
                <div class="col-second even-row-color">tomorrow</div>
                <div class="col-last even-row-color"></div>
                <div class="col-summary-item-name odd-row-color"><a href="Foo.html#%3Cinit%3E(X)">Foo<wbr>(T)</a></div>
                <div class="col-second odd-row-color">tomorrow</div>
                <div class="col-last odd-row-color"></div>
                <div class="col-summary-item-name even-row-color"><a href="Foo.html#%3Cinit%3E(Y)">Foo<wbr>(T)</a></div>
                <div class="col-second even-row-color">tomorrow</div>
                <div class="col-last even-row-color"></div>""");
    }

    @Test
    public void testPreview(Path base) throws IOException {
        // unlike that for other tests, here we cannot simulate ambiguity between
        // a type parameter and a like-named class, because for that the class
        // needs to be in the unnamed package, otherwise its FQN won't be T
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                import jdk.internal.javac.PreviewFeature;
                public abstract class Foo {
                    @PreviewFeature(feature=PreviewFeature.Feature.TEST)
                    public <T extends X> Foo(T arg) { }
                    @PreviewFeature(feature=PreviewFeature.Feature.TEST)
                    public <T extends Y> Foo(T arg) { }
                    @PreviewFeature(feature=PreviewFeature.Feature.TEST)
                    public abstract <T extends X> T m(T arg);
                    @PreviewFeature(feature=PreviewFeature.Feature.TEST)
                    public abstract <T extends Y> T m(T arg);
                }
                class X { }
                class Y { }
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--patch-module", "java.base=" + src.toAbsolutePath().toString(),
                src.resolve("p").resolve("Foo.java").toString());

        checkExit(Exit.OK);
        checkOutput("preview-list.html", true, """
                <div class="col-summary-item-name even-row-color method method-tab1">\
                <a href="java.base/p/Foo.html#m(T)">p.Foo.m<wbr>(T)</a><sup>\
                <a href="java.base/p/Foo.html#preview-m(T)">PREVIEW</a></sup></div>
                <div class="col-second even-row-color method method-tab1">Test Feature</div>
                <div class="col-last even-row-color method method-tab1"></div>
                <div class="col-summary-item-name odd-row-color method method-tab1">\
                <a href="java.base/p/Foo.html#m(p.Y)">p.Foo.m<wbr>(T)</a><sup>\
                <a href="java.base/p/Foo.html#preview-m(p.Y)">PREVIEW</a></sup></div>
                <div class="col-second odd-row-color method method-tab1">Test Feature</div>
                <div class="col-last odd-row-color method method-tab1"></div>""");
        checkOutput("preview-list.html", true, """
                <div class="col-summary-item-name even-row-color constructor constructor-tab1">\
                <a href="java.base/p/Foo.html#%3Cinit%3E(T)">p.Foo<wbr>(T)</a><sup>\
                <a href="java.base/p/Foo.html#preview-%3Cinit%3E(T)">PREVIEW</a></sup></div>
                <div class="col-second even-row-color constructor constructor-tab1">Test Feature</div>
                <div class="col-last even-row-color constructor constructor-tab1"></div>
                <div class="col-summary-item-name odd-row-color constructor constructor-tab1">\
                <a href="java.base/p/Foo.html#%3Cinit%3E(p.Y)">p.Foo<wbr>(T)</a><sup>\
                <a href="java.base/p/Foo.html#preview-%3Cinit%3E(p.Y)">PREVIEW</a></sup></div>
                <div class="col-second odd-row-color constructor constructor-tab1">Test Feature</div>
                <div class="col-last odd-row-color constructor constructor-tab1"></div>""");
    }
}
