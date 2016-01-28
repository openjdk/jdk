/*
 * Copyright (c) 2003, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4965490
 * @summary Verify that matching braces can appear within inline tags.
 * @ignore API, re-evaluate @bold, @maybe causes doclint to throw up.
 * @modules jdk.javadoc
 */

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.util.DocTrees;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;

/**
 * This is a {@code test} comment.
 * It is {@bold {@underline only} a test}.
 * We would like some code
 * {@code for (int i : nums) { doit(i); } return; }
 * to be embedded {@maybe {even {a couple {of levels}}} deep}.
 */
public class InlineTagsWithBraces implements Doclet {

    private static String[] expectedTags = {
        "Text", "@code", "Text",
        "@bold", "Text", "@code", "Text",
        "@maybe", "Text"
    };
    private static String[] expectedText = {
        "This is a ", "test", " comment.\n" +
        " It is ", "{@underline only} a test", ".\n" +
        " We would like some code\n" +
        " ", "for (int i : nums) { doit(i); } return; ", "\n" +
        " to be embedded ", "{even {a couple {of levels}}} deep", "."
    };

    public static void main(String[] args) {
        String thisFile = "" +
            new File(System.getProperty("test.src", "."), "InlineTagsWithBraces.java");

        String[] argarray = {
            "InlineTagsWithBraces",
            "-Xwerror",
            thisFile
        };
        if (jdk.javadoc.internal.tool.Main.execute(argarray) != 0)
            throw new Error("Javadoc encountered warnings or errors.");
    }

    public boolean run(DocletEnvironment root) {
        DocTrees trees = root.getDocTrees();
        TypeElement cd = root.getIncludedClasses().iterator().next();
        DocCommentTree docCommentTree = trees.getDocCommentTree(cd);
        List<? extends DocTree> tags = docCommentTree.getBody();

        for (int i = 0; i < tags.size(); i++) {
            System.out.println(tags.get(0).getKind());
//            if (!tags[i].name().equals(expectedTags[i]) ||
//                        !tags[i].text().equals(expectedText[i])) {
//                throw new Error("Tag \"" + tags[i] + "\" not as expected");
//            }
        }

        return true;
    }

    @Override
    public String getName() {
        return "Test";
    }

    @Override
    public Set<Option> getSupportedOptions() {
        return Collections.emptySet();
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }
}
