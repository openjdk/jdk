/*
 * Copyright (c) 2014, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import javax.swing.AbstractAction;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/* @test
 * @key headful
 * @bug 8038113 8258979
 * @summary [macosx] JTree icon is not rendered in high resolution on Retina,
 *          collapsed icon is not rendered for GTK LAF as well.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug8038113
 */

public class bug8038113 {
    private static JFrame frame;
    private static final String INSTRUCTIONS = """
                Verify that scaled icons are rendered smoothly.
                Check that Collapsed  and Expanded JTree icons are drawn smoothly.
                Check for different LAFs.
                If so, press PASS, else press FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame passFailJFrame = new PassFailJFrame.Builder()
                .title("JTree Expanded/Collapsed Icon Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(6)
                .columns(40)
                .screenCapture()
                .build();
        SwingUtilities.invokeAndWait(
                bug8038113::createAndShowUI);
        passFailJFrame.awaitAndCheck();
    }

    public static void createAndShowUI() {
        final JTree tree = new JTree();
        final BasicTreeUI treeUI = (BasicTreeUI) tree.getUI();
        frame = new JFrame("Test Tree Icon Rendering");

        final JPanel panel = new JPanel() {

            @Override
            public void paint(Graphics g) {
                super.paint(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setStroke(new BasicStroke(0.5f));
                g2.scale(2, 2);

                int x = 10;
                int y = 10;
                Icon collapsedIcon = treeUI.getCollapsedIcon();
                Icon expandeIcon = treeUI.getExpandedIcon();
                int w = collapsedIcon.getIconWidth();
                int h = collapsedIcon.getIconHeight();
                collapsedIcon.paintIcon(this, g, x, y);
                g.drawRect(x, y, w, h);

                y += 10 + h;
                w = expandeIcon.getIconWidth();
                h = expandeIcon.getIconHeight();
                expandeIcon.paintIcon(this, g, x, y);
                g.drawRect(x, y, w, h);
            }
        };

        UIManager.LookAndFeelInfo[] laf = UIManager.getInstalledLookAndFeels();
        JPanel buttonPanel = new JPanel();
        for (int i = 0; i < laf.length; i++) {
            if (laf[i].getName().contains("Motif")) {
                continue;
            }
            JButton button = new JButton(laf[i].getName());
            button.setText(laf[i].getName());
            button.addActionListener(new MyAction());
            buttonPanel.add(button);
        }
        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        frame.getContentPane().setLayout(new BorderLayout());
        frame.getContentPane().add(panel, BorderLayout.CENTER);
        frame.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
        frame.setSize(300, 250);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }

    public static class MyAction implements ActionListener {
        public void actionPerformed(ActionEvent ae) {
            String lafClassName = null;
            UIManager.LookAndFeelInfo lafs[] = UIManager.getInstalledLookAndFeels();
            for (int i = 0; i < lafs.length; i++) {
                if (ae.getActionCommand().equals(lafs[i].getName())) {
                    lafClassName = lafs[i].getClassName();
                    break;
                }
            }
            try {
                UIManager.setLookAndFeel(lafClassName);
                if (frame != null) {
                    frame.dispose();
                }
                createAndShowUI();
            } catch (UnsupportedLookAndFeelException ignored) {
                System.out.println("Unsupported LAF: " + lafClassName);
            } catch (ClassNotFoundException | InstantiationException
                     | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
