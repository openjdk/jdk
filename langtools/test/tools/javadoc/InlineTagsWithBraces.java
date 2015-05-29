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
 * @modules jdk.javadoc
 */

import com.sun.javadoc.*;

/**
 * This is a {@code test} comment.
 * It is {@bold {@underline only} a test}.
 * We would like some code
 * {@code for (int i : nums) { doit(i); } return; }
 * to be embedded {@maybe {even {a couple {of levels}}} deep}.
 */
public class InlineTagsWithBraces extends Doclet {

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
            new java.io.File(System.getProperty("test.src", "."),
                             "InlineTagsWithBraces.java");

        if (com.sun.tools.javadoc.Main.execute(
                "javadoc",
                "InlineTagsWithBraces",
                InlineTagsWithBraces.class.getClassLoader(),
                new String[] {"-Xwerror", thisFile}) != 0)
            throw new Error("Javadoc encountered warnings or errors.");
    }

    public static boolean start(RootDoc root) {
        ClassDoc cd = root.classes()[0];
        Tag[] tags = cd.inlineTags();

        for (int i = 0; i < tags.length; i++) {
            if (!tags[i].name().equals(expectedTags[i]) ||
                        !tags[i].text().equals(expectedText[i])) {
                throw new Error("Tag \"" + tags[i] + "\" not as expected");
            }
        }

        return true;
    }
}
