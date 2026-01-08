/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;

/*
 * @test
 * @bug 4138158
 * @summary Tests that setAutoscrolls(false) locks autoscroll
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4138158
 */

public class bug4138158 {
    private static final String INSTRUCTIONS = """
            Move mouse to beginning of table, press left mouse button and drag mouse down
            below the frame. If the table isn't scrolled down then test passes.
            If the table is scrolled then test fails.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(bug4138158::createTestUI)
                .build()
                .awaitAndCheck();
    }

    public static JFrame createTestUI() {
        JFrame frame = new JFrame("bug4138158");
        JTable table = new JTable(20, 3);
        table.setAutoscrolls(false);
        JScrollPane sp = new JScrollPane(table);
        frame.add(sp);
        frame.setSize(200, 200);
        return frame;
    }
}
