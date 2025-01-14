/*
 * Copyright (c) 2016, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @key headful
 * @bug 8074883
 * @summary Tab key should move to focused button in a button group
 * @run main ButtonGroupFocusTest
 */

import java.awt.AWTEvent;
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;

import javax.imageio.ImageIO;
import javax.swing.ButtonGroup;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.SwingUtilities;

import static java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

public final class ButtonGroupFocusTest {

    private static JRadioButton button1;
    private static JRadioButton button2;
    private static JRadioButton button3;
    private static JRadioButton button4;
    private static JRadioButton button5;

    private static final CountDownLatch button2FocusLatch = new CountDownLatch(1);
    private static final CountDownLatch button3FocusLatch = new CountDownLatch(1);
    private static final CountDownLatch button4FocusLatch = new CountDownLatch(1);

    private static final CountDownLatch button2FocusLatch2 = new CountDownLatch(2);

    private static final long FOCUS_TIMEOUT = 4;

    private static JFrame frame;

    public static void main(String[] args) throws Exception {
        final Robot robot = new Robot();

        SwingUtilities.invokeAndWait(() -> {
            frame = new JFrame("ButtonGroupFocusTest");
            Container contentPane = frame.getContentPane();
            contentPane.setLayout(new FlowLayout());
            button1 = new JRadioButton("Button 1");
            contentPane.add(button1);
            button2 = new JRadioButton("Button 2");
            contentPane.add(button2);
            button3 = new JRadioButton("Button 3");
            contentPane.add(button3);
            button4 = new JRadioButton("Button 4");
            contentPane.add(button4);
            button5 = new JRadioButton("Button 5");
            contentPane.add(button5);

            ButtonGroup group = new ButtonGroup();
            group.add(button1);
            group.add(button2);
            group.add(button3);

            group = new ButtonGroup();
            group.add(button4);
            group.add(button5);

            button2.addFocusListener(new LatchFocusListener(button2FocusLatch));
            button3.addFocusListener(new LatchFocusListener(button3FocusLatch));
            button4.addFocusListener(new LatchFocusListener(button4FocusLatch));

            button2.addFocusListener(new LatchFocusListener(button2FocusLatch2));

            button2.setSelected(true);

            // Debugging aid: log focus owner changes...
            KeyboardFocusManager focusManager = getCurrentKeyboardFocusManager();
            focusManager.addPropertyChangeListener("focusOwner",
                    e -> System.out.println(e.getPropertyName()
                                            + "\n\t" + e.getOldValue()
                                            + "\n\t" + e.getNewValue()));

            // ...and dispatched key events
            Toolkit.getDefaultToolkit().addAWTEventListener(
                    e -> System.out.println("Dispatched " + e),
                    AWTEvent.KEY_EVENT_MASK);

            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        try {
            if (!button2FocusLatch.await(FOCUS_TIMEOUT, SECONDS)) {
                throw new RuntimeException("Button 2 should get focus "
                                           + "after activation");
            }
            robot.waitForIdle();
            robot.delay(200);

            System.out.println("\n\n*** Tab 1st");
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);

            if (!button4FocusLatch.await(FOCUS_TIMEOUT, SECONDS)) {
                throw new RuntimeException("Button 4 should get focus");
            }
            robot.waitForIdle();
            robot.delay(200);

            if (button2FocusLatch2.await(1, MILLISECONDS)) {
                throw new RuntimeException("Focus moved back to Button 2");
            }

            SwingUtilities.invokeAndWait(() -> button3.setSelected(true));
            robot.waitForIdle();
            robot.delay(200);

            System.out.println("\n\n*** Tab 2nd");
            robot.keyPress(KeyEvent.VK_TAB);
            robot.keyRelease(KeyEvent.VK_TAB);

            if (!button3FocusLatch.await(FOCUS_TIMEOUT, SECONDS)) {
                throw new RuntimeException("Selected Button 3 should get focus");
            }
        } catch (Exception e) {
            BufferedImage image = robot.createScreenCapture(getFrameBounds());
            ImageIO.write(image, "png",
                          new File("image.png"));

            SwingUtilities.invokeAndWait(() ->
                    System.err.println("Current focus owner: "
                                       + getCurrentKeyboardFocusManager()
                                         .getFocusOwner()));

            throw e;
        } finally {
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }

    private static Rectangle getFrameBounds() throws Exception {
        Rectangle[] bounds = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> bounds[0] = frame.getBounds());
        return bounds[0];
    }

    private static final class LatchFocusListener extends FocusAdapter {
        private final CountDownLatch focusGainedLatch;

        private LatchFocusListener(CountDownLatch focusGainedLatch) {
            this.focusGainedLatch = focusGainedLatch;
        }

        @Override
        public void focusGained(FocusEvent e) {
            focusGainedLatch.countDown();
        }
    }
}
