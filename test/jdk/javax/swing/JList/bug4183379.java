/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4183379
 * @summary JList has wrong scrolling behavior when you click in the "troth"
 * of a scrollbar, in a scrollpane.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4183379
 */

import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;

public class bug4183379 {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            Click mouse several times in the "troth" of a scrollbars
            in a scrollpane containing a list.
            The list should scrolls by one block, i.e.:

            For vertical scrolling:
              - if scrolling down the last visible element should become the
              first completely visible element
              - if scrolling up, the first visible element should become the
              last completely visible element

            For horizontal scrolling:
              - for scrolling left if the beginning of the first column is not
              visible it should become visible, otherwise the beginning of the
              previous column should become visible;
              - for scrolling right the next colunm after first visible column
              should become visible.
            """;
        PassFailJFrame.builder()
            .title("bug4183379 Instructions")
            .instructions(INSTRUCTIONS)
            .columns(35)
            .testUI(bug4183379::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JFrame fr = new JFrame("bug4183379");

        String[] data = new String[90];
        for (int i=0; i<90; i++) {
            data[i] = "item number "+i;
        }

        JList lst = new JList(data);
        lst.setLayoutOrientation(JList.VERTICAL_WRAP);
        lst.setVisibleRowCount(20);

        JScrollPane jsp = new JScrollPane(lst);
        fr.add(jsp);
        fr.setSize(210,200);
        fr.setAlwaysOnTop(true);
        return fr;
    }
}
