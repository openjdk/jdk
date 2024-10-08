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

import java.awt.Button;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JButton;

/*
 * @test
 * @bug 4431868
 * @summary Tests that hw container doesn't receive mouse enter/exit events when mouse
 *          is moved between its lw and hw children
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MouseEnterExitTest3
 */

public class MouseEnterExitTest3 {
    static final Button button = new Button("Button");
    static final JButton jbutton = new JButton("JButton");
    static final Frame frame = new Frame("Mouse Enter/Exit Test");

    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                1. Move the mouse between Button and JButton
                2. Verify that the frame doesn't receive enter/exit events
                   (Enter/exit events are dumped to the area below)
                4. If you see enter/exit events dumped the test fails
                        """;

        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .rows((int) INSTRUCTIONS.lines().count() + 2)
                .columns(35)
                .testUI(initialize())
                .logArea(4)
                .build()
                .awaitAndCheck();
    }

    final static MouseListener listener = new MouseAdapter() {
        public void mouseEntered(MouseEvent e) {
            PassFailJFrame.log(e.toString());
        }

        public void mouseExited(MouseEvent e) {
            PassFailJFrame.log(e.toString());
        }
    };

    public static Frame initialize() {
        frame.setLayout(new GridLayout(2, 1));
        frame.add(button);
        frame.add(jbutton);
        frame.addMouseListener(listener);
        frame.pack();
        return frame;
    }
}
