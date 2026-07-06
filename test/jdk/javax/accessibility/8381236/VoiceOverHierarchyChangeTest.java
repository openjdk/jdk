/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/*
 * @test
 * @key headful
 * @bug 8381236
 * @summary manual test for VoiceOver that moves Components across Windows
 * @requires os.family == "mac"
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual VoiceOverHierarchyChangeTest
 */

public class VoiceOverHierarchyChangeTest {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
                INSTRUCTIONS:
                1. Open VoiceOver
                2. Move the mouse over the "Does Nothing" button
                3. Click the "Move To Other Window" button
                4. Move the mouse over the "Does Nothing" button

                Expected behavior: VoiceOver reads "Does Nothing" after steps
                2 and 4.
                """;

        PassFailJFrame.builder()
                .title("VoiceOverHierarchyChangeTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(VoiceOverHierarchyChangeTest::createUI)
                .build()
                .awaitAndCheck();
    }
    public static JFrame createUI() {
        JFrame f1 = new JFrame();
        f1.getContentPane().setPreferredSize(new Dimension(300, 100));
        JFrame f2 = new JFrame();
        f2.getContentPane().setPreferredSize(new Dimension(300, 100));

        JButton hopButton = new JButton("Move To Other Window");
        JButton noopButton = new JButton("Does Nothing");
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(hopButton, BorderLayout.NORTH);
        panel.add(noopButton, BorderLayout.SOUTH);

        hopButton.addActionListener(e -> {
            if (SwingUtilities.isDescendingFrom(hopButton, f1)) {
                f2.getContentPane().add(panel);
            } else {
                f1.getContentPane().add(panel);
            }
            f1.repaint();
            f2.repaint();
        });

        f1.getContentPane().add(panel);
        f1.pack();
        f2.pack();

        f1.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentMoved(ComponentEvent e) {
                Rectangle r = f1.getBounds();
                f2.setLocation(r.x, r.y + r.height);
                f2.setVisible(true);
            }
        });

        return f1;
    }
}
