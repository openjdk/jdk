/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4978274
 * @summary Tests that JButton is painted with same visible height
 *          as toggle buttons
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4978274
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.swing.plaf.metal.OceanTheme;

public class bug4978274 {
    private static final String INSTRUCTIONS = """
            The toggle buttons must be painted to the same visible
            height as button. In addition to that verify the following:

            a) All three buttons - "Button", "Toggle Btn" and
               "Selected Toggle Btn" have the same border.

            b) Verify that when "Button" is pressed and moused over
               it has the EXACT same border as "Toggle Btn" and
               "Selected Toggle Btn" on press & mouse over.

            c) Click to the test window (panel) to disable/enable all
               three buttons. In disabled state verify that all three
               buttons have the exact same border.

            If all of the above conditions are true press PASS, else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        MetalLookAndFeel.setCurrentTheme(new OceanTheme());
        UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(createAndShowUI())
                .build()
                .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        JFrame frame = new JFrame("bug4978274");
        frame.setLayout(new BorderLayout());

        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout());
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));
        JButton jButton = new JButton("Button");
        JToggleButton jToggleButton = new JToggleButton("Selected Toggle Btn");
        jToggleButton.setSelected(true);

        panel.add(jButton);
        panel.add(new JToggleButton("Toggle Btn"));
        panel.add(jToggleButton);

        panel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent event) {
              jButton.setEnabled(!jButton.isEnabled());
              jToggleButton.setEnabled(jButton.isEnabled());
                for(int i = 0; i < panel.getComponentCount(); i++) {
                    panel.getComponent(i).setEnabled(jButton.isEnabled());
                }
            }
        });

        frame.add(panel);
        frame.pack();
        return frame;
    }
}
