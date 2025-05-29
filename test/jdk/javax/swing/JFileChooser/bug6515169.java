/*
 * Copyright (c) 2011, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6515169
 * @requires (os.family == "windows")
 * @summary wrong grid header in JFileChooser
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug6515169
 */

import javax.swing.ButtonGroup;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug6515169 {
    private static JFrame frame;
    private static final String INSTRUCTIONS = """
            This test is to verify JFileChooser on Windows and Metal LAF.
            Use the "Change LaF" menu to switch between the 2 LaF
            and verify the following.

            a. Change view mode to "Details"
            b. Check that 4 columns appear: Name, Size, Type and Date Modified
            c. Change current directory by pressing any available subdirectory
               or by pressing button "Up One Level".
            d. Check that still four columns exist.

            Change LaF and repeat the steps a-d.
            If all conditions are true press PASS, else FAIL.
            """;

    public static void main(String[] argv) throws Exception {
        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(40)
                .testUI(bug6515169::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JFrame createTestUI() {
        frame = new JFrame("bug6515169");
        JMenuBar bar = new JMenuBar();
        JMenu lafMenu = new JMenu("Change LaF");
        ButtonGroup lafGroup = new ButtonGroup();
        JCheckBoxMenuItem lafItem1 = new JCheckBoxMenuItem("Window LaF");
        lafItem1.addActionListener(e ->
                setLaF(UIManager.getSystemLookAndFeelClassName()));
        lafGroup.add(lafItem1);
        lafMenu.add(lafItem1);

        JCheckBoxMenuItem lafItem2 = new JCheckBoxMenuItem("Metal LaF");
        lafItem2.addActionListener(e ->
                setLaF(UIManager.getCrossPlatformLookAndFeelClassName()));
        lafGroup.add(lafItem2);
        lafMenu.add(lafItem2);

        bar.add(lafMenu);
        frame.setJMenuBar(bar);

        String dir = ".";
        JFileChooser fc = new JFileChooser(dir);
        fc.setControlButtonsAreShown(false);
        frame.add(fc);
        frame.pack();

        return frame;
    }

    private static void setLaF(String laf) {
        try {
            UIManager.setLookAndFeel(laf);
            SwingUtilities.updateComponentTreeUI(frame);
        } catch (Exception e) {
           throw new RuntimeException("Test Failed!", e);
        }
    }
}
