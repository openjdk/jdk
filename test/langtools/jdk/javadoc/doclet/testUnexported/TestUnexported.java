/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8254622
 * @summary Hide superclasses from conditionally exported packages
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build javadoc.tester.* toolbox.ToolBox
 * @run main TestUnexported
 */

import java.io.IOException;
import java.nio.file.Path;

import toolbox.ModuleBuilder;
import toolbox.ToolBox;

import javadoc.tester.JavadocTester;

public class TestUnexported extends JavadocTester {

    final ToolBox tb;
    final Path src;

    public static void main(String... args) throws Exception {
        var tester = new TestUnexported();
        tester.runTests();
    }

    TestUnexported() throws IOException {
        tb = new ToolBox();
        src = Path.of("src");
        new ModuleBuilder(tb, "ma")
                .classes("""
                           package pa;

                           import pa.internal.*;

                           /**
                            * Class with unexported super types.
                            */
                           public abstract class A extends InternalClass implements InternalInterface {}
                           """,
                        """
                             package pa.internal;

                             /**
                              * Conditionally exported class.
                              */
                             public class InternalClass {
                                /**
                                 * Method in internal class.
                                 */
                                 public void p() {}
                             }
                             """,
                        """
                            package pa.internal;

                            /**
                             * Conditionally exported interface.
                             */
                            public interface InternalInterface {
                               /**
                                * Method in internal interface.
                                */
                                public void m();
                            }
                            """)
                .exports("pa")
                .exportsTo("pa.internal", "mb")
                .write(src);

        new ModuleBuilder(tb, "mb")
                .classes("""
                           package pb;

                           import pa.internal.*;

                           /**
                            * Class with conditionally exported super types.
                            */
                           public abstract class B extends InternalClass implements InternalInterface {}
                           """,
                       """
                           package pb;

                           import pa.internal.*;

                           /**
                            * Interface with conditionally exported super interface.
                            */
                           public interface I extends InternalInterface {}
                           """)
                .requires("ma")
                .exports("pb")
                .write(src);
    }

    // Types in packages that are exported conditionally are hidden in public API documentation.
    @Test
    public void test(Path base) throws Exception {

        Path outDir = base.resolve("out");

        javadoc("-d", outDir.toString(),
                "--no-platform-links",
                "--module-source-path", src.toString(),
                "--show-packages", "exported",
                "--module", "ma,mb");

        checkExit(Exit.OK);

        checkFiles(false, "ma/pa/internal/InternalClass.html", "ma/pa/internal/InternalInterface.html");

        checkOutput("ma/pa/A.html", false, "InternalInterface", "InternalClass");
        checkOutput("mb/pb/B.html", false, "InternalInterface", "InternalClass");
        checkOutput("mb/pb/I.html", false, "InternalInterface");

        checkOrder("ma/pa/A.html", """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance">pa.A</div>
                    """,
                """
                    <div class="type-signature"><span class="modifiers">public abstract class </spa\
                    n><span class="element-name type-name-label">A</span>
                    <span class="extends-implements">extends java.lang.Object</span></div>
                    """,
                """
                    <section class="detail" id="m()">
                    <h3>m</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span \
                    class="element-name">m</span>()</div>
                    <div class="block">Method in internal interface.</div>
                    </div>
                    </section>
                    """,
                """
                    <section class="detail" id="p()">
                    <h3>p</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span \
                    class="return-type">void</span>&nbsp;<span class="element-name">p</span>()</div>
                    <div class="block">Method in internal class.</div>
                    </div>
                    </section>
                    """);

        checkOrder("mb/pb/B.html", """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance">pb.B</div>
                    """,
                """
                    <div class="type-signature"><span class="modifiers">public abstract class </spa\
                    n><span class="element-name type-name-label">B</span>
                    <span class="extends-implements">extends java.lang.Object</span></div>
                    """,
                """
                    <section class="detail" id="m()">
                    <h3>m</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span \
                    class="element-name">m</span>()</div>
                    <div class="block">Method in internal interface.</div>
                    </div>
                    </section>
                    """,
                """
                    <section class="detail" id="p()">
                    <h3>p</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="modifiers">public</span>&nbsp;<span \
                    class="return-type">void</span>&nbsp;<span class="element-name">p</span>()</div>
                    <div class="block">Method in internal class.</div>
                    </div>
                    </section>
                    """);

        checkOrder("mb/pb/I.html", """
                    <div class="type-signature"><span class="modifiers">public interface </span><sp\
                    an class="element-name type-name-label">I</span></div>
                    """,
                """
                    <section class="detail" id="m()">
                    <h3>m</h3>
                    <div class="horizontal-scroll">
                    <div class="member-signature"><span class="return-type">void</span>&nbsp;<span \
                    class="element-name">m</span>()</div>
                    <div class="block">Method in internal interface.</div>
                    </div>
                    </section>
                    """);
    }

