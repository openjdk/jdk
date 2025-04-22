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
 * @bug 4305622
 * @summary MetalToolBarUI.installUI invokeLater causes flickering
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4305622
 */

import java.awt.BorderLayout;
import java.awt.Color;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JToolBar;
import javax.swing.UIManager;
import javax.swing.border.LineBorder;


public class bug4305622 {
    private static JFrame fr;
    static final String INSTRUCTIONS = """
        Press button "Create ToolBar" at frame "Create ToolBar Test".
        If you see any flickering during creating of toolbar
        then the test FAILS, otherwise the test PASSES.
    """;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("bug4305622 Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug4305622::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        fr = new JFrame("Create ToolBar Test");
        JButton button = new JButton("Create ToolBar");
        button.addActionListener(ae -> addToolBar());
        fr.add(button, BorderLayout.SOUTH);
        fr.setSize(400, 400);
        return fr;
    }

    static void addToolBar() {
        fr.repaint();
        fr.revalidate();
        JToolBar toolbar = new JToolBar();

        JButton btn = new JButton("Button 1");
        btn.setBorder(new LineBorder(Color.red, 30));
        toolbar.add(btn);

        btn = new JButton("Button 2");
        btn.setBorder(new LineBorder(Color.red, 30));
        toolbar.add(btn);

        toolbar.updateUI();
        fr.add(toolbar, BorderLayout.NORTH);
    }
}
