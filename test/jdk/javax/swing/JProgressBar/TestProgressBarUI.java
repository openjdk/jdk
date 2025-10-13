/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8318577
 * @summary Tests JProgressBarUI renders correctly in Windows L&F
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestProgressBarUI
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TestProgressBarUI {

    private static final String instructionsText = """
            Two progressbar "Good" and "Bad"
            will be shown with different preferred size,
            If the "Bad" progressbar is rendered at the same
            height as "Good" progressbar,
            without any difference in padding internally
            the test passes, otherwise fails. """;

    public static void main(String[] args) throws Exception {
        System.setProperty("sun.java2d.uiScale", "2.0");
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        PassFailJFrame.builder()
                .title("ProgressBar Instructions")
                .instructions(instructionsText)
                .rows(9)
                .columns(36)
                .testUI(TestProgressBarUI::doTest)
                .build()
                .awaitAndCheck();
    }

    public static JFrame doTest() {
        JFrame frame = new JFrame("JProgressBar");

        JPanel panel = new JPanel(new FlowLayout(20, 20, FlowLayout.LEADING));
        panel.setBackground(Color.white);

        JProgressBar p1 = new JProgressBar(0, 100);
        p1.setValue(50);
        p1.setStringPainted(true);
        p1.setString("GOOD");
        p1.setPreferredSize(new Dimension(100, 21));
        panel.add(p1);

        JProgressBar p2 = new JProgressBar(0, 100);
        p2.setValue(50);
        p2.setStringPainted(true);
        p2.setString("BAD");

        p2.setPreferredSize(new Dimension(100, 22));
        panel.add(p2);

        JComponent c = (JComponent) frame.getContentPane();
        c.add(panel, BorderLayout.CENTER);

        frame.pack();
        frame.setLocationByPlatform(true);
        return frame;
    }
}
