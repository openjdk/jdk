/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4237176
 * @summary Tests that background color is set properly for JViewport
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4237176
 */

import java.awt.Color;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JViewport;

public class bug4237176 {

    private static final String INSTRUCTIONS = """
        Look at the empty space below the table. It should be blue.
        If it is, test passes, otherwise it fails.""";

    public static void main(String[] args) throws Exception {

         PassFailJFrame.builder()
                .title("JViewport Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .testUI(bug4237176::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("Color Demo");
        JTable table = new JTable(new Object[][]{{"one", "two"}}, new Object[]{"A", "B"});
        JScrollPane sp = new JScrollPane(table);
        JViewport vp = sp.getViewport();
        vp.setBackground(Color.blue);
        vp.setOpaque(true);

        frame.getContentPane().add(sp);
        frame.pack();
        return frame;
    }

}
