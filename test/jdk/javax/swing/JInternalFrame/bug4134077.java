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
 * @bug 4134077
 * @requires (os.family == "windows")
 * @summary Metal,Window:If JInternalFrame's title text is long last must be ellipsis
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4134077
 */

import java.awt.BorderLayout;
import java.awt.ComponentOrientation;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

public class bug4134077 {

    private static JFrame frame;

    private static final String INSTRUCTIONS = """
        Try to resize internal frame with diferrent combinations of
        LookAndFeels and title pane's buttons and orientation.

        The internal frame's title should clip if its too long to
        be entierly visible (ends by "...")
        and window can never be
        smaller than the width of the first two letters of the title
        plus "...".""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug4134077 Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(bug4134077::createTestUI)
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
        frame = new JFrame("bug4134077");
        JDesktopPane jdp = new JDesktopPane();
        frame.add(jdp);

        final JInternalFrame jif =
                new JInternalFrame("Very Long Title For Internal Frame", true);
        jdp.add(jif);
        jif.setSize(150,150);
        jif.setLocation(150, 50);
        jif.setComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
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

        JButton orientation = new JButton("Change orientation");
        orientation.addActionListener(e -> {
            jif.setComponentOrientation(
                jif.getComponentOrientation() == ComponentOrientation.LEFT_TO_RIGHT
                    ? ComponentOrientation.RIGHT_TO_LEFT
                    : ComponentOrientation.LEFT_TO_RIGHT);
        });
        p.add(orientation);

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
        frame.setSize(700, 300);
        return frame;
    }
}
