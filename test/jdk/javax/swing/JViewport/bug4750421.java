/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4750421
 * @summary 4143833 - regression in 1.4.x
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4750421
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;

public class bug4750421 {

    private static final String INSTRUCTIONS = """
        A table will be shown.
        Select the third row of the table.
        Then press down arrow button of vertical scrollbar to scroll down.
            (in macos drag the vertical scrollbar down via mouse just enough
            to scroll by 1 unit as there is no arrow button in scrollbar)
        If the selection disappears press Fail else press Pass.""";

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4750421");
        JTable table = new JTable(30, 10);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        JScrollPane pane = new JScrollPane(table);
        frame.getContentPane().add(pane);
        pane.getViewport().setScrollMode(JViewport.BACKINGSTORE_SCROLL_MODE);
        frame.pack();
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JViewport Instructions")
                .instructions(INSTRUCTIONS)
                .rows(7)
                .columns(35)
                .testUI(bug4750421::createTestUI)
                .build()
                .awaitAndCheck();
    }
}
