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

/* @test
 * @bug 4212464
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Verify popup menu borders are drawn correctly when switching L&Fs
 * @run main/manual bug4212464
 */

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4212464 extends JFrame implements ActionListener {

    static String strMotif = "Motif";
    static String motifClassName = "com.sun.java.swing.plaf.motif.MotifLookAndFeel";

    static String strMetal = "Metal";
    static String metalClassName = "javax.swing.plaf.metal.MetalLookAndFeel";

    static bug4212464 frame;
    static JPopupMenu popup;

    static final String INSTRUCTIONS = """
        This test is to see whether popup menu borders behave properly when switching
        back and forth between Motif and Metal L&F.  The initial L&F is Metal.

        Pressing the mouse button on the label in the center of the test window brings
        up a popup menu.

        In order to test, use the labeled buttons to switch the look and feel.
        Clicking a button will cause the menu to be hidden. This is OK. Just click the label again.
        Switch back and forth and verify that the popup menu border changes consistently
        and there is a title for the menu when using Motif L&F (Metal won't have a title).

        Make sure you switch back and forth several times.
        If the change is consistent, press PASS otherwise press FAIL.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4212464::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        try {
            UIManager.setLookAndFeel(metalClassName); // initialize to Metal.
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        frame = new bug4212464("bug4212464");
        popup = new JPopupMenu("Test");
        popup.add("Item 1");
        popup.add("Item 2");
        popup.add("Item 3");
        popup.add("Item 4");

        JPanel p = new JPanel();
        p.setLayout(new FlowLayout());
        JButton motif = (JButton)p.add(new JButton(strMotif));
        JButton metal = (JButton)p.add(new JButton(strMetal));
        motif.setActionCommand(motifClassName);
        metal.setActionCommand(metalClassName);
        motif.addActionListener(frame);
        metal.addActionListener(frame);
        frame.add(BorderLayout.NORTH, p);

        JLabel l = new JLabel("Click any mouse button on this big label");
        l.setFont(new Font(Font.DIALOG, Font.PLAIN, 20));
        l.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });
        frame.add(BorderLayout.CENTER, l);
        frame.setSize(500, 400);
        return frame;
    }

     public bug4212464(String title) {
         super(title);
     }

    public void actionPerformed(ActionEvent e) {
        String str = e.getActionCommand();
        if (str.equals(metalClassName) || str.equals(motifClassName)) {
            changeLNF(str);
        } else {
            System.out.println("ActionEvent: " + str);
        }
    }

    public void changeLNF(String str) {
        System.out.println("Changing LNF to " + str);
        try {
            UIManager.setLookAndFeel(str);
            SwingUtilities.updateComponentTreeUI(frame);
            SwingUtilities.updateComponentTreeUI(popup);
        } catch (Exception e) {
           throw new RuntimeException(e);
        }
    }
}
