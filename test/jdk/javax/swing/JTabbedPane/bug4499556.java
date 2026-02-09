/*
  Copyright (c) 2005, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4499556
 * @summary Use arbitrary (J)Components as JTabbedPane tab labels.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4499556
*/

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug4499556 {

    static final String INSTRUCTIONS = """
        The test window contains a tabbedPane with 4 tabs.

        Tab #0 without any tabComponent, just a title.
        Tab #1 with a JLabel and a little JButton wrapped into JPanel
        Tab #2 with a JButton (Delete #1) as a tabComponent
        Tab #3 with a JTextField as a tabComponent

        Check that tabbedPane and all tabComponents are shown properly
        for different tabLayout and tabPlacement policies,
        (you can change them with help of settings in the right panel),
        and for different looks and feels (you can change L&F by using the L&F menu)

        Remove tab #1 by clicking the Button labeled Delete #1 and then re-insert it
        by clicking "Insert #1". Check that it works - ie Delete #1 is restored.

        If everything displays and behaves as described, the test PASSES, otherwise it FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .instructions(INSTRUCTIONS)
            .columns(50)
            .testUI(bug4499556::createUI)
            .build()
            .awaitAndCheck();
    }

    static volatile JTabbedPane pane;
    static volatile JFrame frame;

    static JFrame createUI() {
        frame = new JFrame("bug4499556");
        pane = getTabbedPane();
        frame.add(pane);
        frame.add(getRightPanel(), BorderLayout.EAST);
        JMenu menu = new JMenu("L&F Menu");
        JMenuItem platformItem = new JMenuItem("Platform L&F");
        JMenuItem nimbusItem = new JMenuItem("Nimbus L&F");
        JMenuItem metalItem = new JMenuItem("Metal L&F");
        menu.add(platformItem);
        menu.add(nimbusItem);
        menu.add(metalItem);
        JMenuBar menuBar = new JMenuBar();
        menuBar.add(menu);
        frame.setJMenuBar(menuBar);
        platformItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setPlatformLAF();
            }
        });
        metalItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setMetalLAF();
            }
        });

        nimbusItem.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                setNimbusLAF();
            }
        });
        frame.setSize(500, 500);
        return frame;
      }

    static JTabbedPane getTabbedPane() {
        final JTabbedPane pane = new JTabbedPane();

        pane.add("Title", new JLabel(""));

        addCompoundTab(pane);

        pane.add("Title", new JLabel(""));
        pane.add("Title", new JLabel(""));

        final JButton button = new JButton("Delete #1");
        pane.setTabComponentAt(2, button);
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                if (pane.getTabCount() == 4) {
                    pane.remove(1);
                    button.setText("Insert #1");
                } else {
                    addCompoundTab(pane);
                    button.setText("Delete #1");
                }
            }
        });

        JTextField tf = new JTextField("JTextField", 7);
        pane.setTabComponentAt(3, tf);

        return pane;
    }

        static JComponent getRightPanel() {
        JComponent ret = new Box(BoxLayout.Y_AXIS);
        ret.setBorder(BorderFactory.createTitledBorder("Properties"));
        ret.setPreferredSize(new Dimension(100, 0));
        final JCheckBox checkBox = new JCheckBox();
        JPanel temp = new JPanel();
        temp.add(new JLabel("Scrollable"));
        temp.add(checkBox);
        pane.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
        checkBox.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                if (checkBox.isSelected()) {
                    pane.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
                } else {
                    pane.setTabLayoutPolicy(JTabbedPane.WRAP_TAB_LAYOUT);
                }
            }
        });
        checkBox.doClick();
        ret.add(temp);
        ButtonGroup group = new ButtonGroup();
        temp = new JPanel();
        JRadioButton topRadio = new JRadioButton("Top");
        temp.add(topRadio);
        topRadio.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                pane.setTabPlacement(JTabbedPane.TOP);
            }
        });
        ret.add(temp);
        temp = new JPanel();
        JRadioButton bottomRadio = new JRadioButton("Bottom");
        temp.add(bottomRadio);
        bottomRadio.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                pane.setTabPlacement(JTabbedPane.BOTTOM);
            }
        });
        ret.add(temp);
        temp = new JPanel();
        JRadioButton leftRadio = new JRadioButton("Left");
        temp.add(leftRadio);
        leftRadio.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                pane.setTabPlacement(JTabbedPane.LEFT);
            }
        });
        ret.add(temp);
        temp = new JPanel();
        JRadioButton rightRadio = new JRadioButton("Right");
        temp.add(rightRadio);
        rightRadio.addItemListener(new ItemListener() {
            public void itemStateChanged(ItemEvent e) {
                pane.setTabPlacement(JTabbedPane.RIGHT);
            }
        });
        ret.add(temp);
        group.add(topRadio);
        group.add(bottomRadio);
        group.add(leftRadio);
        group.add(rightRadio);
        return ret;
    }



    static void addCompoundTab(final JTabbedPane pane) {
        JLabel label = new JLabel("JLabel");
        label.setOpaque(true);
        JPanel testPanel = new JPanel();
        JLabel comp = new JLabel("JLabel");
        testPanel.add(comp);
        JButton closeButton = new CloseButton();
        closeButton.setPreferredSize(new Dimension(10, 10));
        testPanel.add(closeButton);
        testPanel.setOpaque(false);
        pane.insertTab("Test", null, new JLabel(""), "", 1);
        pane.setTabComponentAt(1, testPanel);
    }

    static class CloseButton extends JButton {
        public CloseButton() {
            final Object[] options = {"Fine"};
            addActionListener(new ActionListener() {
                public void actionPerformed(ActionEvent e) {
                    JOptionPane.showOptionDialog(null,
                            "How are you ?", "Hello !", JOptionPane.YES_OPTION,
                            JOptionPane.QUESTION_MESSAGE, null, options, null);
                }
            });
        }

        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            g.setColor(Color.black);
            g.drawLine(0, 0, getWidth()-1, getHeight()-1);
            g.drawLine(0, getHeight()-1, getWidth()-1, 0);
        }
    }

    static boolean setLAF(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        SwingUtilities.updateComponentTreeUI(frame);
        return true;
    }

    static final boolean setPlatformLAF() {
        return setLAF(UIManager.getSystemLookAndFeelClassName());
    }

    static final boolean setNimbusLAF() {
        return setLAF("javax.swing.plaf.nimbus.NimbusLookAndFeel");
    }

    static final boolean setMetalLAF() {
        return setLAF("javax.swing.plaf.metal.MetalLookAndFeel");
    }
}
