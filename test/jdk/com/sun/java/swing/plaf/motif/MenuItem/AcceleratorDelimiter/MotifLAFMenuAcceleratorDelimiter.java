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
 * @bug 4210461
 * @summary Tests that Motif Look & Feel's MenuItem Accelerator Delimiter is
 * shown properly
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MotifLAFMenuAcceleratorDelimiter
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

public class MotifLAFMenuAcceleratorDelimiter {
    public static void main(String[] args) throws Exception {
        try {
            UIManager.setLookAndFeel(
                "com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        } catch (Exception e) {
            throw new RuntimeException("The Motif LAF failed to instantiate");
        }

        String INSTRUCTIONS = """
            The visual design specification for the Motif LAF asks for
            a "+" to delimit the other two entities in a menu item's
            accelerator.

            As a point of reference, the visual design specifications for the
            L&Fs are as follows: JLF/Metal = "-", Mac = "-", Motif = "+",
            Windows = "+".

            Click on "Menu" of "MotifLAFMenuAcceleratorDelimiter" window,
            make sure it shows MenuItem with label "Hi There! ^+H" or
            "Hi There! Ctrl+H".

            If it shows same label test passed otherwise failed.
            """;
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(MotifLAFMenuAcceleratorDelimiter::initialize)
            .build()
            .awaitAndCheck();
    }

    private static JFrame initialize() {
        JFrame fr = new JFrame("MotifLAFMenuAcceleratorDelimiter");
        JPanel menuPanel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        menuBar.setOpaque(true);
        JMenu exampleMenu = new JMenu("Menu");
        JMenuItem hiMenuItem = new JMenuItem("Hi There!");
        hiMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,
            ActionEvent.CTRL_MASK));
        exampleMenu.add(hiMenuItem);
        menuBar.add(exampleMenu);
        menuPanel.add(menuBar);

        fr.setLayout(new BorderLayout());
        fr.add(menuPanel, BorderLayout.CENTER);
        fr.setSize(250,100);
        return fr;
    }
}
