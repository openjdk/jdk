/*
 * Copyright (c) 1999, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6439354
 * @summary Verify TitleBorder appearance Color/Visibility for WLAF
 * @requires (os.family == "windows")
 * @run main/manual TitledBorderTest
 */
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class TitledBorderTest implements ActionListener {

    private static GridBagLayout layout;
    private static JPanel mainControlPanel;
    private static JPanel resultButtonPanel;
    private static JTextArea instructionTextArea;
    private static JButton passButton;
    private static JButton failButton;
    private static JFrame mainFrame;

    public static void main(String[] args) throws Exception {
        TitledBorderTest titledBorderTest = new TitledBorderTest();
    }

    public TitledBorderTest() throws Exception {
        createUI();
    }

    public final void createUI() throws Exception {

        UIManager.setLookAndFeel("com.sun.java.swing.plaf."
                + "windows.WindowsLookAndFeel");

        SwingUtilities.invokeAndWait(() -> {

            mainFrame = new JFrame("Window LAF TitledBorder Test");
            layout = new GridBagLayout();
            mainControlPanel = new JPanel(layout);
            resultButtonPanel = new JPanel(layout);

            GridBagConstraints gbc = new GridBagConstraints();
            String instructions
                    = "INSTRUCTIONS:"
                    + "\n set Windows Theme to HighContrast#1."
                    + "\n (ControlPanel->Personalization->High Contrast#1)"
                    + "\n If Titled Border(Border Line) is visible then test"
                    + " passes else failed.";

            instructionTextArea = new JTextArea();
            instructionTextArea.setText(instructions);
            instructionTextArea.setEnabled(false);
            instructionTextArea.setDisabledTextColor(Color.black);
            instructionTextArea.setBackground(Color.white);

            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            mainControlPanel.add(instructionTextArea, gbc);

            mainControlPanel.setBorder(BorderFactory.
                    createTitledBorder("Titled Border"));

            passButton = new JButton("Pass");
            passButton.setActionCommand("Pass");
            passButton.addActionListener(TitledBorderTest.this);
            failButton = new JButton("Fail");
            failButton.setActionCommand("Fail");
            failButton.addActionListener(TitledBorderTest.this);
            gbc.gridx = 0;
            gbc.gridy = 0;
            resultButtonPanel.add(passButton, gbc);
            gbc.gridx = 1;
            gbc.gridy = 0;
            resultButtonPanel.add(failButton, gbc);

            gbc.gridx = 0;
            gbc.gridy = 1;
            mainControlPanel.add(resultButtonPanel, gbc);

            mainFrame.add(mainControlPanel);
            mainFrame.pack();
            mainFrame.setVisible(true);
        });
    }

    @Override
    public void actionPerformed(ActionEvent evt) {
        if (evt.getSource() instanceof JButton) {
            JButton btn = (JButton) evt.getSource();
            cleanUp();
            switch (btn.getActionCommand()) {
                case "Pass":
                    break;
                case "Fail":
                    throw new AssertionError("User Clicked Fail!");
            }
        }
    }

    private static void cleanUp() {
        mainFrame.dispose();
    }

}
