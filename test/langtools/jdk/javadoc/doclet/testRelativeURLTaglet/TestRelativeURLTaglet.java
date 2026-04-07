/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8373922
 * @summary  Enhance Taglet API to support relative URLs
 * @library /tools/lib ../../lib
 * @modules jdk.javadoc/jdk.javadoc.internal.tool
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 * @build toolbox.ToolBox javadoc.tester.*
 * @run main TestRelativeURLTaglet
 */

import java.net.URI;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import javadoc.tester.JavadocTester;
import jdk.javadoc.doclet.Taglet;
import toolbox.ModuleBuilder;
import toolbox.ToolBox;

public class TestRelativeURLTaglet extends JavadocTester implements Taglet {

    public static void main(String... args) throws Exception {
        new TestRelativeURLTaglet().runTests();
    }

    ToolBox tb = new ToolBox();

    @Test
    public void testRelativePackageURL(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p.q;
                /**
                 * First {@test sentence}.
                 */
                 public interface A {}
                """,
                """
                package p.q.r;
                /**
                 * Comment {@test with} inline {@test tags}.
                 *
                 * @test 123
                 * @test 456
                 */
                 public class C {}
                """);
        tb.writeFile(src.resolve("p/q/doc-files/test.html"), """
                <html>
                <body>
                HTML {@test file} with {@test inline} tags.
                </body>
                </html>
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "-tagletpath", System.getProperty("test.class.path"),
                "-taglet", "TestRelativeURLTaglet",
                "-subpackages", "p");
        checkExit(Exit.OK);

        checkOutput("p/q/A.html", true,
                "First ../../sentence.");
        checkOutput("p/q/package-summary.html", true,
                "First ../../sentence.");
        checkOutput("p/q/doc-files/test.html", true,
                "HTML ../../../file with ../../../inline tags.");
        checkOutput("p/q/r/C.html", true,
                "Comment ../../../with inline ../../../tags.",
                "../../../123 ../../../456");
        checkOutput("p/q/r/package-summary.html", true,
                "Comment ../../../with inline ../../../tags.");
        checkOutput("index-all.html", true,
                "First sentence.",
                "Comment with inline tags.");
        checkOutput("allclasses-index.html", true,
                "First sentence.",
                "Comment with inline tags.");
    }

    @Test
    public void testRelativeModuleURL(Path base) throws Exception {
        Path src = base.resolve("src");
        new ModuleBuilder(tb, "ma")
                .comment("Module {@test ma}.")
                .classes(
                    """
                    /**
                     * Package {@test ma/p/a}.
                     */
                     package p.a;
                    """,
                    """
                    package p.a;
                    /**
                     * Class in {@test ma/p/a}.
                     *
                     * @test block1
                     * @test block2
                     */
                    public class A {}
                    """)
                .exports("p.a")
                .write(src);

        javadoc("-d", base.resolve("out").toString(),
                "--module-source-path", src.toString(),
                "-tagletpath", System.getProperty("test.class.path"),
                "-taglet", "TestRelativeURLTaglet",
                "--module", "ma");
        checkExit(Exit.OK);

        checkOutput("ma/p/a/package-summary.html", true,
                "Package ../../../ma/p/a.",
                "Class in ../../../ma/p/a.");
        checkOutput("ma/p/a/A.html", true,
                "Class in ../../../ma/p/a.",
                "<dl class=\"notes\">../../../block1 ../../../block2</dl>");
        checkOutput("ma/module-summary.html", true,
                "Module ../ma.",
                "Package ../ma/p/a.");
        checkOutput("index-all.html", true,
                "Class in ma/p/a.",
                "Module ma.",
                "Package ma/p/a.");
    }

    @Test
    public void testUnnamedPackageURL(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                /**
                 * Class in unnamed package: {@test tag}.
                 *
                 * @test first
                 * @test second
                 */
                 public class C {}
                """);

        javadoc("-d", base.resolve("out").toString(),
                "--source-path", src.toString(),
                "-tagletpath", System.getProperty("test.class.path"),
                "-taglet", "TestRelativeURLTaglet",
                src.resolve("C.java").toString());
        checkExit(Exit.OK);

        checkOutput("C.html", true,
                "Class in unnamed package: tag.",
                "<dl class=\"notes\">first second</dl>");
        checkOutput("index-all.html", true,
                "Class in unnamed package: tag.");
        checkOutput("allclasses-index.html", true,
                "Class in unnamed package: tag.");
    }

    // Taglet implementation

    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.allOf(Location.class);
    }

    @Override
    public boolean isInlineTag() {
        return true;
    }

    @Override
    public boolean isBlockTag() {
        return true;
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element element, URI docRoot) {
        if (tags.size() == 1 && tags.getFirst() instanceof UnknownInlineTagTree uit) {
            return docRoot.resolve(uit.getContent().toString()).toString();
        } else {
            return tags.stream()
                    .map(t -> docRoot.resolve(((UnknownBlockTagTree) t).getContent().toString()).toString())
                    .collect(Collectors.joining(" "));
        }
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element element) {
        throw new UnsupportedOperationException();
    }
}

