/*
 * Copyright (c) 2006, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4148057 6178004
 * @summary REGRESSION: setToolTipText does not work if the
 *          component is not focused
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6178004
 */

import java.awt.Point;
import java.awt.Window;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;
import javax.swing.UIManager;

public class bug6178004 {
    private static JFrame frame1;
    private static JFrame frame2;
    private static final int SIZE = 300;

    private static final String INSTRUCTIONS = """
            You can change Look And Feel using the menu "Change LaF".

            Make sure that Frame2 or instruction window is active.
            Move mouse over the button inside "Frame 1".
            If tooltip is NOT shown or Frame 1 jumped on top of
            the Frame2, press FAIL.

            For Metal/Windows LaF:
            Tooltips are shown only if one of the frames (or the instruction
            window) is active. To test it click on any other application to
            make frames and instruction window inactive and then verify that
            tooltips are not shown any more.

            For Motif/GTK/Nimbus/Aqua LaF:
            Tooltips should be shown for all frames irrespective of whether
            the application is active or inactive.

            Note: Tooltip for Frame1 is always shown at the top-left corner.
            Tooltips could be shown partly covered by another frame.

            If above is true press PASS else FAIL.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("bug6178004 Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(10)
                .columns(40)
                .testUI(createAndShowUI())
                .positionTestUI(bug6178004::positionTestWindows)
                .build()
                .awaitAndCheck();
    }

    private static List<Window> createAndShowUI() {
        ToolTipManager.sharedInstance().setInitialDelay(0);

        frame1 = new JFrame("bug6178004 Frame1");
        frame1.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JButton button = new JButton("Test") {
            public Point getToolTipLocation(MouseEvent event) {
                return new Point(10, 10);
            }
        };
        button.setToolTipText("Tooltip-1");
        frame1.add(button);
        frame1.setSize(SIZE, SIZE);

        frame2 = new JFrame("bug6178004 Frame2");
        frame2.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JButton button2 = new JButton("Click me") ;
        button2.setToolTipText("Tooltip-2");
        frame2.add(button2);
        frame2.setSize(SIZE, SIZE);

        JMenuBar bar = new JMenuBar();
        JMenu lafMenu = new JMenu("Change LaF");
        ButtonGroup lafGroup = new ButtonGroup();

        LookAndFeel currentLaf = UIManager.getLookAndFeel();
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        for (final UIManager.LookAndFeelInfo lafInfo : lafs) {
            JCheckBoxMenuItem lafItem = new JCheckBoxMenuItem(lafInfo.getName());
            lafItem.addActionListener(e -> setLaF(lafInfo.getClassName()));
            if (lafInfo.getClassName().equals(currentLaf.getClass().getName())) {
                lafItem.setSelected(true);
            }

            lafGroup.add(lafItem);
            lafMenu.add(lafItem);
        }

        bar.add(lafMenu);
        frame2.setJMenuBar(bar);
        return List.of(frame1, frame2);
    }

    private static void setLaF(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            SwingUtilities.updateComponentTreeUI(frame1);
            SwingUtilities.updateComponentTreeUI(frame2);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // custom window layout required for this test
    private static void positionTestWindows(List<? extends Window> testWindows,
                                            PassFailJFrame.InstructionUI instructionUI) {
        int gap = 5;
        int x = instructionUI.getLocation().x + instructionUI.getSize().width + gap;
        // the two test frames need to overlap for this test
        testWindows.get(0).setLocation(x, instructionUI.getLocation().y);
        testWindows.get(1).setLocation((x + SIZE / 2), instructionUI.getLocation().y);
    }
}
