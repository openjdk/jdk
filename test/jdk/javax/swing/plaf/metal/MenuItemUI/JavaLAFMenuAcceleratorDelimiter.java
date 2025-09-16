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
 * @summary Confirm Metal Look & Feel's MenuItem Accelerator Delimiter
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual JavaLAFMenuAcceleratorDelimiter
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

public class JavaLAFMenuAcceleratorDelimiter {
    static final String INSTRUCTIONS = """
        A simple check. The visual design specification for JLF/Metal asks for
        a "-" to delimit the other two entities in a menu item's accelerator.
        The test passes if the delimiter for the accelerator is correct when
        opening the example menu. Otherwise, the test fails.
    """;

    public static void main(String[] args) throws Exception {
        // Set Metal L&F
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        PassFailJFrame.builder()
                .title("JavaLAFMenuAcceleratorDelimiter Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(JavaLAFMenuAcceleratorDelimiter::createUI)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Metal L&F Accelerator Delimiter Test");
        JPanel menuPanel = new JPanel();
        JMenuBar menuBar = new JMenuBar();
        menuBar.setOpaque(true);
        JMenu exampleMenu = new JMenu("Example");
        JMenuItem hiMenuItem = new JMenuItem("Hi There!");
        hiMenuItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_H,
                ActionEvent.CTRL_MASK));
        exampleMenu.add(hiMenuItem);
        menuBar.add(exampleMenu);
        menuPanel.add(menuBar);

        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(menuPanel, BorderLayout.CENTER);
        frame.setSize(250, 150);
        return frame;
    }
}
