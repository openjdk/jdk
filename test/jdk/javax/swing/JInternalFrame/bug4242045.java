/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4242045
 * @requires (os.family == "windows")
 * @summary JInternalFrame titlepane icons should be restored after attribute change
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4242045
 */

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class bug4242045 {

    private static JFrame frame;

    private static final String INSTRUCTIONS = """
        Add and remove iconify/maximize/close buttons using the buttons
        "Iconifiable", "Maximizable", "Closable" under different LookAndFeels.
        If they appears and disappears correctly then test passes. If any
        button does not appear or disappear as expected or appear with incorrect
        placement then test fails.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4242045 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4242045::createTestUI)
                .build()
                .awaitAndCheck();
    }

     private static void setLF(ActionEvent e) {
        try {
            UIManager.setLookAndFeel(((JButton)e.getSource()).getActionCommand());
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (ClassNotFoundException | InstantiationException
                 | UnsupportedLookAndFeelException
                 | IllegalAccessException ex) {
             throw new RuntimeException(ex);
        }
    }

    private static JFrame createTestUI() {

        frame = new JFrame("bug4242045");
        JDesktopPane jdp = new JDesktopPane();
        JInternalFrame jif = new JInternalFrame("Test", true);
        frame.add(jdp);

        jdp.add(jif);
        jif.setSize(150, 150);
        jif.setVisible(true);

        JPanel p = new JPanel();

        JButton metal = new JButton("Metal");
        metal.setActionCommand("javax.swing.plaf.metal.MetalLookAndFeel");
        metal.addActionListener((ActionEvent e) -> setLF(e));
        p.add(metal);

        JButton windows = new JButton("Windows");
        windows.setActionCommand("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        windows.addActionListener((ActionEvent e) -> setLF(e));
        p.add(windows);

        JButton motif = new JButton("Motif");
        motif.setActionCommand("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
        motif.addActionListener((ActionEvent e) -> setLF(e));
        p.add(motif);

        JButton clo = new JButton("Closable");
        clo.addActionListener(e -> jif.setClosable(!jif.isClosable()));
        p.add(clo);

        JButton ico = new JButton("Iconifiable");
        ico.addActionListener(e -> jif.setIconifiable(!jif.isIconifiable()));
        p.add(ico);

        JButton max = new JButton("Maximizable");
        max.addActionListener(e -> jif.setMaximizable(!jif.isMaximizable()));
        p.add(max);

        frame.add(p, BorderLayout.SOUTH);
        frame.setSize(650, 250);
        return frame;
    }

}
