/*
 * Copyright (c) 2007, 2025, Oracle and/or its affiliates. All rights reserved.
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
 *     ComponentOrientation property is set to RIGHT_TO_LEFT.
 *     The tester is asked to compare left-to-right and
 *     right-to-left menu bars and decide whether they are mirror
 *     images of each other.
 * @library /test/jdk/java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation
 */

import java.awt.ComponentOrientation;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class RightLeftOrientation {

    private static List<JFrame> frames;

    private static final String INSTRUCTIONS = """
        This test checks menu bars for correct Right-To-Left component orientation.

        You should see two frames, each contains a menu bar.
        One frame is labelled "Left To Right" and contains
        a menu bar with menus starting on its left side.
        The other frame is labelled "Right To Left" and
        contains a menu bar with menus starting on its right side.

        The test also displays a frame with radio buttons
        to change the look and feel of the menu bars.
        For each look and feel, compare the two menu bars
        in LTR and RTL orientation and make sure they are mirror
        images of each other.""";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                 .title("Menu Bar RTL Instructions")
                 .instructions(INSTRUCTIONS)
                 .columns(30)
                 .testUI(RightLeftOrientation::createTestUI)
                 .positionTestUIRightColumn()
                 .build()
                 .awaitAndCheck();
    }

    private static JFrame createPlafChangerFrame() {
        JFrame frame = new JFrame("Change Look and Feel");
        JPanel panel = new JPanel();

        ButtonGroup group = new ButtonGroup();
        ActionListener plafChanger = new PlafChanger();

        UIManager.LookAndFeelInfo[] lafInfos = UIManager.getInstalledLookAndFeels();
        for (int i = 0; i < lafInfos.length; i++) {
            JRadioButton rb = new JRadioButton(lafInfos[i].getName());
            rb.setActionCommand(lafInfos[i].getClassName());
            rb.addActionListener(plafChanger);
            group.add(rb);
            panel.add(rb);
            if (i == 0) {
                rb.setSelected(true);
            }
        }

        frame.add(panel);
        frame.pack();
        return frame;
    }

    private static List<JFrame> createTestUI() {
        JFrame plafFrame = createPlafChangerFrame();

        JFrame ltrFrame = new JFrame("Left To Right");
        ltrFrame.setJMenuBar(createMenuBar(ComponentOrientation.LEFT_TO_RIGHT));
        ltrFrame.setSize(400, 100);

        JFrame rtlFrame = new JFrame("Right To Left");
        rtlFrame.setJMenuBar(createMenuBar(ComponentOrientation.RIGHT_TO_LEFT));
        rtlFrame.setSize(400, 100);

        return (frames = List.of(plafFrame, ltrFrame, rtlFrame));
    }

    private static final class PlafChanger implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            String lnfName = e.getActionCommand();

            try {
                UIManager.setLookAndFeel(lnfName);
                frames.forEach(SwingUtilities::updateComponentTreeUI);
            } catch (Exception exc) {
                String message = "Could not set Look and Feel to " + lnfName;
                System.err.println(message);
                JOptionPane.showMessageDialog(frames.get(0),
                                              message,
                                              "Look and Feel Error",
                                              JOptionPane.ERROR_MESSAGE);
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
