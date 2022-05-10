/*
 * Copyright (c) 2001, 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.GridLayout;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;
import javax.swing.UnsupportedLookAndFeelException;

import static javax.swing.UIManager.getInstalledLookAndFeels;

/*
 * @test
 * @key headful
 * @bug 4138746
 * @summary This testcase tests RFE-4138746 request, verifies the case-sensitive
 *          setting of Mnemonics to AbstractButton & JLabel.
 * @run main JLabelMnemonicsTest
 */
public class JLabelMnemonicsTest {

    private static JButton button1;
    private static JButton button2;
    private static JLabel label1;
    private static JLabel label2;
    private static JPanel panel;
    private static Robot robot;
    private static boolean result;
    private static CountDownLatch focusGainedLatch;
    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        final boolean isMac =
                System.getProperty("os.name").toLowerCase().contains("os x");

        robot = new Robot();
        robot.setAutoWaitForIdle(true);
        robot.setAutoDelay(200);
        List<String> lafs = Arrays.stream(getInstalledLookAndFeels())
                                  .map(LookAndFeelInfo::getClassName)
                                  .collect(Collectors.toList());
        for (final String laf : lafs) {
            try {
                result = true;
                focusGainedLatch = new CountDownLatch(1);
                AtomicBoolean lafSetSuccess = new AtomicBoolean(false);
                SwingUtilities.invokeAndWait(() -> {
                    lafSetSuccess.set(setLookAndFeel(laf));
                    if (lafSetSuccess.get()) {
                        createUI();
                    }
                });
                if (!lafSetSuccess.get()) {
                    continue;
                }
                robot.waitForIdle();

                // Verifier 1: Verifies if getDisplayedMnemonicIndex() returns the
                // right index set with setDisplayedMnemonicIndex method for JButton
                if (getDisplayedMnemonicIndex(button1) == 5 &&
                    getDisplayedMnemonicIndex(button2) == 0) {
                    System.out.println("Verifier 1 Passed");
                } else {
                    System.out.println(
                            "Verifier 1 Failed, testing JButton failed");
                    result = false;
                }

                // Verifier 2: Verifies that, on setting displayedMnemonicIndex to
                // -1, the component can be still accessed with the mnemonic set
                if (isMac) {
                    hitKeys(KeyEvent.VK_ALT, KeyEvent.VK_CONTROL,
                            KeyEvent.VK_V);
                } else {
                    hitKeys(KeyEvent.VK_ALT, KeyEvent.VK_V);
                }
                if (focusGainedLatch.await(3, TimeUnit.SECONDS)) {
                    System.out.println("Verifier 2 Passed");
                } else {
                    System.out.println(
                            "Verifier 2 Failed, Waited too long, but Button3 " +
                            "has not yet gained focus in " + laf);
                    result = false;
                }

                // Verifier 3: Testing JLabel: Verifies if
                // getDisplayedMnemonicIndex() returns the right
                // index set with setDisplayedMnemonicIndex method for JLabel
                if (getDisplayedMnemonicIndex(label1) == 5 &&
                    getDisplayedMnemonicIndex(label2) == 0) {
                    System.out.println("Verifier 3 Passed");
                } else {
                    System.out.println("Verifier 3, testing JLabel Failed");
                    result = false;
                }

                if (result) {
                    System.out.println("Test Passed in " + laf);
                } else {
                    throw new RuntimeException(
                            "Test Failed, as one or more verifiers failed in " +
                            laf);
                }
            } finally {
                SwingUtilities.invokeAndWait(JLabelMnemonicsTest::disposeFrame);
            }
        }
    }

    private static int getDisplayedMnemonicIndex(JLabel jLabel)
            throws Exception {
        final AtomicInteger index = new AtomicInteger();
        SwingUtilities.invokeAndWait(
                () -> index.set(jLabel.getDisplayedMnemonicIndex()));
        return index.get();
    }

    private static int getDisplayedMnemonicIndex(AbstractButton button)
            throws Exception {
        final AtomicInteger index = new AtomicInteger();
        SwingUtilities.invokeAndWait(
                () -> index.set(button.getDisplayedMnemonicIndex()));
        return index.get();
    }

    private static void hitKeys(int... keys) {
        for (int key : keys) {
            robot.keyPress(key);
        }

        for (int i = keys.length - 1; i >= 0; i--) {
            robot.keyRelease(keys[i]);
        }
    }

    private static void createUI() {
        frame = new JFrame();
        panel = new JPanel();
        panel.setLayout(new GridLayout(2, 3));

        button1 = new JButton("Save As");
        button1.setMnemonic(KeyEvent.VK_A);
        button1.setDisplayedMnemonicIndex(5);
        panel.add(button1);

        button2 = new JButton("save AS");
        button2.setMnemonic(KeyEvent.VK_S);
        panel.add(button2);

        JButton button3 = new JButton("Save As");
        button3.setMnemonic(KeyEvent.VK_V);
        button3.setDisplayedMnemonicIndex(-1);
        panel.add(button3);
        button3.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent fe) {
                System.out.println("FocusGained on Button3");
                focusGainedLatch.countDown();
            }
        });

        label1 = new JLabel("Save As");
        label1.setDisplayedMnemonic(KeyEvent.VK_A);
        label1.setDisplayedMnemonicIndex(5);
        panel.add(label1);

        label2 = new JLabel("save AS");
        label2.setDisplayedMnemonic(KeyEvent.VK_S);
        panel.add(label2);

        frame.add(panel);
        frame.setLocationRelativeTo(null);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private static boolean setLookAndFeel(String lafName) {
        try {
            UIManager.setLookAndFeel(lafName);
        } catch (UnsupportedLookAndFeelException ignored) {
            System.out.println("Ignoring Unsupported L&F: " + lafName);
            return false;
        } catch (ClassNotFoundException | InstantiationException
                | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
        return true;
    }

    private static void disposeFrame() {
        if (frame != null) {
            frame.dispose();
            frame = null;
        }
    }

}
