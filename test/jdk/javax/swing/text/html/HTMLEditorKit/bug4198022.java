/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @bug 4198022
 * @summary Tests if HTML tags <sup>, <sub> and <nobr> are supported in JEditorPane
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4198022
 */

import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.text.html.HTMLEditorKit;

public class bug4198022 {

    static final String INSTRUCTIONS = """
    There are two "lines" of text in the displayed HTML window
    The first line/string contains <sub> and <sup> HTML elements.
    The word "subscript" should be subscripted (placed lower than the word "Testing"),
    and the word "superscript" should be superscripted (placed higher than "Testing").
    If instead these words are placed on the same level then the test FAILS.

    The second line/string contains a sentence marked with <nobr> tag.
    It should be presented as one long line without breaks.
    It is OK for the line to extend past the end of the window and not be all visible.
    If the line is broken up so you see multiple lines the test FAILS.

    If all behaves as expected, the test PASSES.
    """;

    static JFrame createUI() {

        JFrame frame = new JFrame("bug4198022");
        JEditorPane ep = new JEditorPane();
        HTMLEditorKit ek = new HTMLEditorKit();
        ep.setEditorKit(ek);
        ep.setText(
            "<html><body>Testing <sub>subscript</sub> and <sup>superscript</sup>.<br>" +
            "<nobr>This text is intended to be presented as a single line without " +
            "any word wrapping, regardless of whether it fits the viewable area of " +
            "the editor pane or not.</nobr></body></html>");

        ep.setEditable(false);

        frame.add(ep);
        frame.setSize(500, 300);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4198022::createUI)
            .build()
            .awaitAndCheck();
    }
}
