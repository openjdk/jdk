/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;

/*
 * @test
 * @bug 4127272
 * @summary TextArea displays head of text when scrolling horizontal bar.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextScrollTest
 */

public class TextScrollTest extends Frame {
    private static final String INSTRUCTIONS = """
            1. A TextArea whose content starts with the text ",
               'Scroll till the' will appear on the window ",
            2. Use the Horizontal thumb button of the TextArea to view the entire",
               content of the TextArea",
            3. While scrolling, if the text 'Scroll till the' appears repeatedly, Click Fail  ",
               else Click Pass"
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextScrollTest")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(TextScrollTest::new)
                .build()
                .awaitAndCheck();
    }

    public TextScrollTest() {
        this.setLayout(new BorderLayout());

        Panel p = new Panel();
        TextArea ta = new TextArea("Scroll till the right end of the " +
                "TextArea is reached. Action Done?\n", 10, 20);

        p.add(ta);
        add("Center", p);
        setSize(200, 200);
    }
}
