/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8367376
 * @key headful
 * @summary DesktopProperty never reset pending status to process new updates
 * @modules java.desktop/sun.swing.plaf
 * @run main DesktopPropertyResetPendingFlagTest
 */

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicButtonUI;

import sun.swing.plaf.DesktopProperty;

import java.awt.AWTEvent;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public class DesktopPropertyResetPendingFlagTest extends JFrame {

    static class ExpectedException extends RuntimeException {}

    /**
     * The original ticket required changing the system accessibility settings.
     * But we can instead automate this test if we just create & update our own
     * DesktopProperty.
     */
    static class TestDesktopProperty extends DesktopProperty {

        public TestDesktopProperty(String key, Object fallback) {
            super(key, fallback);
        }

        @Override
        public void updateUI() {
            super.updateUI();
        }
    }

    private static void assertEquals(int expectedValue, int actualValue) {
        String msg = "expected " + expectedValue +
                " observed " + actualValue;
        if (expectedValue != actualValue) {
            throw new RuntimeException(msg);
        }
        System.out.println(msg);
    }

    public static void main(String[] args) throws Exception {
        // we only override this to intercept ExpectedExceptions
        EventQueue newEventQueue = new EventQueue() {
            @Override
            protected void dispatchEvent(AWTEvent event) {
                try {
                    super.dispatchEvent(event);
                } catch (ExpectedException e) {
                    // This is part of the test. But if we don't catch
                    // this here the test harness says our test failed.
                    observedExpectedExceptionCounter++;
                }
            }
        };
        Toolkit.getDefaultToolkit().getSystemEventQueue().push(newEventQueue);

        for (UIManager.LookAndFeelInfo info :
                UIManager.getInstalledLookAndFeels()) {
            System.out.println("Setting L&F: " + info.getClassName());
            UIManager.setLookAndFeel(info.getClassName());
            runTest();
        }
    }

    private static void runTest() throws Exception {
        AtomicReference<JFrame> frameRef = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            DesktopPropertyResetPendingFlagTest t =
                    new DesktopPropertyResetPendingFlagTest();
            t.pack();
            t.setVisible(true);
            frameRef.set(t);
        });

        try {
            panelUpdateUICounter = 0;
            observedExpectedExceptionCounter = 0;

            TestDesktopProperty property =
                    new TestDesktopProperty("test", new Object());

            SwingUtilities.invokeLater(property::updateUI);
            SwingUtilities.invokeAndWait(() -> {
            });

            SwingUtilities.invokeLater(property::updateUI);
            SwingUtilities.invokeAndWait(() -> {
            });

            CountDownLatch keepAliveLatch = new CountDownLatch(1);

            SwingUtilities.invokeLater(() -> {
                // We expect 3 updateUI invocations: during construction, the first
                // property.updateUI, & the second property.updateUI
                assertEquals(3, panelUpdateUICounter);

                // We expect 2 attempts on buttonUI.uninstallUI
                assertEquals(2, observedExpectedExceptionCounter);

                // The test is finished
                keepAliveLatch.countDown();
            });

            keepAliveLatch.await();
        } finally {
            SwingUtilities.invokeAndWait(() -> frameRef.get().dispose());
        }
    }

    static int panelUpdateUICounter;
    static int observedExpectedExceptionCounter;

    DesktopPropertyResetPendingFlagTest() {
        JButton button = new JButton("button");
        button.setUI(new BasicButtonUI() {
            @Override
            public void uninstallUI(JComponent c) {
                throw new ExpectedException();
            }
        });
        JPanel p = new JPanel() {
            @Override
            public void updateUI() {
                panelUpdateUICounter++;
            }
        };
        p.add(button);
        getContentPane().add(p);
    }
}
