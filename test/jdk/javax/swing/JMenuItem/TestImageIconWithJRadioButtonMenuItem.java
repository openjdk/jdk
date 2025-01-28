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
 * @bug 8348760
 * @summary Verify if RadioButton is shown if JRadioButtonMenuItem
 *          is rendered with ImageIcon in WindowsLookAndFeel
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestImageIconWithJRadioButtonMenuItem
 */

import javax.swing.AbstractButton;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.UIManager;

public class TestImageIconWithJRadioButtonMenuItem {

    private static final String instructionsText = """
        Two JRadioButtonMenuItem will be shown.
        One JRadioButtonMenuItem is with imageIcon and
        another one without imageicon.
        Verify that for JRadioButtonMenuItem with imageicon,
        radiobutton is been shown alongside the imageicon.
        If radiobutton is shown, test passes else fails.""";

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        PassFailJFrame.builder()
                .title("JRadioButtonMenuItem Instructions")
                .instructions(instructionsText)
                .rows(10)
                .columns(40)
                .testUI(TestImageIconWithJRadioButtonMenuItem::doTest)
                .build()
                .awaitAndCheck();
    }

    public static JFrame doTest() {
        String imgPath1 = "./duke.gif";

        JFrame frame = new JFrame("RadioButtonWithImageIcon");
        ImageIcon imageIcon1 = new ImageIcon(imgPath1);
        AbstractButton button1 = new JRadioButtonMenuItem("JRadioButtonMenuItem 1",
                imageIcon1);
        button1.setSelected(true);
        AbstractButton button2 = new JRadioButtonMenuItem("JRadioButtonMenuItem 2");

        ButtonGroup buttonGroup = new ButtonGroup();
        buttonGroup.add(button1);
        buttonGroup.add(button2);

        JPanel panel = new JPanel();
        panel.add(button1);
        panel.add(button2);

        frame.getContentPane().add(panel);
        frame.pack();
        return frame;
    }
}
