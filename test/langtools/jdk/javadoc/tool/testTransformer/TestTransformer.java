/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug      8298405
 * @summary  Markdown support in the standard doclet
 * @library  /tools/lib ../../lib
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.javadoc/jdk.javadoc.internal.tool
 * @build    toolbox.ToolBox javadoc.tester.*
 * @run main TestTransformer
 */

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.RawTextTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreeFactory;
import com.sun.source.util.DocTrees;

import com.sun.tools.javac.api.JavacTrees;

import javadoc.tester.JavadocTester;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.StandardDoclet;
import toolbox.ToolBox;

public class TestTransformer extends JavadocTester {
    public static void main(String... args) throws Exception {
        var tester = new TestTransformer();
        tester.runTests();
    }

    private final ToolBox tb = new ToolBox();

    @Test
    public void testFindStandardTransformer_raw() {
        int count = 0;
        var sl = ServiceLoader.load(JavacTrees.DocCommentTreeTransformer.class);
        for (var t : sl) {
            if (t.name().equals("standard")) {
                out.println("Found " + t);
                count++;
            }
        }
        checking("transformer");
        if (count == 1) {
            passed("expected transformer found");
        } else {
            failed("expected transformer not found");
        }
    }

    @Test
    public void testFindStandardTransformer_stream() {
        var dct = getTransformer("standard");
        checking("transformer");
        if (dct.isPresent()) {
            out.println("Found " + dct.get());
            passed("expected transformer found");
        } else {
            failed("transformer not found");
        }
    }

    private Optional<JavacTrees.DocCommentTreeTransformer> getTransformer(String name) {
        var sl = ServiceLoader.load(JavacTrees.DocCommentTreeTransformer.class);
        return sl.stream()
                .map(ServiceLoader.Provider::get)
                .filter(t -> t.name().equals(name))
                .findFirst();
    }

    public static class MyTransformer implements JavacTrees.DocCommentTreeTransformer {

        private DocTreeFactory m;
        @Override
        public String name() {
            return getClass().getSimpleName();
        }

        @Override
        public DocCommentTree transform(DocTrees trees, DocCommentTree tree) {
            m = trees.getDocTreeFactory();
            return m.newDocCommentTree(transform(tree.getFullBody()), tree.getBlockTags());
        }

        private List<? extends DocTree> transform(List<? extends DocTree> list) {
            return list.stream().map(this::transform).toList();
        }

        private DocTree transform(DocTree tree) {
            return switch (tree) {
                case TextTree tt ->
                        m.newTextTree(transform(tt.getBody()));
                case RawTextTree rtt ->
                        m.newRawTextTree(rtt.getKind(), transform(rtt.getContent()));
                default ->
                        tree;
            };
        }

        private String transform(String s) {
            return s.replace("lowercase", "UPPERCASE");
        }
    }

    public static class MyDoclet extends StandardDoclet {
        @Override
        public boolean run(DocletEnvironment docEnv) {
            ((JavacTrees) docEnv.getDocTrees()).setDocCommentTreeTransformer(new MyTransformer());
            return super.run(docEnv);
        }
    }

    @Test
    public void testMyTransformer(Path base) throws Exception {
        Path src = base.resolve("src");
        tb.writeJavaFiles(src, """
                package p;
                /**
                 * This traditional comment contains a lowercase word.
                 */
                public class Trad { }
                """, """
                package p;
                /// This line comment contains a lowercase word.
                public class Line { }
                """);

        javadoc("-d", base.resolve("api").toString(),
                "-Xdoclint:none",
                "-XDaccessInternalAPI", // required to access JavacTrees
                "-doclet", "TestTransformer$MyDoclet",
                "-docletpath", System.getProperty("test.classes"),
                "-sourcepath", src.toString(),
                "p");
        checkExit(Exit.OK);

        checkOutput("p/Line.html", true, """
                <div class="block">This line comment contains a UPPERCASE word.</div>""");
        checkOutput("p/Trad.html", true, """
                <div class="block">This traditional comment contains a UPPERCASE word.</div>""");
    }
}