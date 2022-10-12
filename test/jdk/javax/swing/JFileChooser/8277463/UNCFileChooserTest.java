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

/* @test
   @bug 8277463
   @requires (os.family == "windows")
   @summary JFileChooser with Metal L&F doesn't show non-canonical UNC path in - Look in
   @run main/manual UNCFileChooserTest
*/

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class UNCFileChooserTest {
    private static volatile CountDownLatch countDownLatch;
    private static JFrame instructionFrame;
    private static JFrame testFrame;
    private static volatile boolean testPassed = false;

    private static boolean validatePlatform() {
        String osName = System.getProperty("os.name");
        if (osName == null) {
            throw new RuntimeException("Name of the current OS could not be" +
                    " retrieved.");
        }
        return osName.startsWith("Windows");
    }

    private static void createInstructionUI() throws Exception {
        UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
        String instructions =
                "1. Enter the non-canonical UNC path of the directory to test\n"
                + "example: \\\\pc-name\\dir\\..\n"
                + "2. An \"Open File\" file chooser dialog pops up\n"
                + "3. Check the \"Look in\" Combobox at the top for quickly changing directory is not empty\n"
                + "4. Close the file chooser Dialog\n"
                + "5. If the \"Look in\" Combobox is not empty then press PASS else press FAIL\n";
        instructionFrame = new JFrame("InstructionFrame");
        JTextArea textArea = new JTextArea(instructions);
        textArea.setEditable(false);
        final JButton passButton = new JButton("PASS");
        passButton.addActionListener((e) -> {
            testPassed = true;
            instructionFrame.dispose();
            countDownLatch.countDown();
        });
        final JButton failButton = new JButton("FAIL");
        failButton.addActionListener((e) -> {
            instructionFrame.dispose();
            countDownLatch.countDown();
        });

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.add(textArea, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(passButton);
        buttonPanel.add(failButton);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);
        instructionFrame.setDefaultCloseOperation(
                WindowConstants.DISPOSE_ON_CLOSE);
        instructionFrame.setBounds(0,0,500,500);
        instructionFrame.add(mainPanel);
        instructionFrame.pack();
        instructionFrame.setVisible(true);
    }

    private static void showOpenDialog() throws Exception {
        String path = JOptionPane.showInputDialog(testFrame, "Enter the non-canonical UNC path of the directory to test.\nexample: \\\\pc-name\\dir\\..");
        if (path == null) {
            throw new RuntimeException("Enter the directory path to test.");
        }
        new JFileChooser(path).showOpenDialog(null);
    }

    public static void main(String[] args) throws Exception {
        if (!validatePlatform()) {
            System.out.println("This test is only for MS Windows OS.");
            return;
        }
        countDownLatch = new CountDownLatch(1);
        UNCFileChooserTest uncFileChooserTest =
                new UNCFileChooserTest();

        uncFileChooserTest.createInstructionUI();
        uncFileChooserTest.showOpenDialog();
        countDownLatch.await(15, TimeUnit.MINUTES);

        if(!testPassed) {
            throw new RuntimeException("Test failed!");
        }
    }
}
