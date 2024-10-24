/*
 * Copyright (c) 2016, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7096375
 * @summary  Test that Swing does not ignore first click on a JButton after
 * decreasing system's time
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TimeChangeButtonClickTest
 */

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

public class TimeChangeButtonClickTest {
    private static final String INSTRUCTIONS = """
            <html><body>
            <ol>
            <li>
                <strong>Test 1:</strong>
                <ol style="margin-top: 0px">
                    <li>Click <b>Test Button</b> with left mouse button</li>
                    <li>Observe: Button Press count change to 1</li>
                </ol>
            </li>
            <li>
                <strong>Test 2:</strong>
                <ol style="margin-top: 0px">
                    <li>Change the system time to one hour less than current time</li>
                    <li>Click <b>Test Button</b> with left mouse button</li>
                    <li>Observe: Button Press count change to 2</li>
                </ol>
            </li>
            <li>
                <strong>Test 3:</strong>
                <ol style="margin-top: 0px">
                    <li>Change the system time by adding two hours</li>
                    <li>Click <b>Test Button</b> with left mouse button</li>
                    <li>Observe: Button Press count changes to 3</li>
                </ol>
            </li>

            <li style="margin-top: 8px; padding-top: 8px; border-top: 1px solid">
            Restore the system time.</li>
            <li style="margin-top: 8px; padding-top: 8px; border-top: 1px solid">
            Press <b>Pass</b> if Button Press count is 3,
            and <b>Fail</b> otherwise.</li>
            </ol>
            </body></html>
            """;

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                      .instructions(INSTRUCTIONS)
                      .rows(20)
                      .columns(40)
                      .splitUI(TimeChangeButtonClickTest::createTestPanel)
                      .build()
                      .awaitAndCheck();
    }

    private static JComponent createTestPanel() {
        final JLabel buttonPressCountLabel = new JLabel(
                "Button Press Count: 0");

        JButton testButton = new JButton("Test Button");
        testButton.addActionListener(new ActionListener() {
            private int buttonPressCount;

            @Override
            public void actionPerformed(ActionEvent e) {
                buttonPressCount++;
                buttonPressCountLabel.setText(
                        "Button Press Count: " + buttonPressCount);
            }
        });

        JPanel buttonPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        buttonPanel.add(testButton, gbc);
        gbc.gridy = 1;
        gbc.ipady = 16;
        buttonPanel.add(buttonPressCountLabel, gbc);

        Box testPanel = Box.createVerticalBox();
        testPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        testPanel.add(Box.createVerticalGlue());
        testPanel.add(buttonPanel);
        testPanel.add(Box.createVerticalGlue());

        return testPanel;
    }
}
