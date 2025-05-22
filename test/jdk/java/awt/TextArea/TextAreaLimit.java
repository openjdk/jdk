/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.TextArea;

/*
 * @test
 * @bug 4341196
 * @summary Tests that TextArea can handle more than 64K of text
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaLimit
 */

public class TextAreaLimit extends Frame {
    static TextArea text;
    private static final String INSTRUCTIONS = """
            You will see a text area with 40000 lines of text
            each with its own line number. If you see the caret after line 39999
            then test passes. Otherwise it fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaLimit")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(TextAreaLimit::new)
                .build()
                .awaitAndCheck();
    }

    public TextAreaLimit() {
        setLayout(new BorderLayout());

        text = new TextArea();
        add(text);
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < 40000; i++) {
            buf.append(i + "\n");
        }
        text.setText(buf.toString());
        text.setCaretPosition(buf.length());
        text.requestFocus();
        setSize(200, 200);
    }
}
