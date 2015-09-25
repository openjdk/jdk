/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.EventQueue;
import java.awt.FlowLayout;

import javax.swing.*;
import javax.swing.UIManager.LookAndFeelInfo;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/**
 * @test
 * @bug 8134947
 * @author Sergey Bylokhov
 * @run main/timeout=300/othervm -Xmx12m -XX:+HeapDumpOnOutOfMemoryError UnninstallUIMemoryLeaks
 */
public final class UnninstallUIMemoryLeaks {

    private static JFrame frame;

    public static void main(final String[] args) throws Exception {
        try {
            createGUI();
            for (final LookAndFeelInfo laf : getInstalledLookAndFeels()) {
                final String name = laf.getName();
                if (name.contains("OS X") || name.contains("Metal")) {
                    SwingUtilities.invokeAndWait(() -> setLookAndFeel(laf));
                    SwingUtilities.invokeAndWait(() -> {
                        for (int i = 0; i < 4000; ++i) {
                            SwingUtilities.updateComponentTreeUI(frame);
                        }
                    });
                }
            }
        } finally {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }
    }

    private static void createGUI() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new JFrame();
            frame.setLayout(new FlowLayout());

            frame.add(new JButton("JButton"));
            frame.add(new JCheckBox("JCheckBox"));
            frame.add(new JComboBox<>());
            frame.add(new JEditorPane());
            frame.add(new JFormattedTextField("JFormattedTextField"));
            frame.add(new JLabel("label"));
            frame.add(new JPanel());
            frame.add(new JPasswordField("JPasswordField"));
            frame.add(new JProgressBar());
            frame.add(new JRadioButton("JRadioButton"));
            frame.add(new JScrollBar());
            frame.add(new JScrollPane());
            frame.add(new JSeparator());
            frame.add(new JSlider());
            frame.add(new JSpinner());
            frame.add(new JSplitPane());
            frame.add(new JTabbedPane());
            frame.add(new JTable());
            frame.add(new JTextArea("JTextArea"));
            frame.add(new JTextField("JTextField"));
            frame.add(new JTextPane());
            frame.add(new JToggleButton());
            frame.add(new JToolBar());
            frame.add(new JToolTip());
            frame.add(new JTree());
            frame.add(new JViewport());

            final JMenuBar bar = new JMenuBar();
            final JMenu menu1 = new JMenu("menu1");
            final JMenu menu2 = new JMenu("menu2");
            menu1.add(new JMenuItem("menuitem"));
            menu2.add(new JCheckBoxMenuItem("JCheckBoxMenuItem"));
            menu2.add(new JRadioButtonMenuItem("JRadioButtonMenuItem"));
            bar.add(menu1);
            bar.add(menu2);
            frame.setJMenuBar(bar);

            final String[] data = {"one", "two", "three", "four"};
            final JList<String> list = new JList<>(data);
            frame.add(list);

            final JDesktopPane pane = new JDesktopPane();
            final JInternalFrame internalFrame = new JInternalFrame();
            internalFrame.setBounds(10, 10, 130, 130);
            internalFrame.setVisible(true);
            pane.add(internalFrame);
            pane.setSize(150, 150);

            frame.add(pane);
            frame.pack();
            frame.setSize(600, 600);
            frame.setLocationRelativeTo(null);
            // Commented to prevent a reference from RepaintManager
            // frame.setVisible(true);
        });
    }

    private static void setLookAndFeel(final LookAndFeelInfo laf) {
        try {
            UIManager.setLookAndFeel(laf.getClassName());
            System.out.println("LookAndFeel: " + laf.getClassName());
        } catch (ClassNotFoundException | InstantiationException |
                UnsupportedLookAndFeelException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
