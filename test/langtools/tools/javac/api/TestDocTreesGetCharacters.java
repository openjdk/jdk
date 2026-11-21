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
 * @bug 8397902
 * @summary Test character decoding by DocTrees.getCharacters
 * @modules jdk.compiler
 * @run main TestDocTreesGetCharacters
 */

import java.util.List;
import java.util.Objects;

import javax.lang.model.util.Elements;
import javax.tools.ToolProvider;

import com.sun.source.doctree.EntityTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;

public class TestDocTreesGetCharacters {

    public static void main(String[] args) throws Exception {
        var compiler = ToolProvider.getSystemJavaCompiler();
        var fileManager = compiler.getStandardFileManager(null, null, null);

        var task = (JavacTask) compiler.getTask(null, fileManager, null, null, null, null);

        var test = new TestDocTreesGetCharacters(DocTrees.instance(task), task.getElements());
        test.run();
    }

    private final DocTrees trees;
    private final Elements elements;

    TestDocTreesGetCharacters(DocTrees trees, Elements elements) {
        this.trees = trees;
        this.elements = elements;
    }

    void run() {
        // Named references.
        check("lt", "<");
        check("gt", ">");
        check("amp", "&");
        check("nbsp", "\u00A0");
        check("NonBreakingSpace", "\u00A0");

        // Decimal and hexadecimal references.
        check("#65", "A");
        check("#x41", "A");
        check("#X41", "A");
        check("#00065", "A");
        check("#x000041", "A");

        // Supplementary and multi-code-point results.
        check("Afr", "\uD835\uDD04");
        check("#x1D504", "\uD835\uDD04");
        check("#120068", "\uD835\uDD04");
        check("#x1F600", "\uD83D\uDE00");
        check("#128512", "\uD83D\uDE00");
        check("NotEqualTilde", "\u2242\u0338");
        check("nesim", "\u2242\u0338");

        // Boundaries
        check("#xD7A3", "\uD7A3");         // Valid character below surrogates
        check("#xE000", "\uE000");         // First character after surrogates
        check("#xFFFD", "\uFFFD");         // Replacement character
        check("#x10000", "\uD800\uDC00");  // First supplementary code point
        check("#65536", "\uD800\uDC00");   // Same in decimal format
        check("#x10FFFD", "\uDBFF\uDFFD"); // High supplementary private-use value.

        // Unknown and invalid references
        check("unknownEntity", null);
        check("#xD800", null);           // First high surrogate
        check("#xDBFF", null);           // Last high surrogate
        check("#xDC00", null);           // First low surrogate
        check("#xDFFF", null);           // Last low surrogate
        check("#x10FFFE", null);         // Unicode noncharacter
        check("#x10FFFF", null);         // Unicode noncharacter
        check("#x110000", null);         // Out of range

        // Malformed references and overflow
        for (String name : List.of(
                "#", "#x", "#X",
                "#xyz", "#xG", "#12z", "#1.5",
                "#-1", "#x-1",
                "#2147483648", "#x80000000",
                "#999999999999999999999999999999")) {
            check(name, null);
        }
    }

    void check(String name, String expected) {
        EntityTree entity = trees.getDocTreeFactory().newEntityTree(elements.getName(name));
        String actual = trees.getCharacters(entity);

        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("&" + name
                            + ";: expected " + format(expected)
                            + ", found " + format(actual));
        }
    }

    static String format(String text) {
        return text == null ? "null" : text.chars()
                .mapToObj(cp -> String.format("'\\u%04X'", cp))
                .toList()
                .toString();
    }
}
