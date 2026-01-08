/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6681958
 * @summary Verifies Maximization state of JInternalFrames is
 *          not corrupted by WindowsDesktopManager
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual JIFMaximizedTrfAttribute
 */

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;

import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.UIManager;

import java.beans.PropertyVetoException;

public class JIFMaximizedTrfAttribute {

    static final String INSTRUCTIONS = """
        A maximised JInternalFrame would be shown with a
        button "open another internal frame".
        Press the button on the internal frame.
        A second internal frame is created and opened.
        If second internal frame is maximized,
        press Pass else press Fail.""";

    public static void main(String[] args) throws Exception {
        // Set Windows L&F
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PassFailJFrame.builder()
                .title("Test Instructions")
                .instructions(INSTRUCTIONS)
                .columns(50)
                .testUI(JIFMaximizedTrfAttribute::createUI)
                .position(PassFailJFrame.Position.TOP_LEFT_CORNER)
                .build()
                .awaitAndCheck();
    }

    static JFrame createUI() {

        final JDesktopPane pane = new JDesktopPane();
        final JInternalFrame internal = new JInternalFrame("the first internal frame", true, true, true, true);
        pane.add(internal);
        internal.setBounds(0, 0, 200, 100);
        try {
            internal.setMaximum(true);
        } catch (PropertyVetoException e) { }
        internal.setVisible(true);
        JButton button = new JButton("open another internal frame");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent evt) {
                final JInternalFrame internal2 = new JInternalFrame("another one", true, true, true, true);
                pane.add(internal2);
                internal2.setBounds(250, 150, 200, 100);
                try {
                    internal2.setMaximum(true);
                } catch (PropertyVetoException e) {}
                internal2.setVisible(true);
            }
        });
        internal.add(button, BorderLayout.SOUTH);

        JFrame f = new JFrame("JIFMaximizedTrfAttribute");
        f.add(pane, BorderLayout.CENTER);
        f.setSize(800, 600);
        f.setLocationRelativeTo(null);
        return f;
    }
}
