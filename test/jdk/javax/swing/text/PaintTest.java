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
 * @bug 4210250
 * @summary Tests that PlainView repaints the necessary lines of text.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual PaintTest
 */

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

public class PaintTest {

    private static final String INSTRUCTIONS = """
        Click the paint button.
        If half of the second line is erased,
        that is you can only see the bottom half of the second line
        with the top half painted over in white, click fail, else click pass.""";


    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("PlainView Repaint Instructions")
                .instructions(INSTRUCTIONS)
                .rows(7)
                .columns(35)
                .testUI(PaintTest::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("PaintTest");

        new PaintTest().create(frame.getContentPane());
        frame.pack();
        return frame;
    }


    void create(Container parent) {
        parent.setLayout(new FlowLayout());

        final JTextArea ta = new JTextArea
            ("A sample textarea\nwith a couple of lines\nof text") {
                public Dimension getPreferredSize() {
                    Dimension size = super.getPreferredSize();
                    if (getFont() != null) {
                        size.height += getFontMetrics(getFont())
                                       .getHeight() / 2;
                    }
                    return size;
                }
            };
        JButton button = new JButton("paint");

        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        Rectangle taBounds = ta.getBounds();
                        int fontHeight =
                            ta.getFontMetrics(ta.getFont()).getHeight();

                        taBounds.height = fontHeight + fontHeight / 2;
                        ta.repaint(taBounds);
                    }
                });
            }
        });

        parent.add(new JScrollPane(ta));
        parent.add(button);
    }
}
