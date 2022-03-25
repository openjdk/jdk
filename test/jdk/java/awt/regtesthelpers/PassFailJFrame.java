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
import java.awt.Frame;
import java.awt.HeadlessException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

public class PassFailJFrame extends JFrame {
    private final CountDownLatch latch = new CountDownLatch(1);
    private boolean failed = false;
    private String testFailedReason;
    private JTextArea instructionsText;
    private int maxStringLength = 50;
    private int timeoutMinutes = 3;
    private Runnable tearDownRunnable;

    public PassFailJFrame(String instructions, String title) throws HeadlessException {
        super(title);

        setLayout(new BorderLayout());
        instructionsText = new JTextArea(instructions, 20, maxStringLength);
        instructionsText.setEditable(false);
        instructionsText.setFocusable(false);
        add(instructionsText, BorderLayout.NORTH);

        JButton btnPass = new JButton("Pass");
        btnPass.addActionListener((e) -> latch.countDown());

        JButton btnFail = new JButton("Fail");
        btnFail.addActionListener((e) -> getFailureReason());

        JPanel buttonsPanel = new JPanel();
        buttonsPanel.add(btnPass);
        buttonsPanel.add(btnFail);

        add(buttonsPanel, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void awaitAndCheck() throws InterruptedException {
        boolean timeoutHappened = !latch.await(timeoutMinutes, TimeUnit.MINUTES);

        for (Frame f : JFrame.getFrames()) {
            System.out.println(f.getTitle() + " is getting disposed");
            f.dispose();
        }

        if (tearDownRunnable != null) {
            tearDownRunnable.run();
        }

        if (timeoutHappened) {
            throw new RuntimeException("Test timed out!");
        }
        if (failed) {
            throw new RuntimeException("Test failed! : " + testFailedReason);
        }
    }

    protected void tearDownRunnable(Runnable runnable) {
        tearDownRunnable = runnable;
    }

    public void getFailureReason() {
        final JDialog dialog = new JDialog();
        dialog.setTitle("Failure reason");
        JPanel jPanel = new JPanel(new BorderLayout());
        JTextArea jTextArea = new JTextArea(5, 20);

        JButton okButton = new JButton("Ok");
        okButton.addActionListener((ae) -> {
            testFailedReason = jTextArea.getText();
            failed = true;
            dialog.dispose();
            latch.countDown();
        });

        jPanel.add(new JScrollPane(jTextArea), BorderLayout.CENTER);

        JPanel okayBtnPanel = new JPanel();
        okayBtnPanel.add(okButton);

        jPanel.add(okayBtnPanel, BorderLayout.SOUTH);
        dialog.add(jPanel);
        dialog.setLocationRelativeTo(null);
        dialog.pack();
        dialog.setVisible(true);
    }
}
