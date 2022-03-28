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
import java.awt.HeadlessException;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import static javax.swing.SwingUtilities.invokeAndWait;
import static javax.swing.SwingUtilities.isEventDispatchThread;

public class PassFailJFrame extends JFrame {
    private final CountDownLatch latch = new CountDownLatch(1);
    private boolean failed = false;
    private String testFailedReason;
    private JTextArea instructionsText;
    private final int maxRowLength;
    private final int maxStringLength;
    private final int timeoutMinutes;

    /**
     * Constructs a JFrame with a given title & serves as test instructional
     * frame where the user follows the specified test instruction in order
     * to test the test case & mark the test pass or fail. If the expected
     * result is seen then the user click on the 'Pass' button else click
     * on the 'Fail' button and the reason for the failure should be
     * specified in the JDailog JTextArea.
     *
     * @param title           title of the Frame.
     * @param instructions    specified instruction that user should follow.
     * @param maxRowLength    number of visible rows of the JTextArea where the
     *                        instruction is show.
     * @param maxStringLength number of columns of the instructional JTextArea
     * @param timeoutMinutes  timeout of the test where time is specified in
     *                        minutes.
     * @throws HeadlessException
     * @throws InterruptedException
     * @throws InvocationTargetException
     */
    public PassFailJFrame(String title, String instructions,
                          int maxRowLength, int maxStringLength,
                          int timeoutMinutes) throws HeadlessException,
            InterruptedException, InvocationTargetException {
        super(title);
        this.maxRowLength = maxRowLength;
        this.maxStringLength = maxStringLength;
        this.timeoutMinutes = timeoutMinutes;

        invokeAndWait(() -> {
            setLayout(new BorderLayout());
            instructionsText = new JTextArea(instructions, maxRowLength, maxStringLength);
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
            setLocation(10, 10);
            setVisible(true);
        });
    }

    /**
     * Wait for the user decision i,e user selects pass or fail button.
     * If user does not select pass or fail button then the test waits for
     * the specified timeoutMinutes period and the test gets timeout.
     *
     * @throws InterruptedException
     * @throws InvocationTargetException
     */
    public void awaitAndCheck() throws InterruptedException, InvocationTargetException {
        boolean timeoutHappened = !latch.await(this.timeoutMinutes,
                TimeUnit.MINUTES);
        if (isEventDispatchThread()) {
            dispose();
        } else invokeAndWait(() -> dispose());

        if (timeoutHappened) {
            throw new RuntimeException("Test timed out!");
        }
        if (failed) {
            throw new RuntimeException("Test failed! : " + testFailedReason);
        }
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
