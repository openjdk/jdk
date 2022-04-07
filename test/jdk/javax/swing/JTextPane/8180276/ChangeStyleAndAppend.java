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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Dialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Panel;
import java.awt.TextArea;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.border.EtchedBorder;
import javax.swing.JFrame;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/*
 * @test
 * @bug 8180276
 * @summary Verifies if getText method returns extra carriage return when
 *          mixed with methods of document.
 * @key headful
 * @run main/manual ChangeStyleAndAppend
 */

public class ChangeStyleAndAppend extends JFrame {
    static Dialog controlDialog;
    private AtomicReference<Boolean> testResult = new AtomicReference<>(false);
    static final CountDownLatch latch = new CountDownLatch(1);
    class MyJTextPane extends JTextPane {
        public void appendLine(String s) {
            try {
                Document doc = this.getDocument();
                doc.insertString(doc.getLength(), s + System.lineSeparator(), null);
            } catch(BadLocationException e) {
                System.err.println(e);
            }
        }
    }

    void disposeUI()
    {
        controlDialog.dispose();
        this.dispose();
    }
    public ChangeStyleAndAppend() {
        initializeUI();
        instructionDialog();
    }

    private void instructionDialog() {

        controlDialog = new Dialog((JFrame)null, "TextPaneTest");
        String instructions =
                "Verify if Extra space is present between each line in " +
                        "\nfirst Text Pane." +
                        "\nIf Extra Space is present then press \"Fail\"," +
                        "\notherwise press \"Pass\".";
        TextArea messageArea = new TextArea(instructions, 6, 80, TextArea.SCROLLBARS_NONE);
        controlDialog.add("North", messageArea);

        Button passedButton = new Button("Pass");
        passedButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testResult.set(true);
                latch.countDown();
            }
        });

        Button failedButton = new Button("Fail");
        failedButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                testResult.set(false);
                latch.countDown();
            }
        });

        Panel buttonPanel = new Panel();
        buttonPanel.add("West",passedButton);
        buttonPanel.add("East", failedButton);
        controlDialog.add("South", buttonPanel);

        controlDialog.setBounds(300, 0, 400, 200);
        controlDialog.setVisible(true);

    }

    private void initializeUI() {

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        MyJTextPane pane0 = new MyJTextPane();
        pane0.appendLine("MyJTextPane using append() and then calling setText()");
        pane0.appendLine("Second line. ");
        pane0.appendLine("Third line");
        pane0.setText(pane0.getText() + "At last" + System.lineSeparator());
        pane0.setBorder(new EtchedBorder(EtchedBorder.RAISED));

        add(pane0, BorderLayout.NORTH);

        MyJTextPane pane = new MyJTextPane();
        pane.appendLine("MyJTextPane calling appendLine()");
        pane.appendLine("Second line. ");
        pane.appendLine("Third line");
        pane.appendLine("At last");
        pane.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        add(pane, BorderLayout.CENTER);

        JTextPane pane2 = new JTextPane();
        pane2.setText("Normal JTextPane calling setText()");
        pane2.setText(pane2.getText() + System.lineSeparator() + "Second line. ");
        pane2.setText(pane2.getText() + System.lineSeparator() + "Third line");
        pane2.setText(pane2.getText() + System.lineSeparator() + "At last");
        pane2.setBorder(new EtchedBorder(EtchedBorder.RAISED));
        add(pane2, BorderLayout.SOUTH);

        pack();
        setVisible(true);
    }

    public static void main(String[] args) throws InterruptedException {
        final ChangeStyleAndAppend[] frame = new ChangeStyleAndAppend[1];
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                frame[0] = new ChangeStyleAndAppend();
            }
        });
        boolean status = latch.await(5, TimeUnit.MINUTES);
        if (!status) {
            System.out.println("Test timed out.");
        }

        frame[0].onCompletion(frame[0].testResult);
    }
    private void onCompletion(AtomicReference<Boolean> res)
    {
        disposeUI();
        if (res.toString() == "false")
        {
            throw new RuntimeException("Test Failed");
        }
    }
}


