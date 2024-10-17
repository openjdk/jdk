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

/* Note that although this test makes use of Swing classes, like JFrame and */
/* JButton, it is really an AWT test, because it tests mechanism of sending */
/* paint events. */

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/*
 * @test
 * @bug 4174831
 * @summary Tests that frame do not flicker on diagonal resize
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FrameResizeTest_4
 */

public class FrameResizeTest_4 {
    private static final String INSTRUCTIONS = """
          Try enlarging the frame diagonally.
          If buttons inside frame excessively repaint themselves and flicker
          while you enlarge frame, the test fails.
          Otherwise, it passes.
          """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("FrameResizeTest_4 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(FrameResizeTest_4::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame f = new JFrame("FrameResizeTest_4 Flickering Frame");

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JButton("West"), BorderLayout.WEST);
        panel.add(new JButton("East"), BorderLayout.EAST);
        panel.add(new JButton("North"), BorderLayout.NORTH);
        panel.add(new JButton("South"), BorderLayout.SOUTH);
        panel.add(new JButton("Center"), BorderLayout.CENTER);
        f.setContentPane(panel);

        f.pack();
        f.setBounds(100, 50, 300, 200);

        return f;
    }
}
