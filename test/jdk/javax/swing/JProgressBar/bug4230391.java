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
 * @bug 4230391
 * @summary Tests that JProgressBar draws correctly when Insets are not zero
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4230391
*/

import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

public class bug4230391 {

    static final String INSTRUCTIONS = """
        Tests that progress bars honor insets in different L&Fs.
        Different L&Fs render the progress bar differently, and may or may
        not have a different colored background around the progress bar,
        and may or may not draw a border around the bar+background.
        The progress bars should be of equal width and the progress
        rendering line/bar should not extend past/overlap any border.
        If it is as described, the test PASSES.
    """;

    static class InsetProgressBar extends JProgressBar {
        private Insets insets = new Insets(12, 12, 12, 12);

        public InsetProgressBar(boolean horiz, int low, int hi) {
            super((horiz)?JProgressBar.HORIZONTAL:JProgressBar.VERTICAL, low, hi);
        }

        public Insets getInsets() {
            return insets;
        }
    }

    static JPanel createBarSet() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));
        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        for (int i = 0; i < lafs.length; i++) {
           if (i > 0) {
               panel.add(Box.createVerticalStrut(10));
            }
            panel.add(createProgressBars(lafs[i].getName(), lafs[i].getClassName()));
        }
        return panel;
    }

    static JPanel createProgressBars(String name, String plaf) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));
        try {
            LookAndFeel save = UIManager.getLookAndFeel();
            UIManager.setLookAndFeel(plaf);

            ComponentOrientation ltr = ComponentOrientation.LEFT_TO_RIGHT;

            Box b = Box.createVerticalBox();
            panel.add(b);
            panel.add(Box.createHorizontalStrut(5));
            panel.add(createProgressBar(false, true, ltr));
            UIManager.setLookAndFeel(save);
        } catch (Exception e) {
            System.err.println(e);
        }
        return panel;
    }

    static JProgressBar createProgressBar(boolean solid, boolean horiz,
                                          ComponentOrientation o) {
        if (solid) {
            UIManager.put("ProgressBar.cellSpacing", Integer.valueOf(0));
            UIManager.put("ProgressBar.cellLength", Integer.valueOf(1));
        } else {
            UIManager.put("ProgressBar.cellSpacing", Integer.valueOf(2));
            UIManager.put("ProgressBar.cellLength", Integer.valueOf(7));
        }

        JProgressBar p = new InsetProgressBar(horiz, 0, 20);
        p.setStringPainted(solid);
        p.setValue(20);
        p.setComponentOrientation(o);

        return p;
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Progress Bar Insets Test");
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.X_AXIS));
        contentPane.add(createBarSet());
        frame.setSize(400, 300);
        return frame;
    }

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("Progress Bar Insets Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(bug4230391::createUI)
            .build()
            .awaitAndCheck();
    }
}
