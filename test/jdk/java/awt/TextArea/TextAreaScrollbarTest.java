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

import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.TextArea;

/*
 * @test
 * @bug 4158997
 * @key headful
 * @summary Make sure that the TextArea has both horizontal and
 * vertical scrollbars when bad scrollbar arguments are passed
 * into the constructor.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaScrollbarTest
 */

public class TextAreaScrollbarTest {
    private static final String INSTRUCTIONS = """
            Check to see that each TextArea has the specified
            number and placement of scrollbars, i.e., both scrollbars,
            horizontal only, vertical only, or no scrollbars at all.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaScrollbarTest")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TestFrame::new)
                .build()
                .awaitAndCheck();
    }
}

class TestFrame extends Frame {
    private String both = "Both Scrollbars  Both Scrollbars  Both Scrollbars\n";
    private String horiz = "Horizontal Scrollbar Only  Horizontal Scrollbar Only\n";
    private String vert = "Vertical Scrollbar Only  Vertical Scrollbar Only\n";
    private String none = "No Scrollbars  No Scrollbars  No Scrollbars  No Scrollbars\n";

    public TestFrame() {
        super("Test frame");

        // sets a GridLayout w/ 2 columns and an unspecified # of rows
        setLayout(new GridLayout(0, 2, 15, 5));

        TextArea t1 = new TextArea(both + both + both + both + both + both, 3, 8, 0);
        add(new Label("TA should have both scrollbars: arg = 0"));
        add(t1);

        TextArea t2 = new TextArea(both + both + both + both + both + both, 3, 8, -1);
        add(new Label("TA should have both scrollbars: arg = -1"));
        add(t2);

        TextArea t3 = new TextArea(both + both + both + both + both + both, 3, 8, 4);
        add(new Label("TA should have both scrollbars: arg = 4"));
        add(t3);

        TextArea t4 = new TextArea(horiz + horiz + horiz + horiz + horiz + horiz, 3, 8, 2);
        add(new Label("TA should have horizontal scrollbar: arg = 2"));
        add(t4);

        TextArea t5 = new TextArea(vert + vert + vert + vert + vert + vert, 3, 8, 1);
        add(new Label("TA should have vertical scrollbar: arg = 1"));
        add(t5);

        TextArea t6 = new TextArea(none + none + none + none + none + none, 3, 8, 3);
        add(new Label("TA should have no scrollbars: arg = 3"));
        add(t6);

        TextArea t7 = new TextArea();
        t7.setText(both + both + both + both + both + both);
        add(new Label("Both scrollbars: TextArea()"));
        add(t7);

        TextArea t8 = new TextArea(both + both + both + both + both + both);
        add(new Label("Both scrollbars: TextArea(String text)"));
        add(t8);

        TextArea t9 = new TextArea(3, 8);
        t9.setText(both + both + both + both + both + both);
        add(new Label("Both scrollbars: TextArea(int rows, int columns)"));
        add(t9);

        TextArea t10 = new TextArea(both + both + both + both + both + both, 3, 8);
        add(new Label("Both scrollbars: TextArea(text, rows, columns)"));
        add(t10);

        setSize(600, 600);
    }
}

