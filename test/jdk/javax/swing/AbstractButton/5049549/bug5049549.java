/*
 * Copyright (c) 2006, 2022, Oracle and/or its affiliates. All rights reserved.
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
   @bug 5049549 7132413
   @summary Tests that the proper icon is used for different states.
   @library ../../regtesthelpers
   @build Blocker
   @run main/manual bug5049549
*/

import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class bug5049549 {

    private static ImageIcon DE = new ImageIcon(bug5049549.class.getResource("DE1.gif"));
    private static ImageIcon DI = new ImageIcon(bug5049549.class.getResource("DI1.gif"));
    private static ImageIcon DS = new ImageIcon(bug5049549.class.getResource("DS1.gif"));
    private static ImageIcon RO = new ImageIcon(bug5049549.class.getResource("RO1.gif"));
    private static ImageIcon RS = new ImageIcon(bug5049549.class.getResource("RS1.gif"));
    private static ImageIcon SE = new ImageIcon(bug5049549.class.getResource("SE1.gif"));
    private static ImageIcon PR = new ImageIcon(bug5049549.class.getResource("PR1.gif"));

    private static Blocker blocker = new Blocker();

    private static class KButton extends JButton {

            KButton(String ex) {
                super(ex);
            }

            private Icon disabledIcon;
            private Icon disabledSelectedIcon;

            public Icon getDisabledIcon() {
                return disabledIcon;
            }

            public Icon getDisabledSelectedIcon() {
                return disabledSelectedIcon;
            }

            public void setDisabledIcon(Icon icon) {
                disabledIcon = icon;
            }

            public void setDisabledSelectedIcon(Icon icon) {
                disabledSelectedIcon = icon;
            }
    }

    public static void main(String[] args) throws Throwable {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                UIManager.put("swing.boldMetal", Boolean.FALSE);
                runTest();
            }
        });

        blocker.blockTillDone();
    }

    private static void runTest() {
        JFrame frame = blocker.createFrameWithPassFailButtons("Wrong icon is used.");

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel label = new JLabel("The following disabled buttons should have white icons");
        panel.add(label);
        label = new JLabel("with red text that matches the button text.");
        panel.add(label);

        KButton button;

        /* disabled: default icon */
        button = new KButton("DE");
        button.setEnabled(false);
        button.setIcon(DE);
        panel.add(button);

        /* disabled-selected: default icon */
        button = new KButton("DE");
        button.setEnabled(false);
        button.setSelected(true);
        button.setIcon(DE);
        panel.add(button);

        /* disabled: default and disabled icons */
        button = new KButton("DI");
        button.setEnabled(false);
        button.setIcon(DE);
        button.setDisabledIcon(DI);
        panel.add(button);

        /* disabled-selected: default and disabled icons */
        button = new KButton("DI");
        button.setEnabled(false);
        button.setSelected(true);
        button.setIcon(DE);
        button.setDisabledIcon(DI);
        panel.add(button);

        /* disabled-selected: default, selected and disabled icons */
        button = new KButton("SE");
        button.setEnabled(false);
        button.setSelected(true);
        button.setIcon(DE);
        button.setSelectedIcon(SE);
        button.setDisabledIcon(DI);
        panel.add(button);

        /* disabled-selected: default, disabled-selected, selected and disabled icons */
        button = new KButton("DS");
        button.setEnabled(false);
        button.setSelected(true);
        button.setIcon(DE);
        button.setSelectedIcon(SE);
        button.setDisabledIcon(DI);
        button.setDisabledSelectedIcon(DS);
        panel.add(button);

        label = new JLabel("The following buttons have a white icon with red text,");
        panel.add(label);
        label = new JLabel("and a triplet of strings separated by commas. The first");
        panel.add(label);
        label = new JLabel("string says what the icon text should show in the normal");
        panel.add(label);
        label = new JLabel("state. The second the rollover state. And the third shows");
        panel.add(label);
        label = new JLabel("what it should show when pressed. Verify each of these");
        panel.add(label);
        label = new JLabel("states for each button.");
        panel.add(label);

        /* normal: default, rollover icons */
        button = new KButton("DE, RO, DE");
        button.setIcon(DE);
        button.setRolloverIcon(RO);
        panel.add(button);

        /* normal: default, rollover, pressed icons */
        button = new KButton("DE, RO, PR");
        button.setIcon(DE);
        button.setRolloverIcon(RO);
        button.setPressedIcon(PR);
        panel.add(button);

        /* selected: default, rollover, pressed icons */
        button = new KButton("DE, RO, PR");
        button.setSelected(true);
        button.setIcon(DE);
        button.setRolloverIcon(RO);
        button.setPressedIcon(PR);
        panel.add(button);

        /* selected: default, rollover, pressed icons */
        button = new KButton("DE, DE, PR");
        button.setSelected(true);
        button.setIcon(DE);
        button.setPressedIcon(PR);
        panel.add(button);

        /* selected: default, selected, rollover, pressed icons */
        button = new KButton("SE, SE, PR");
        button.setSelected(true);
        button.setIcon(DE);
        button.setSelectedIcon(SE);
        button.setRolloverIcon(RO);
        button.setPressedIcon(PR);
        panel.add(button);

        /* selected: default, selected, rollover, rollover-selected, pressed icons */
        button = new KButton("SE, RS, PR");
        button.setSelected(true);
        button.setIcon(DE);
        button.setRolloverSelectedIcon(RS);
        button.setSelectedIcon(SE);
        button.setRolloverIcon(RO);
        button.setPressedIcon(PR);
        panel.add(button);

        /* selected: default, selected, rollover, rollover-selected icons */
        button = new KButton("SE, RS, SE");
        button.setSelected(true);
        button.setIcon(DE);
        button.setRolloverSelectedIcon(RS);
        button.setSelectedIcon(SE);
        button.setRolloverIcon(RO);
        panel.add(button);

        /* selected: default, selected icons */
        button = new KButton("SE, SE, SE");
        button.setSelected(true);
        button.setIcon(DE);
        button.setSelectedIcon(SE);
        button.setRolloverIcon(RO);
        panel.add(button);

        frame.add(panel);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
