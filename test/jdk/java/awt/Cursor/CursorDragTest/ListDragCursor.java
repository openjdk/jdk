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

/*
 * @test
 * @bug 4313052
 * @summary Test cursor changes after mouse dragging ends
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ListDragCursor
 */

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.List;
import java.awt.Panel;
import java.awt.TextArea;

public class ListDragCursor {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Move mouse to the TextArea.
                2. Press the left mouse button.
                3. Drag mouse to the list.
                4. Release the left mouse button.

                If the mouse cursor starts as a Text Line Cursor and changes
                to a regular Pointer Cursor, then Hand Cursor when hovering
                the list, pass the test. This test fails if the cursor does
                not update at all when pointing over the different components.
                """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(ListDragCursor::createUI)
                .build()
                .awaitAndCheck();
    }

    public static Frame createUI() {
        Frame frame = new Frame("Cursor change after drag");
        Panel panel = new Panel();

        List list = new List(2);
        list.add("List1");
        list.add("List2");
        list.add("List3");
        list.add("List4");
        list.setCursor(new Cursor(Cursor.HAND_CURSOR));

        TextArea textArea = new TextArea(3, 5);
        textArea.setCursor(new Cursor(Cursor.TEXT_CURSOR));

        panel.add(textArea);
        panel.add(list);

        frame.add(panel);
        frame.setBounds(300, 100, 300, 150);
        return frame;
    }
}
