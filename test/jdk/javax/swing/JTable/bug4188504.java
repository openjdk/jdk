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
 * @bug 4188504
 * @summary setResizable for specified column.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4188504
 */

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;

public class bug4188504 {
    private static final String INSTRUCTIONS = """
            1. A table is displayed with 3 columns - A, B, C.

            2. Try to resize second column of table (Move mouse to the position
            between "B" and "C" headers, press left mouse button and move to
            right/left).
            PLEASE NOTE: You may be able to swap the columns but make sure the
            width of column B stays the same.

            3. If the second column does not change its width then press PASS
            otherwise press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(createAndShowUI())
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JFrame jFrame = new JFrame("bug4188504");
        JTable tableView = new JTable(4, 3);
        tableView.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tableView.getColumnModel().getColumn(1).setResizable(false);

        jFrame.add(new JScrollPane(tableView));
        jFrame.setSize(300, 150);
        return jFrame;
    }
}
