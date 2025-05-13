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
 * @bug 4128110
 * @summary Verify that JViewport.setViewportView() and JScrollPane.setViewport()
 *          force a re-layout and a repaint.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual SetViewRepaint
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;

public class SetViewRepaint {
    private static final String INSTRUCTIONS = """
            Verify the following two cases:

            1) Press "JViewport.setViewportView()" button and verify that
               the blue label is replaced by a scrolling list.

            2) Press "JScrollPane.setViewport()" button and verify that
               the red label is replaced by a scrolling list as well.

            In either case the display should update automatically after
            pressing the button.

            If the above is true, press PASS else press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(30)
                .testUI(SetViewRepaint::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("SetViewRepaint");
        JPanel p1 = new JPanel(new BorderLayout());
        JPanel p2 = new JPanel(new BorderLayout());

        JLabel label1 = new ColorLabel(Color.BLUE, "Blue Label");
        final JList list1 = new JList(new String[]{"one", "two", "three", "four"});
        final JScrollPane sp1 = new JScrollPane(label1);
        ActionListener doSetViewportView = e -> sp1.setViewportView(list1);
        JButton b1 = new JButton("JViewport.setViewportView()");
        b1.addActionListener(doSetViewportView);
        p1.add(sp1, BorderLayout.CENTER);
        p1.add(b1, BorderLayout.SOUTH);

        JLabel label2 = new ColorLabel(Color.RED, "Red Label");
        final JList list2 = new JList(new String[]{"five", "six", "seven", "eight"});
        final JScrollPane sp2 = new JScrollPane(label2);
        ActionListener doSetViewport = e -> {
            JViewport vp = new JViewport();
            vp.setView(list2);
            sp2.setViewport(vp);
        };
        JButton b2 = new JButton("JScrollPane.setViewport()");
        b2.addActionListener(doSetViewport);
        p2.add(sp2, BorderLayout.CENTER);
        p2.add(b2, BorderLayout.SOUTH);
        frame.setLayout(new GridLayout(1, 2));
        frame.add(p1);
        frame.add(p2);
        frame.setResizable(false);
        frame.setSize(500, 120);
        return frame;
    }

    private static class ColorLabel extends JLabel {
        ColorLabel(Color color, String text) {
            super(text);
            setForeground(Color.WHITE);
            setBackground(color);
            setOpaque(true);
            setHorizontalAlignment(CENTER);
        }
    }
}
