/*
 * Copyright (c) 2004, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 5078454
 * @summary Test horizontal wheel scroll behavior of (including RTL)
 * @requires (os.family == "windows")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual HorizScrollers
 */

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class HorizScrollers {
    private static final String INSTRUCTIONS = """
            This is a semi-automatic test with three phases.
            For each phase, you will need to change the mouse setting, as
            directed by a dialog. Once the correct setting is confirmed,
            the next test phase will run automatically.
            DO NOT TOUCH ANYTHING DURING TESTING!

            The test will automatically FAIL during testing if something
            fails. Otherwise, the test will automatically PASS after the
            third testing phase.
    """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("HorizScrollers Instructions")
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testTimeOut(10)
                .splitUIRight(ConfigPanel::new)
                .logArea(6)
                .build()
                .awaitAndCheck();
    }

    private static final int[] SCROLLAMTS = {1, 30, 3};
    private static final String[] CONFIG_MSGS = {
            "Set the scrolling speed to the slowest value (1 line).",
            "Set the scrolling speed to the fastest value (30 lines).",
            "Set the scrolling speed to two ticks above the slowest value (3 lines)."
    };

    private static int current = 0;
    private static final String MW_TEXT = "Rotate the mouse wheel here";
    private static final String CONFIG_INSTRUCTION_TEMPLATE = """
            Configure Mouse Wheel for Phase %d

            Open the Mouse Control Panel and go to the 'Wheel' tab.
            If 'Wheel' tab is not available just press Pass.

            %s

            Test the setting on the area below.
            Once the mouse is setup correctly, the area will turn green.
            When you're ready for the next part of the test to run, press GO!
    """;

    static class ConfigPanel extends JPanel
            implements ActionListener, MouseWheelListener {
        JTextArea msg;
        JButton goBtn;
        JLabel mwArea;
        int scrollAmount;

        private final Color defaultBg;

        ConfigPanel() {
            this.scrollAmount = SCROLLAMTS[current];
            Container content = this;
            content.setLayout(new BorderLayout());
            msg = new JTextArea();
            msg.setMargin(new Insets(5, 5, 5, 5));
            msg.setEditable(false);
            msg.setLineWrap(true);
            msg.setWrapStyleWord(true);
            content.add(msg, BorderLayout.NORTH);

            mwArea = new JLabel(MW_TEXT, SwingConstants.CENTER);
            mwArea.setPreferredSize(new Dimension(200, 250));
            mwArea.setBorder(BorderFactory.createLineBorder(Color.BLACK));
            mwArea.setOpaque(true);
            mwArea.addMouseWheelListener(this);
            content.add(mwArea, BorderLayout.CENTER);

            defaultBg = mwArea.getBackground();
            setPhase(current);

            goBtn = new JButton("GO!");
            goBtn.setEnabled(false);
            goBtn.addActionListener(this);
            JPanel flowPanel = new JPanel();
            flowPanel.setLayout(new FlowLayout());
            flowPanel.add(goBtn);
            content.add(flowPanel, BorderLayout.SOUTH);

            setPreferredSize(new Dimension(600, 400));
        }

        public void setPhase(int phase) {
            if (phase < 3) {
                setVisible(true);
                PassFailJFrame.log("Phase %d scroll speed %d"
                        .formatted(phase + 1, SCROLLAMTS[phase]));
                PassFailJFrame.log(CONFIG_MSGS[phase]);

                scrollAmount = SCROLLAMTS[phase];
                msg.setText(CONFIG_INSTRUCTION_TEMPLATE
                        .formatted(phase + 1, CONFIG_MSGS[phase]));
                mwArea.setBackground(defaultBg);
                mwArea.setText(MW_TEXT);
            } else {
                // all cases passed
                showFinalReminderIfNeeded(false);
            }
        }

        private void showFinalReminderIfNeeded(boolean isFailure) {
            if (scrollAmount != 3) {
                JOptionPane.showMessageDialog(
                        ConfigPanel.this.getTopLevelAncestor(),
                        ("Test %s. Please make sure you have restored " +
                                "the original scrolling speed in the " +
                                "Mouse settings.")
                                .formatted(isFailure
                                        ? "failed"
                                        : "passed"),
                        isFailure
                                ? "Failure"
                                : "Success",
                        isFailure
                                ? JOptionPane.WARNING_MESSAGE
                                : JOptionPane.INFORMATION_MESSAGE
                );
            }

            if (isFailure) {
                PassFailJFrame.forceFail();
            } else {
                PassFailJFrame.forcePass();
            }
        }

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() == goBtn) {
                goBtn.setEnabled(false);

                new Thread(() -> { // new thread to avoid running robot on EDT
                    boolean passed;
                    try {
                        passed = RTLScrollers.runTest(SCROLLAMTS[current]);
                    } catch (Exception ex) {
                        PassFailJFrame.log("Failure: " + ex);
                        SwingUtilities.invokeLater(() ->
                                showFinalReminderIfNeeded(true));
                        return;
                    }

                    PassFailJFrame.log("Phase %d passed: %b\n"
                            .formatted(current + 1, passed));
                    if (passed) {
                        SwingUtilities.invokeLater(() -> {
                            goBtn.setEnabled(true);
                            setPhase(++current);
                        });
                    } else {
                        SwingUtilities.invokeLater(() ->
                                showFinalReminderIfNeeded(true));
                    }
                }).start();
            }
        }

        public void mouseWheelMoved(MouseWheelEvent e) {
            int eventScrollAmt = e.getScrollAmount();
            if (eventScrollAmt == scrollAmount) {
                mwArea.setBackground(Color.GREEN);
                mwArea.setText("Mouse wheel configured - press Go");
                goBtn.setEnabled(true);
                goBtn.requestFocusInWindow();
                PassFailJFrame.log("Proceed to the test with go button");
                return;
            }
            if (eventScrollAmt < scrollAmount) {
                mwArea.setText("Increase the scroll speed. (Want:"
                        + scrollAmount + " Got:" + eventScrollAmt + ")");
            } else {
                mwArea.setText("Decrease the scroll speed. (Want:"
                        + scrollAmount + " Got:" + eventScrollAmt + ")");
            }
        }
    }
}
