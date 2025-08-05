/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Button;
import java.awt.Frame;
import java.awt.TextArea;

/*
 * @test
 * @bug 6192116
 * @summary Auto-scrolling does not work properly for TextArea when appending
 *          some text, on XToolkit
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaAppendScrollTest2
 */

public class TextAreaAppendScrollTest2 extends Frame {
    TextArea area;
    private static final String INSTRUCTIONS = """
            Press the "Append \'cool\' button until you are able
            to reach the end of current line in the text area. If
            the next \'cool\' added wraps to a new line, press
            pass. Otherwise, press fail.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("TextAreaAppendScrollTest2")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(40)
                .testUI(TextAreaAppendScrollTest2::new)
                .build()
                .awaitAndCheck();
    }

    public TextAreaAppendScrollTest2() {
        setLayout(new BorderLayout());
        Button btn = new Button("Append \'cool\'");
        btn.addActionListener(e -> area.append("cool "));
        add("South", btn);
        area = new TextArea("AWT is cool ", 3, 3, TextArea.SCROLLBARS_NONE);
        add("Center", area);
        setSize(200, 200);
        StringBuilder coolStr = new StringBuilder("");
        for (int i = 0; i < 12 * 15; i++) {
            coolStr.append("cool ");
        }
        area.append(coolStr.toString());
    }
}