    // Types in packages that are exported conditionally are shown when documenting
    // all module packages including internal ones.
    @Test
    public void testIncluded(Path base) throws Exception {

        Path outDir = base.resolve("out");

        javadoc("-d", outDir.toString(),
                "--no-platform-links",
                "--module-source-path", src.toString(),
                "--show-module-contents", "all",
                "--show-packages", "all",
                "--module", "ma,mb");

        checkExit(Exit.OK);

        checkFiles(true, "ma/pa/internal/InternalClass.html", "ma/pa/internal/InternalInterface.html");

        checkOutput("ma/pa/A.html", true, "InternalInterface", "InternalClass");
        checkOutput("mb/pb/B.html", true, "InternalInterface", "InternalClass");
        checkOutput("mb/pb/I.html", true, "InternalInterface");

        checkOrder("ma/pa/A.html",
                """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance"><a href="internal/InternalClass.html" title="class in \
                    pa.internal">pa.internal.InternalClass</a>
                    <div class="inheritance">pa.A</div>
                    """,
                """
                    <div class="type-signature"><span class="modifiers">public abstract class </spa\
                    n><span class="element-name type-name-label">A</span>
                    <span class="extends-implements">extends <a href="internal/InternalClass.html" \
                    title="class in pa.internal">InternalClass</a>
                    implements <a href="internal/InternalInterface.html" title="interface in pa.int\
                    ernal">InternalInterface</a></span></div>
                    """,
                """
                    <h3 id="methods-inherited-from-class-pa.internal.InternalClass">Methods inherit\
                    ed from class&nbsp;<a href="internal/InternalClass.html#method-summary" title="\
                    class in pa.internal">InternalClass</a></h3>
                    <code><a href="internal/InternalClass.html#p()" title="p()">p</a></code></div>
                    """,
                """
                    <h3 id="methods-inherited-from-class-pa.internal.InternalInterface">Methods inh\
                    erited from interface&nbsp;<a href="internal/InternalInterface.html#method-summ\
                    ary" title="interface in pa.internal">InternalInterface</a></h3>
                    <code><a href="internal/InternalInterface.html#m()" title="m()">m</a></code></div>
                    """);

        checkOrder("mb/pb/B.html",
                """
                    <div class="inheritance" title="Inheritance Tree">java.lang.Object
                    <div class="inheritance"><a href="../../ma/pa/internal/InternalClass.html" titl\
                    e="class in pa.internal">pa.internal.InternalClass</a>
                    <div class="inheritance">pb.B</div>
                    """,
                """
                    <div class="type-signature"><span class="modifiers">public abstract class </spa\
                    n><span class="element-name type-name-label">B</span>
                    <span class="extends-implements">extends <a href="../../ma/pa/internal/Internal\
                    Class.html" title="class in pa.internal">InternalClass</a>
                    implements <a href="../../ma/pa/internal/InternalInterface.html" title="interfa\
                    ce in pa.internal">InternalInterface</a></span></div>
                    """,
                """
                    <h3 id="methods-inherited-from-class-pa.internal.InternalClass">Methods inherit\
                    ed from class&nbsp;<a href="../../ma/pa/internal/InternalClass.html#method-summ\
                    ary" title="class in pa.internal">InternalClass</a></h3>
                    <code><a href="../../ma/pa/internal/InternalClass.html#p()" title="p()">p</a></code></div>
                    """,
                """
                    <h3 id="methods-inherited-from-class-pa.internal.InternalInterface">Methods inh\
                    erited from interface&nbsp;<a href="../../ma/pa/internal/InternalInterface.html\
                    #method-summary" title="interface in pa.internal">InternalInterface</a></h3>
                    <code><a href="../../ma/pa/internal/InternalInterface.html#m()" title="m()">m</a></code></div>
                    """);

    }
}
