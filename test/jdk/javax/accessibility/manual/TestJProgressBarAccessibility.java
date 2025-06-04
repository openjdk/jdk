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


/*
@test
@key headful
@summary manual test for accessibility JProgressBar
@run main/manual TestJProgressBarAccessibility
*/

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.accessibility.AccessibleContext;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

import lib.ManualTestFrame;
import lib.TestResult;

public class TestJProgressBarAccessibility {

    private static JFrame frame;
    private static volatile int value = 10;
    private static final String instruction = """
            Aim : Check whether JProgressBar value is read in case of VoiceOver or
            Screen magnifier shows the magnified value in case of Screen magnifier is enabled
            1) Move the mouse pointer over the JProgressBar and if you
            hear the JProgressBar value in case of VoiceOver then the test pass else fail.
            2) Move the mouse pointer over the JProgressBar and if you see the magnified value
            when Screen magnifier is enabled then the test pass else fail.
            """;

    private static void createTestUI() throws InterruptedException, InvocationTargetException {
        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("Test JProgressBar accessibility");
            JProgressBar progressBar = new JProgressBar();
            progressBar.setValue(value);
            progressBar.setStringPainted(true);

            progressBar.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    super.mouseClicked(e);
                    if ( value == 100) {
                        value = 0;
                    } else {
                        value += 5;
                    }
                    progressBar.setValue(value);
                }
            });

            AccessibleContext accessibleContext =
                    progressBar.getAccessibleContext();
            accessibleContext.setAccessibleName("JProgressBar accessibility name");
            accessibleContext.setAccessibleDescription("Jprogress accessibility " +
                    "description");

            frame.getContentPane().add(progressBar, BorderLayout.CENTER);

            frame.setSize(200,200);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, IOException {

        Consumer<JEditorPane> testInstProvider = e -> {
            e.setContentType("text/plain");
            e.setText(instruction);
        };

        Supplier<TestResult> resultSupplier = ManualTestFrame.showUI(
                "JProgressBar " +
                        "Accessibility Test", "Wait until the Test UI is " +
                        "seen", testInstProvider);

        // Create and show TestUI
        createTestUI();

        //this will block until user decision to pass or fail the test
        TestResult  testResult = resultSupplier.get();
        ManualTestFrame.handleResult(testResult,"TestJProgressBarAccessibility");
    }
}

