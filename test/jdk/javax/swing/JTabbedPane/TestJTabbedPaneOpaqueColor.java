/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.GridLayout;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;

/*
 * @test
 * @bug 4690946 8226990 6462396
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "windows" | os.family == "linux")
 * @summary Test JTabbedPane's contentarea, tab area and tab color
 *          for different LAFs when opacity is enabled or disabled.
 * @run main/manual TestJTabbedPaneOpaqueColor
 */

public class TestJTabbedPaneOpaqueColor {
    private static JFrame frame;
    private static JTabbedPane tabPane;
    private static final String INSTRUCTIONS = """
            The background color of panel which contains the tabbed pane is green.
            The background color of the tabbed pane is red.
            The TabbedPane is not opaque initially.
            For 'Content Opaque' and 'Tabs Opaque' to have effect, tab pane opacity should
            be set to false i.e. Opaque checkbox should be unchecked.
            NOTE: For Nimbus LAF, tabs color are specific to nimbus style.

            Check the default behaviour of the tabbed pane:
              - the area behind tabs is transparent (it must be green).
              - the tabs area is opaque (it must be red, except the selected tab which must be gray).
              - the content area is opaque (it must be gray).

            Test Case 1 - Test TabPane Opacity:
            NOTE: For Nimbus LAF, tabs color are always gray in color.

            Verify the following with 'opaque' option:
            when checked:
              - the area behind tabs is opaque (it must be red).
              - the tabs area is opaque (it must be red, except the selected tab which must be gray).
              - the content area is opaque (it must be gray).
            when unchecked:
              - the area behind tabs is transparent (it must be green).
              - the tabs area is opaque (it must be red, except the selected tab which must be gray).
              - the content area is opaque (it must be gray).

            Check this behaviour by clicking on present L&F button and tab layout.

            Test Case 2 - Test Content pane opacity:
            To test Content pane opacity, make sure "Opaque checkbox" is UNCHECKED.

            Verify the following with 'content opaque' option:
            when checked:
              - the content area should be opaque (it must be gray).
            when unchecked:
              - the content area should be transparent (it must be green).

            Check this behaviour by clicking on present L&F button and tab layout.

            Test Case 3 - Test Tabs opacity:
            To test Tabs opacity, make sure "Opaque checkbox" is UNCHECKED.
            NOTE: For Nimbus LAF, tabs color are specific to nimbus style.
                  All tabs are gray in color if tabs opaque is checked.

            Verify the following with 'tabs opaque' option:
            when checked:
              - the tabs are opaque (it must be red, except the selected tab which must be gray).
            when unchecked:
              - the tabs are transparent (it must be green).

            Check this behaviour by clicking on present L&F button and tab layout.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("JTabbedPane Tab and Content Area Color Test Instructions")
            .instructions(INSTRUCTIONS)
            .testTimeOut(10)
            .rows(25)
            .columns(60)
            .testUI(TestJTabbedPaneOpaqueColor::createAndShowUI)
            .build()
            .awaitAndCheck();
    }

    private static JFrame createAndShowUI() {
        int NUM_TABS = 15;
        frame = new JFrame("Test JTabbedPane Opaque Color");
        JTabbedPane tabPane = new JTabbedPane();
        tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
        tabPane.setTabPlacement(JTabbedPane.TOP);
        PassFailJFrame.positionTestWindow(
                frame, PassFailJFrame.Position.HORIZONTAL);
        for (int i = 0; i < NUM_TABS; ++i) {
            tabPane.addTab("Tab " + i , new JLabel("Content Area"));
        }
        JPanel panel = new JPanel(new BorderLayout());
        panel.add(tabPane, BorderLayout.CENTER);
        panel.setBackground(Color.green);
        tabPane.setBackground(Color.red);

        UIManager.LookAndFeelInfo[] laf = UIManager.getInstalledLookAndFeels();
        JPanel lafButtonPanel = new JPanel(new GridLayout(1, 3));
        for (int i = 0; i < laf.length; ++i) {
            if (laf[i].getName().contains("Motif")
                || laf[i].getName().contains("Windows")) {
                continue;
            }
            JButton button = new JButton(laf[i].getName());
            button.setText(laf[i].getName());
            button.addActionListener(new MyAction());
            lafButtonPanel.add(button);
        }

        JButton scrollButton = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
            }
        });
        scrollButton.setText("SCROLL layout");

        JPanel layoutButtonPanel = new JPanel(new GridLayout(1, 2));
        JButton wrapButton = new JButton(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
            }
        });
        wrapButton.setText("WRAP layout");

        layoutButtonPanel.add(scrollButton);
        layoutButtonPanel.add(wrapButton);

        JCheckBox contentOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (((AbstractButton)e.getSource()).isSelected()) {
                    UIManager.put("TabbedPane.contentOpaque", Boolean.TRUE);
                } else {
                    UIManager.put("TabbedPane.contentOpaque", Boolean.FALSE);
                }
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        contentOpaqueChkBox.setText("Content Opaque");
        contentOpaqueChkBox.setSelected(true);
        contentOpaqueChkBox.setEnabled(true);

        JCheckBox tabOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                if (((AbstractButton)e.getSource()).isSelected()) {
                    UIManager.put("TabbedPane.tabsOpaque", Boolean.TRUE);
                } else {
                    UIManager.put("TabbedPane.tabsOpaque", Boolean.FALSE);
                }
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        tabOpaqueChkBox.setText("Tabs Opaque");
        tabOpaqueChkBox.setSelected(true);
        tabOpaqueChkBox.setEnabled(true);

        JCheckBox tabPaneOpaqueChkBox = new JCheckBox(new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                tabPane.setOpaque(((AbstractButton)e.getSource()).isSelected());
                contentOpaqueChkBox.setEnabled(!((AbstractButton)e.getSource()).isSelected());
                tabOpaqueChkBox.setEnabled(!((AbstractButton)e.getSource()).isSelected());
                tabPane.repaint();
                SwingUtilities.updateComponentTreeUI(frame);
            }
        });
        tabPaneOpaqueChkBox.setText("Opaque");

        JPanel checkBoxPanel = new JPanel();
        checkBoxPanel.add(tabPaneOpaqueChkBox);
        checkBoxPanel.add(contentOpaqueChkBox);
        checkBoxPanel.add(tabOpaqueChkBox);

        JPanel nestedPanels = new JPanel(new GridLayout(2, 1));
        nestedPanels.add(lafButtonPanel);
        nestedPanels.add(layoutButtonPanel);
        panel.add(checkBoxPanel, BorderLayout.NORTH);
        panel.add(nestedPanels, BorderLayout.SOUTH);
        frame.add(panel);
        frame.setSize(500, 500);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
        return frame;
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
