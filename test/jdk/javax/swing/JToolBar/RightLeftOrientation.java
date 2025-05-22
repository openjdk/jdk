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
 * @bug 4214514
 * @summary
 *     This test checks if tool bars lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT. This test is
 *     manual.  The tester is asked to compare left-to-right and
 *     right-to-left tool bars and judge whether they are mirror images of each
 *     other.
 * @library /test/jdk/java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToolBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class RightLeftOrientation {

    static JFrame ltrFrame;
    static JFrame rtlFrame;

    private static final String INSTRUCTIONS = """
        This test checks tool bars for correct Right-To-Left Component Orientation.

        You should see two frames, each containing a tool bar.

        One frame will be labelled "Left To Right" and will contain
        a tool bar with buttons starting on its left side.
        The other frame will be labelled "Right To Left" and will
        contain a tool bar with buttons starting on its right side.

        The test will also contain radio buttons that can be used to set
        the look and feel of the tool bars.
        For each look and feel, you should compare the two tool bars and
        make sure they are mirror images of each other.
        You should also drag the tool bars to each corner of the frame
        to make sure the docking behavior is consistent between the two frames.""";

     public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                 .title("RTL test Instructions")
                 .instructions(INSTRUCTIONS)
                 .rows((int) INSTRUCTIONS.lines().count() + 2)
                 .columns(35)
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
        Container contentPane = ltrFrame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        panel = new JPanel();
        panel.setBackground(Color.white);
        contentPane.add("Center",panel);
        contentPane.add("North",
                        createToolBar(ComponentOrientation.LEFT_TO_RIGHT));
        ltrFrame.setSize(400, 140);
        ltrFrame.setLocation(new Point(10, 10));
        ltrFrame.setVisible(true);

        rtlFrame = new JFrame("Right To Left");
        contentPane = rtlFrame.getContentPane();
        contentPane.setLayout(new BorderLayout());
        panel = new JPanel();
        panel.setBackground(Color.white);
        contentPane.add("Center",panel);
        contentPane.add("North",
                        createToolBar(ComponentOrientation.RIGHT_TO_LEFT));
        rtlFrame.setSize(400, 140);
        rtlFrame.setLocation(new Point(420, 10));
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


    static JToolBar createToolBar(ComponentOrientation o) {
        JToolBar toolBar = new JToolBar();
        toolBar.setComponentOrientation(o);

        JButton button = new JButton("One");
        button.setComponentOrientation(o);
        toolBar.add(button);

        button = new JButton("Two");
        button.setComponentOrientation(o);
        toolBar.add(button);

        button = new JButton("Three");
        button.setComponentOrientation(o);
        toolBar.add(button);

        return toolBar;
    }

}
