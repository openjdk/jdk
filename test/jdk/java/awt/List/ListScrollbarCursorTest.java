/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4290684
 * @summary Tests that cursor on the scrollbar of the list is set to default.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual ListScrollbarCursorTest
 */

import java.awt.Cursor;
import java.awt.Frame;
import java.awt.List;
import java.awt.Panel;

public class ListScrollbarCursorTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. You see the list in the middle of the panel.
                   This list has two scrollbars.
                2. The cursor should have a shape of hand over the main area
                   and a shape of arrow over scrollbars.
                3. Move the mouse cursor to either horizontal or vertical scrollbar.
                4. Press PASS if you see the default arrow cursor else press FAIL.
                """;
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(ListScrollbarCursorTest::initialize)
                .build()
                .awaitAndCheck();
    }

    static Frame initialize() {
        Frame frame = new Frame("List Scrollbar Cursor Test");
        Panel panel = new Panel();
        List list = new List(3);
        list.add("List item with a very long name" +
                "(just to make the horizontal scrollbar visible)");
        list.add("Item 2");
        list.add("Item 3");
        list.setCursor(new Cursor(Cursor.HAND_CURSOR));
        panel.add(list);
        frame.add(panel);
        frame.setSize(200, 200);
        return frame;
    }
}
