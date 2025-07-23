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
 * @bug 4249161
 * @summary Tests that JList.setComponentOrientation() works correctly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4249161
 */

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JScrollPane;

public class bug4249161 {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            1. With a scroll bar, confirm that all words ("one" - "twenty") are
            aligned at the left side of a list.
            2. Press "Change!" button. All words on the list should be moved
            to the right side.
            3. Press the same button again. All words should be moved to the
            left side.

            If all items in a list are moved as soon as "Change!" button is
            pressed, test passes.
            """;
        PassFailJFrame.builder()
            .title("bug4249161 Instructions")
            .instructions(INSTRUCTIONS)
            .columns(35)
            .testUI(bug4249161::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JFrame fr = new JFrame("bug4249161");

        String[] data = {"one", "two", "three", "four", "five",
            "six", "seven", "eight", "nine", "ten",
            "eleven", "twelve", "thirteen", "fourteen", "fifteen",
            "sixteen", "seventeen", "eighteen", "nineteen", "twenty"
        };
        final JList list = new JList(data);
        list.setSize(200, 200);
        list.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        JScrollPane pane = new JScrollPane(list);
        fr.add(pane);

        JButton button = new JButton("Change!");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (list.getComponentOrientation() !=
                    ComponentOrientation.RIGHT_TO_LEFT) {
                    list.setComponentOrientation
                        (ComponentOrientation.RIGHT_TO_LEFT);
                } else {
                    list.setComponentOrientation
                        (ComponentOrientation.LEFT_TO_RIGHT);
                }
            }
        });
        fr.add(button, BorderLayout.SOUTH);
        fr.setSize(200, 300);
        fr.setAlwaysOnTop(true);
        return fr;
    }
}
