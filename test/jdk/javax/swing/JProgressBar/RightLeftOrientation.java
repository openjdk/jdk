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
 * @bug 4230355
 * @summary
 *     This test checks if progress bars lay out correctly when their
 *     ComponentOrientation property is set to RIGHT_TO_LEFT. This test is
 *     manual. The tester is asked to compare left-to-right and
 *     right-to-left progress bars and judge whether they are mirror images
 *     of each other.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RightLeftOrientation
 */

import java.awt.ComponentOrientation;
import java.awt.Container;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.LookAndFeel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.UIManager;

public class RightLeftOrientation {

    static final String INSTRUCTIONS = """
        This test checks progress bars for correct Right-To-Left Component Orientation.
        The progress bars in the left column should fill up from the left while the bars in
        the right column should fill up from the right.
        If this is so, the test PASSES, otherwise it FAILS.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
            .title("Progress Bar Orientation Test Instructions")
            .instructions(INSTRUCTIONS)
            .columns(60)
            .testUI(RightLeftOrientation::createUI)
            .build()
            .awaitAndCheck();
    }

    static JFrame createUI() {
        JFrame frame = new JFrame("Progress Bar Orientation Test");
        Container contentPane = frame.getContentPane();
        contentPane.setLayout(new BoxLayout(contentPane, BoxLayout.X_AXIS));
        contentPane.add(createBarSet(ComponentOrientation.LEFT_TO_RIGHT));
        contentPane.add(createBarSet(ComponentOrientation.RIGHT_TO_LEFT));
        frame.pack();
        return frame;
    }

    static JPanel createBarSet(ComponentOrientation o) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel header;
        if (o.isLeftToRight())
            header = new JLabel("Left To Right");
        else
            header = new JLabel("Right To Left");
        panel.add(header);

        UIManager.LookAndFeelInfo[] lafs = UIManager.getInstalledLookAndFeels();
        for (int i = 0; i < lafs.length; i++) {
            if (i > 0)
                panel.add(Box.createVerticalStrut(10));
            panel.add(createProgressBars(lafs[i].getName(),
                                          lafs[i].getClassName(), o));
        }

        return panel;
    }

    static JPanel createProgressBars(String name, String plaf,
                                     ComponentOrientation o) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel label = new JLabel(name);
        panel.add(label);
        try {
            LookAndFeel save = UIManager.getLookAndFeel();
            UIManager.setLookAndFeel(plaf);

            panel.add(createProgressBar(true, 0, o));
            panel.add(Box.createVerticalStrut(5));

            panel.add(createProgressBar(true, 5, o));
            panel.add(Box.createVerticalStrut(5));

            panel.add(createProgressBar(true, 10, o));
            panel.add(Box.createVerticalStrut(5));

            panel.add(createProgressBar(true, 20, o));
            panel.add(Box.createVerticalStrut(5));

            UIManager.put("ProgressBar.cellSpacing", Integer.valueOf(2));
            UIManager.put("ProgressBar.cellLength", Integer.valueOf(7));

            panel.add(createProgressBar(false, 5, o));
            panel.add(Box.createVerticalStrut(5));

            panel.add(createProgressBar(false, 20, o));

            UIManager.setLookAndFeel(save);
        } catch (Exception e) {
            System.err.println(e);
        }
        return panel;
    }

    static JProgressBar createProgressBar(boolean paintStr, int value,
                                          ComponentOrientation o) {
        JProgressBar p = new JProgressBar(JProgressBar.HORIZONTAL, 0, 20);
        p.setStringPainted(paintStr);
        p.setValue(value);
        p.setComponentOrientation(o);
        return p;
    }

}
