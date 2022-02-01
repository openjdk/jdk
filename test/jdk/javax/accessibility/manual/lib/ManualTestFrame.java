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
package lib;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.border.BevelBorder;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.awt.BorderLayout.*;
import static javax.swing.SwingUtilities.invokeAndWait;

/**
 * A frame which can be used to display manual test descriptions as well as, in case of a failure,
 * enter failure reason and capture the screen.
 */
public class ManualTestFrame extends JFrame {

    private boolean alreadyFailed = false;

    private ManualTestFrame(String testName, String headerText, URL instructions, Consumer<TestResult> listener) throws IOException {

        super(testName);

        JLabel statusLabel = new JLabel("Follow test description, select \"Pass\" or \"Fail\"");
        statusLabel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        PassFailPane[] passFail = new PassFailPane[1];
        FailureReasonPane failureReason = new FailureReasonPane(reason -> {
            passFail[0].setFailEnabled(!reason.isEmpty());
        });
        ScreenImagePane image = new ScreenImagePane(e -> {
            listener.accept(new TestResult(e));
            dispose();
        });

        JPanel failureInfoPane = new JPanel();
        failureInfoPane.setLayout(new GridLayout(1, 2, 10, 10));
        failureInfoPane.add(failureReason);
        failureInfoPane.add(image);
        failureInfoPane.setVisible(false);

        JPanel main = new JPanel();
        main.setLayout(new BorderLayout(10, 10));
        DescriptionPane description = new DescriptionPane(instructions);
        main.add(description, CENTER);
        passFail[0] = new PassFailPane((status) -> {
            if (status) {
                listener.accept(new TestResult());
                dispose();
            } else {
                if (!alreadyFailed) {
                    alreadyFailed = true;
                    split.setDividerLocation(.5);
                    failureInfoPane.setVisible(true);
                    pack();
                    image.capture();
                    failureReason.requestFocus();
                    statusLabel.setText("Enter failure reason, re-take screenshot, push \"Fail\"");
                } else {
                    listener.accept(new TestResult(failureReason.getReason(), image.getImage()));
                    dispose();
                }
            }
        });
        main.add(passFail[0], SOUTH);

        split.setLeftComponent(main);
        split.setRightComponent(failureInfoPane);
        split.setDividerLocation(1.);

        getContentPane().setLayout(new BorderLayout());

        if (headerText != null) {
            JTextArea warningLabel = new JTextArea(headerText);
            warningLabel.setBorder(BorderFactory.createBevelBorder(BevelBorder.LOWERED));
            warningLabel.setEditable(false);
            warningLabel.setFocusable(false);
            getContentPane().add(warningLabel, NORTH);
        }

        getContentPane().add(statusLabel, SOUTH);
        getContentPane().add(split, CENTER);

        setPreferredSize(new Dimension(800, 600));
        pack();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setVisible(true);
    }

    /**
     * Show a test control frame which allows a user to either pass or fail the test.
     * @param testName
     * @param headerText
     * @param instructions
     * @return Returning supplier blocks till the test is passed or failed by the user.
     * @throws InterruptedException
     * @throws InvocationTargetException
     */
    public static Supplier<TestResult> showUI(String testName, String headerText, URL instructions)
            throws InterruptedException, InvocationTargetException {
        AtomicReference<TestResult> resultContainer = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        invokeAndWait(() -> {
            try {
                new ManualTestFrame(testName, headerText, instructions, (status) -> {
                    resultContainer.set(status);
                    latch.countDown();
                });
            } catch (IOException e) {
                resultContainer.set(new TestResult(e));
                e.printStackTrace();
            }
        });
        return () -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                return new TestResult(e);
            }
            return resultContainer.get();
        };
    }

}
