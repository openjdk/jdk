/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4401480
 * @summary Tests that closing ProgressMonitor dialog cancels it
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual bug4401480
 */

import java.lang.reflect.InvocationTargetException;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingUtilities;

public class bug4401480 {
    private static ProgressMonitor monitor;
    private static volatile boolean cancelled = false;

    private static final String INSTRUCTIONS = """
            This is a semi-automated test which automatically
            passes if closing the JProgressBar dialog cancels it.
            Read the following test instructions and when ready
            click on the Start button below.

            After clicking on Start button wait for few seconds for
            progress monitor (a dialog with progress bar) to appear.
            Close it by clicking on the window close button.
            DO NOT click on Cancel button.

            NOTE:
            Ensure to click on the window close button before
            progress bar reaches its max limit.
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("JProgress Monitor Instructions")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUIBottom(bug4401480::createTestUI)
                .build()
                .awaitAndCheck();
    }

    private static JPanel createTestUI() {
        JPanel panel = new JPanel();
        JButton startButton = new JButton("Start");
        startButton.addActionListener(e -> {
            monitor = new ProgressMonitor(null, "Progress", "Running ...", 0, 10);
            monitor.setProgress(0);

            new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    int count = i;
                    try {
                        SwingUtilities.invokeAndWait(() ->
                                        monitor.setProgress(count));
                        Thread.sleep(2000);
                        SwingUtilities.invokeAndWait(() ->
                                        cancelled = monitor.isCanceled());
                    } catch (InterruptedException
                             | InvocationTargetException ex) {
                        throw new RuntimeException(ex);
                    }
                    if (cancelled) {
                        break;
                    }
                }

                if (cancelled) {
                    PassFailJFrame.forcePass();
                } else {
                    PassFailJFrame.forceFail("Test Failed! JProgress Monitor"
                                             + " was not cancelled");
                }
            }).start();
        });
        panel.add(startButton);
        return panel;
    }
}
