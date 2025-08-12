/*
 * Copyright (c) 2007, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4211731 4214512
 * @summary
 *     This test checks if menu bars lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT. This test is
 *     manual.  The tester is asked to compare left-to-right and
 *     right-to-left menu bars and judge whether they are mirror images of each
 *     other.
 * @library /test/jdk/java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation
 */

import java.awt.ComponentOrientation;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class RightLeftOrientation {

    static JFrame ltrFrame;
    static JFrame rtlFrame;

    private static final String INSTRUCTIONS = """
        This test checks menu bars for correct Right-To-Left Component Orientation.

        You should see two frames, each containing a menu bar.

        One frame will be labelled "Left To Right" and will contain
        a menu bar with menus starting on its left side.
        The other frame will be labelled "Right To Left" and will
        contain a menu bar with menus starting on its right side.

        The test will also contain radio buttons that can be used to set
        the look and feel of the menu bars.
        For each look and feel, you should compare the two menu
        bars and make sure they are mirror images of each other. """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                 .title("RTL test Instructions")
                 .instructions(INSTRUCTIONS)
                 .rows((int) INSTRUCTIONS.lines().count() + 2)
                 .columns(30)
                 .testUI(RightLeftOrientation::createTestUI)
                 .build()
                 .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        JFrame frame = new JFrame("RightLeftOrientation");
        JPanel panel = new JPanel();

        ButtonGroup group = new ButtonGroup();
        JRadioButton rb;
        ActionListener plafChanger = new PlafChanger();

        UIManager.LookAndFeelInfo[] lafInfos = UIManager.getInstalledLookAndFeels();
        for (int i = 0; i < lafInfos.length; i++) {
            rb = new JRadioButton(lafInfos[i].getName());
            rb.setActionCommand(lafInfos[i].getClassName());
            rb.addActionListener(plafChanger);
            group.add(rb);
            panel.add(rb);
            if (i == 0) {
                rb.setSelected(true);
            }
        }

        frame.add(panel);

        ltrFrame = new JFrame("Left To Right");
        ltrFrame.setJMenuBar(createMenuBar(ComponentOrientation.LEFT_TO_RIGHT));
        ltrFrame.setSize(400, 100);
        ltrFrame.setLocation(new Point(10, 10));
        ltrFrame.setVisible(true);

        rtlFrame = new JFrame("Right To Left");
        rtlFrame.setJMenuBar(createMenuBar(ComponentOrientation.RIGHT_TO_LEFT));
        rtlFrame.setSize(400, 100);
        rtlFrame.setLocation(new Point(10, 120));
        rtlFrame.setVisible(true);
        frame.pack();
        return frame;
    }

    static class PlafChanger implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            String lnfName = e.getActionCommand();

            try {
                UIManager.setLookAndFeel(lnfName);
                SwingUtilities.updateComponentTreeUI(ltrFrame);
                SwingUtilities.updateComponentTreeUI(rtlFrame);
            }
            catch (Exception exc) {
                System.err.println("Could not load LookAndFeel: " + lnfName);
            }

        }
    }


    static JMenuBar createMenuBar(ComponentOrientation o) {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setComponentOrientation(o);

        JMenu menu = new JMenu("One");
        menu.setComponentOrientation(o);
        menuBar.add(menu);

        menu = new JMenu("Two");
        menu.setComponentOrientation(o);
        menuBar.add(menu);

        menu = new JMenu("Three");
        menu.setComponentOrientation(o);
        menuBar.add(menu);

        return menuBar;
    }

}
