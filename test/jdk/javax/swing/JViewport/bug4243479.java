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
 * @bug 4243479
 * @summary Tests that JViewport do not blit when not visible
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4243479
 */

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.awt.Point;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;

public class bug4243479 {
    private static final String INSTRUCTIONS = """
        The tabbed pane contains two tabs: "button" and "scrollpane".
        The second contains some text inserted into a scrollpane.
        Press the "Press here" button.
        If a piece of scrollpane appears, press Fail else press Pass.""";

    private static JFrame createTestUI() {
        char[] text = new char[20000];
        for (int counter = 0; counter < text.length; counter++) {
            if (counter % 80 == 0) {
                text[counter] = '\n';
            }
            else {
                text[counter] = 'a';
            }
        }
        JScrollPane sp = new JScrollPane(new JTextArea
                                               (new String(text)));
        final JViewport vp = sp.getViewport();
        vp.putClientProperty("EnableWindowBlit", Boolean.TRUE);
        JTabbedPane tp = new JTabbedPane();
        JPanel panel = new JPanel(new FlowLayout());
        JButton button = new JButton("Press here");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent ae) {
                Point location = vp.getViewPosition();
                location.y += 100;
                vp.setViewPosition(location);
            }
        });
        panel.add(button);
        tp.add("button", panel);
        tp.add("scrollpane", sp);
        JFrame frame = new JFrame("4243479 Test");
        frame.getContentPane().add(tp);
        frame.pack();
        return frame;
    }

    public static void main(String[] argv) throws Exception {
         PassFailJFrame.builder()
                .title("JViewport Instructions")
                .instructions(INSTRUCTIONS)
                .rows(5)
                .columns(30)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .testUI(bug4243479::createTestUI)
                .build()
                .awaitAndCheck();
    }
}
