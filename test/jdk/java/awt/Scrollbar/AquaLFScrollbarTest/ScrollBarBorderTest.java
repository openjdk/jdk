/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.JScrollBar;
import javax.swing.JPanel;
import javax.swing.JFrame;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.Box;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import java.awt.Frame;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Panel;
import java.awt.TextArea;
import java.awt.Button;
import java.awt.Scrollbar;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/*
 * @test
 * @bug 8190264
 * @summary JScrollBar ignores its border when using macOS Mac OS X Aqua look and feel
 * @run main/manual ScrollBarBorderTest
 */
public class ScrollBarBorderTest implements ActionListener {

    // On macOS 10.12.6 using the Mac look and feel (com.apple.laf.AquaLookAndFeel)
    // the scroll bar ignores the custom border and allows the scroll thumb to move
    // beneath the border. Run with:
    // java ScrollBarBorderTest

    // If run using any other look and feel (e.g. Metal) then the right side of
    // the scroll bar stops at the border as expected. Run with:
    // java -Dswing.defaultlaf=javax.swing.plaf.metal.MetalLookAndFeel ScrollBarBorderTest

    // Java version: 1.8.0_151

    private static JScrollBar scrollBar;
    private static JPanel panel;
    private static JFrame frame;
    private static Frame instructionFrame;
    private static GridBagLayout layout;
    private static Panel mainControlPanel;
    private static Panel resultButtonPanel;
    private static TextArea instructionTextArea;
    private static Button passButton;
    private static Button failButton;
    private static Thread mainThread = null;
    private static boolean testPassed = false;
    private static boolean isInterrupted = false;
    private static final int testTimeOut = 300000;
    private static String testFailMessage = "Test Failed. Thumb was able to move into border.";


    public void createAndShowGUI() {
        // create scroll bar
        scrollBar = new JScrollBar(Scrollbar.HORIZONTAL);
        scrollBar.setBorder(new CustomBorder());

        // create panel
        panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel(UIManager.getLookAndFeel().toString()));
        panel.add(Box.createVerticalStrut(20));
        panel.add(scrollBar);

        // create frame
        frame = new JFrame("ScrollBarBorderTest");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.getContentPane().add(panel);
        frame.pack();
        frame.setVisible(true);
    }

    private void createInstructionUI() {
        instructionFrame = new Frame("Scrollbar");
        layout = new GridBagLayout();
        mainControlPanel = new Panel(layout);
        resultButtonPanel = new Panel(layout);

        GridBagConstraints gbc = new GridBagConstraints();
        String instructions
                = "\nINSTRUCTIONS:\n"
                + "\n   Try to drag the thumb of the scrollbar into the red zone."
                + "\n   If the thumb is able to go into the red zone, click fail."
                + "\n   Otherwise, pass.";

        instructionTextArea = new TextArea(7, 70);
        instructionTextArea.setText(instructions);
        instructionTextArea.setEnabled(false);
        instructionTextArea.setBackground(Color.white);

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        mainControlPanel.add(instructionTextArea, gbc);

        passButton = new Button("Pass");
        passButton.setName("Pass");
        passButton.addActionListener((ActionListener) this);

        failButton = new Button("Fail");
        failButton.setName("Fail");
        failButton.addActionListener((ActionListener) this);

        setButtonEnable(true);

        gbc.gridx = 0;
        gbc.gridy = 0;
        resultButtonPanel.add(passButton, gbc);
        gbc.gridx = 1;
        gbc.gridy = 0;
        resultButtonPanel.add(failButton, gbc);
        gbc.gridx = 0;
        gbc.gridy = 1;
        mainControlPanel.add(resultButtonPanel, gbc);

        instructionFrame.add(mainControlPanel);
        instructionFrame.pack();
        instructionFrame.setVisible(true);
    }

    @Override
    public void actionPerformed(ActionEvent ae) {
        if (ae.getSource() instanceof Button) {
            Button btn = (Button) ae.getSource();
            switch (btn.getName()) {
                case "Pass":
                    testPassed = true;
                    isInterrupted = true;
                    mainThread.interrupt();
                    break;
                case "Fail":
                    testPassed = false;
                    isInterrupted = true;
                    mainThread.interrupt();
                    break;
            }
        }
    }

    private static void setButtonEnable(boolean status) {
        passButton.setEnabled(status);
        failButton.setEnabled(status);
    }

    private static void cleanUp() {
        frame.dispose();
        instructionFrame.dispose();
    }

    public static void main(String[] args) {
        ScrollBarBorderTest borderTest = new ScrollBarBorderTest();
        borderTest.createInstructionUI();
        borderTest.createAndShowGUI();

        mainThread = Thread.currentThread();
        try {
            mainThread.sleep(testTimeOut);
        } catch (InterruptedException ex) {
            if (!testPassed) {
                throw new RuntimeException(testFailMessage);
            }
        } finally {
            cleanUp();
        }

        if (!isInterrupted) {
            throw new RuntimeException("Test Timed out after "
                    + testTimeOut / 1000 + " seconds");
        }
    }


    // custom border
    private static class CustomBorder implements Border {
        public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            g.setColor(new Color(255, 0, 0, 100));
            g.fillRect(width - 150, y, width, height);
        }

        public Insets getBorderInsets(Component c) {
            return new Insets(0, 0, 0, 150);
        }

        public boolean isBorderOpaque() {
            return true;
        }
    }

}