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
 * @bug 5035668
 * @summary Test that metal ToolBar border correctly sizes the MetalBumps used
 *          for the grip
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug5035668
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.GridLayout;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

public class bug5035668 {
    static final String INSTRUCTIONS = """
        This test is for Metal LaF only.

        All of them have an empty border around their own border.
        If you see that in any toolbar the grip (little dotted strip) overlaps
        the empty border press Fail. If you see that grips are completely
        inside empty borders press Pass.
    """;

    public static void main(String[] args) throws Exception {
        // Set metal l&f
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4251592 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug5035668::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Metal JToolBar Border Overlap Test");
        frame.setLayout(new BorderLayout());
        frame.setBackground(Color.white);

        // Horizontal toolbar left-to-right
        final JToolBar toolBar = new JToolBar();
        toolBar.setBorder(new CompoundBorder(new EmptyBorder(10, 10, 10, 10),
                toolBar.getBorder()));
        toolBar.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
        toolBar.add(new ToolBarButton(toolBar));

        // Horizontal toolbar right-to-left
        JToolBar toolBar2 = new JToolBar();
        toolBar2.setBorder(new CompoundBorder(new EmptyBorder(10, 10, 10, 10),
                toolBar2.getBorder()));
        toolBar2.setComponentOrientation(ComponentOrientation.RIGHT_TO_LEFT);
        toolBar2.add(new ToolBarButton(toolBar2));

        JPanel topPanel = new JPanel(new GridLayout(2, 0));
        topPanel.add(toolBar);
        topPanel.add(toolBar2);
        frame.add(topPanel, BorderLayout.NORTH);

        JToolBar toolBar3 = new JToolBar();
        toolBar3.setBorder(new CompoundBorder(new EmptyBorder(10, 10, 10, 10),
                toolBar3.getBorder()));
        toolBar3.setOrientation(JToolBar.VERTICAL);
        toolBar3.add(new ToolBarButton(toolBar3));
        frame.add(toolBar3, BorderLayout.EAST);

        frame.setSize(200, 200);
        return frame;
    }

    static class ToolBarButton extends JButton {
        final JToolBar toolBar;

        public ToolBarButton(JToolBar p_toolBar) {
            super("Change toolbar's orientation");
            this.toolBar = p_toolBar;
            addActionListener(e -> toolBar.setOrientation(1 - toolBar.getOrientation()));
        }
    }
}
