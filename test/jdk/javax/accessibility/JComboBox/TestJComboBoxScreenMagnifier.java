/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.lang.reflect.InvocationTargetException;

import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/*
 * @test
 * @bug 8283214
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @requires (os.family == "mac")
 * @summary Verifies if item selected in JComboBox magnifies using
 *          screen magnifier a11y tool
 * @run main/manual TestJComboBoxScreenMagnifier
 */

public class TestJComboBoxScreenMagnifier {
    private static JFrame frame;
    private static final String INSTRUCTIONS =
            "1) Enable Screen magnifier on the Mac\n\n" +
                "System Preference -> Accessibility -> Zoom -> " +
                "Select \"Enable Hover Text\"\n\n" +
            "2) Move the mouse over the combo box and press " +
                "\"Command\" button.\n\n" +
            "3) If magnified label is visible, press Pass else Fail.";

    public static void main(String[] args) throws InterruptedException,
             InvocationTargetException {
        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
                .title("JComboBox Screen Magnifier Test Instructions")
                .instructions(INSTRUCTIONS)
                .testTimeOut(5)
                .rows(12)
                .columns(40)
                .screenCapture()
                .build();

        SwingUtilities.invokeAndWait(TestJComboBoxScreenMagnifier::createAndShowUI);
        passFailJFrame.awaitAndCheck();
    }

    private static void createAndShowUI() {
        frame = new JFrame("JComboBox A11Y Screen Magnifier Test");

        String[] fruits = new String[] {"Apple", "Orange",
                "Mango", "Pineapple", "Banana"};
        JComboBox<String> comboBox = new JComboBox<String>(fruits);
        JPanel fruitPanel = new JPanel(new GridLayout(1, 2));
        JLabel fruitLabel = new JLabel("Fruits:", JLabel.CENTER);
        fruitLabel.getAccessibleContext().setAccessibleName("Fruits Label");
        fruitPanel.add(fruitLabel);
        fruitPanel.add(comboBox);
        comboBox.getAccessibleContext().setAccessibleName("Fruit Combo box");
        frame.getContentPane().add(fruitPanel, BorderLayout.CENTER);

        PassFailJFrame.addTestWindow(frame);
        PassFailJFrame.positionTestWindow(frame,
                PassFailJFrame.Position.HORIZONTAL);
        frame.pack();
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);
    }
}
