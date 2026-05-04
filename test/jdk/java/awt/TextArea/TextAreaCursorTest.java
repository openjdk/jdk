/*
 * Copyright (c) 1997, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 4060320
 * @summary Test TextArea cursor shape on its scrollbars
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAreaCursorTest
 */

public class TextAreaCursorTest {
    private static final String INSTRUCTIONS = """
            Move the cursor into textarea and on scrollbar. Verify that the shape of
            cursor on scrollbar should not be I-beam. Also, when the cursor in textarea
            is set to some other shape, it does not affect the cursor shape on the
            scrollbars.
            """;

    public static void main(String args[]) throws Exception {
            PassFailJFrame.builder()
                    .title("TextAreaCursorTest")
                    .instructions(INSTRUCTIONS)
                    .rows((int) INSTRUCTIONS.lines().count() + 2)
                    .columns(40)
                    .testUI(TextAreaCursorTest::createGUI)
                    .build()
                    .awaitAndCheck();
    }

    public static Frame createGUI () {
        Frame f = new Frame("TextAreaCursorTest");
        BorderLayout layout = new BorderLayout();
        f.setLayout(layout);

        TextArea ta = new TextArea("A test to make sure that cursor \n" +
                "on scrollbars has the correct shape\n\n" +
                "Press button to change the textarea\n" +
                "cursor to Hand_Cursor\n" +
                "Make sure that the cursor on scrollbars\n" +
                "remains the same", 10, 30);

        Button bu = new Button("Change Cursor");

        f.add(ta, BorderLayout.NORTH);
        f.add(bu, BorderLayout.SOUTH);
        bu.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                Cursor curs1 = new Cursor(Cursor.HAND_CURSOR);
                ta.setCursor(curs1);
            }
        });
        f.pack();
        return f;
    }
}
