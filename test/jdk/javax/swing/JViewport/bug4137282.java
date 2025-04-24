/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4137282
 * @summary Tests that scrollbars appear automatically when the enclosed
 *          component is enlarged
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4137282
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class bug4137282 {

    private static final String INSTRUCTIONS = """
        Press the "resize" button. Two scrollbars should appear.
        Press Pass if they appear and Fail otherwise.""";

    static volatile JPanel pane;

    public static void main(String[] args) throws Exception {

         PassFailJFrame.builder()
                .title("JViewport Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .testUI(bug4137282::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        pane = new JPanel();

        pane.setBackground(Color.blue);
        setPaneSize(100, 100);
        JFrame frame = new JFrame("bug4137282");
        JScrollPane sp = new JScrollPane(pane);

        JButton b = new JButton("resize");
        b.addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent ev) {
                    setPaneSize(300, 300);
                }
            });

        frame.add(b, BorderLayout.NORTH);
        frame.add(sp, BorderLayout.CENTER);
        frame.pack();
        return frame;
    }

    static void setPaneSize(int w, int h) {
        pane.setPreferredSize(new Dimension(w, h));
        pane.setSize(w, h);
    }
}
