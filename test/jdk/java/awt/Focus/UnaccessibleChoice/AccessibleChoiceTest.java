/*
 * Copyright (c) 2002, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Button;
import java.awt.Choice;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

/*
 * @test
 * @bug 4478780
 * @key headful
 * @summary Tests that Choice can be accessed and controlled by keyboard.
 */

public class AccessibleChoiceTest {
    static Frame frame;
    static Choice choice;
    static Button button;
    static volatile CountDownLatch go;
    static volatile Point loc;
    static volatile int bWidth;
    static volatile int bHeight;

    public static void main(final String[] args) throws Exception {
        try {
            createAndShowUI();
            test();
        } finally {
            if (frame != null) {
                EventQueue.invokeAndWait(() -> frame.dispose());
            }
        }
    }

    public static void createAndShowUI() throws Exception {
        go = new CountDownLatch(1);
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("Accessible Choice Test Frame");
            choice = new Choice();
            button = new Button("default owner");
            frame.setLayout(new FlowLayout());
            frame.add(button);
            button.addFocusListener(new FocusAdapter() {
                public void focusGained(FocusEvent e) {
                    go.countDown();
                }
            });
            choice.add("One");
            choice.add("Two");
            frame.add(choice);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void test() throws Exception {
        Robot robot;
        try {
            robot = new Robot();
        } catch (Exception ex) {
            throw new RuntimeException("Can't create robot");
        }
        robot.waitForIdle();
        robot.delay(1000);
        robot.setAutoWaitForIdle(true);

        // Focus default button and wait till it gets focus
        EventQueue.invokeAndWait(() -> {
            loc = button.getLocationOnScreen();
            bWidth = button.getWidth();
            bHeight = button.getHeight();
        });
        robot.mouseMove(loc.x + bWidth / 2,
                        loc.y + bHeight / 2);
        robot.delay(500);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);

        try {
            go.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            throw new RuntimeException("Interrupted !!!");
        }

        if (!button.isFocusOwner()) {
            throw new RuntimeException("Button doesn't have focus");
        }

        robot.delay(500);

        // Press Tab key to move focus to Choice
        robot.keyPress(KeyEvent.VK_TAB);
        robot.keyRelease(KeyEvent.VK_TAB);

        robot.delay(500);

        if (!choice.isFocusOwner()) {
            throw new RuntimeException("Choice doesn't have focus");
        }

        // Press Down key to select next item in the choice
        // If bug exists we won't be able to do so
        robot.keyPress(KeyEvent.VK_DOWN);
        robot.keyRelease(KeyEvent.VK_DOWN);

        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("mac")) {
            robot.delay(500);
            robot.keyPress(KeyEvent.VK_DOWN);
            robot.keyRelease(KeyEvent.VK_DOWN);
            robot.delay(500);
            robot.keyPress(KeyEvent.VK_ENTER);
            robot.keyRelease(KeyEvent.VK_ENTER);
        }

        robot.delay(1000);

        // On success second item should be selected
        if (!choice.getSelectedItem().equals(choice.getItem(1))) {
            // Print out os name to check if mac conditional is relevant
            System.err.println("Failed on os: " + osName);

            // Save image to better debug the status of test when failing
            GraphicsConfiguration ge = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice()
                    .getDefaultConfiguration();
            BufferedImage failImage = robot.createScreenCapture(ge.getBounds());
            ImageIO.write(failImage, "png", new File("failImage.png"));

            throw new RuntimeException("Choice can't be controlled by keyboard");
        }
    }
}
