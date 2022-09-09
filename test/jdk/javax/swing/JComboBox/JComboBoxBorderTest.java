/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8054572
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @summary Tests if JComboBox displays correctly when editable/non-editable
 * @run main/manual JComboBoxBorderTest
 */

import java.awt.FlowLayout;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class JComboBoxBorderTest {
    private static final String instructionsText = "Pass if you can see both " +
            "an editable and non-editable JComboBox and if they display " +
            "reasonably. Fail if they do not appear or are misaligned.";

    private static JFrame frame;

    public static void createAndShowGUI() throws InterruptedException,
            InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {

            JLabel label = new JLabel("Editable combo box:");
            JLabel label2 = new JLabel("Non-editable combo box:");

            JComboBox<String> comboBox = new JComboBox<>(new String[]
                    { "Item 1", "Item 2", "Item 3" });
            JComboBox<String> comboBox2 = new JComboBox<>(new String[]
                    { "Item 1", "Item 2", "Item 3" });
            comboBox.setEditable(true);

            FlowLayout layout = new FlowLayout(FlowLayout.LEADING);
            JPanel panel = new JPanel(layout);
            panel.add(label);
            panel.add(comboBox);

            panel.add(label2);
            panel.add(comboBox2);

            frame = new JFrame();
            frame.getContentPane().add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);

            PassFailJFrame.addTestWindow(frame);
            PassFailJFrame.positionTestWindow(frame,
                    PassFailJFrame.Position.HORIZONTAL);

            frame.setVisible(true);
        });
    }

    public static void main(String[] args) throws Exception {

        UIManager.setLookAndFeel("com.apple.laf.AquaLookAndFeel");

        PassFailJFrame pfjFrame = new PassFailJFrame("JScrollPane "
                + "Test Instructions", instructionsText, 5);

        createAndShowGUI();

        pfjFrame.awaitAndCheck();
    }
}