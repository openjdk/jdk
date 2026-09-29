/*
 * Copyright (c) 2003, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4785160
 * @summary Test that the cursor is always visible when typing in JTextArea with JScrollBar
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4785160
*/

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class bug4785160 {

    static final String INSTRUCTIONS = """
         Ensure that the horizontal scrollbar is visible in the JTextArea.
         If necessary, reduce the width of the window so that the scrollbar becomes visible.
         Scroll all the way to the right so the end of the line is visible.
         If necessary, move the text caret in the text area to the end of line.
         The test PASSES if the caret is visible at the end of the line.
         The test FAILS if the caret disappears when moved to the end of the line.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("bug4785160 Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4785160::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("bug4785160");
        JTextArea area = new JTextArea();
        String s = "";
        for (int i = 0; i < 80; i++) {
             s += "m";
        }
        area.setText(s);
        area.getCaret().setDot(area.getText().length() + 1);
        frame.add(new JScrollPane(area));
        frame.setSize(300, 300);
        return frame;
    }
}
