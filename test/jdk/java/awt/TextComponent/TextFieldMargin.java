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

/*
 * @test
 * @bug 4129511
 * @summary Tests that TextField margins are not exceedingly wide
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextFieldMargin
 */

import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.Label;
import java.awt.TextArea;
import java.awt.TextField;

public class TextFieldMargin {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Examine the TextField, Label, and TextArea to see
                   that the text is vertically aligned along the left
                2. If all are aligned along the left, then test PASS,
                   else test FAILS.
                """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(TextFieldMargin::initialize)
                .build()
                .awaitAndCheck();
    }

    public static Frame initialize() {
        Frame frame = new Frame("Frame with a text field & a label");
        frame.setLayout(new GridLayout(5, 1));
        TextField text_field = new TextField("Left Textfield");
        frame.add(text_field);
        Label label = new Label("Left Label");
        frame.add(label);
        TextArea text_area = new TextArea("Left Textfield");
        frame.add(text_area);
        frame.setBounds(50, 50, 300, 300);
        return frame;
    }
}
