/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Color;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/*
 * @test
 * @bug 4124096
 * @summary Modal JDialog is not modal on Solaris
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual WindowInputBlock
 */

public class WindowInputBlock {
    private static final String INSTRUCTIONS = """
            When the Window is up, you see a "Show Modal Dialog" button, a
            "Test" button and a TextField.
            Verify that the "Test" button is clickable, and the TextField can
            receive focus.

            Now, click on "Show Modal Dialog" button to bring up a modal dialog
            and verify that both "Test" button and TextField are not accessible.
            Close the new dialog window. If the test behaved as described, pass
            this test. Otherwise, fail this test.

            """;

    public static void main(String[] argv) throws Exception {
        JFrame frame = new ModalDialogTest();
        PassFailJFrame.builder()
                .title("WindowInputBlock")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .testUI(frame)
                .build()
                .awaitAndCheck();
    }
}

class ModalDialogTest extends JFrame implements ActionListener {
    JDialog dialog = new JDialog(new JFrame(), "Modal Dialog", true);

    public ModalDialogTest() {
        setTitle("Modal Dialog Test");
        JPanel controlPanel = new JPanel();
        JPanel infoPanel = new JPanel();
        JButton showButton = new JButton("Show Modal Dialog");
        JButton testButton = new JButton("Test");
        JTextField textField = new JTextField("Test");

        getContentPane().setLayout(new BorderLayout());
        infoPanel.setLayout(new GridLayout(0, 1));

        showButton.setOpaque(true);
        showButton.setBackground(Color.yellow);

        testButton.setOpaque(true);
        testButton.setBackground(Color.pink);

        controlPanel.add(showButton);
        controlPanel.add(testButton);
        controlPanel.add(textField);

        infoPanel.add(new JLabel("Click the \"Show Modal Dialog\" button " +
                "to display a modal JDialog."));
        infoPanel.add(new JLabel("Click the \"Test\" button to verify " +
                "dialog modality."));

        getContentPane().add(BorderLayout.NORTH, controlPanel);
        getContentPane().add(BorderLayout.SOUTH, infoPanel);
        dialog.setSize(200, 200);

        showButton.addActionListener(this);
        testButton.addActionListener(this);

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }

            public void windowClosed(WindowEvent e) {
                System.exit(0);
            }
        });

        pack();
        setSize(450, 120);
    }

    public void actionPerformed(ActionEvent evt) {
        String command = evt.getActionCommand();

        if (command == "Show Modal Dialog") {
            System.out.println("*** Invoking JDialog.show() ***");
            dialog.setLocation(200, 200);
            dialog.setVisible(true);
        } else if (command == "Test") {
            System.out.println("*** Test ***");
        }
    }
}
