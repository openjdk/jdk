/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4133768 4363569
 * @summary Tests how button displays its icons
 * @key headful
 * @run main bug4133768
 */

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.image.BufferedImage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;

public class bug4133768 {
    private static Icon RED;
    private static Icon GREEN;
    private static JFrame f;
    private static AbstractButton[] buttons;
    private static volatile Point buttonLocation;
    private static volatile int buttonWidth;
    private static volatile int buttonHeight;
    private static Robot robot;
    private static int ROLLOVER_Y_OFFSET = 4;
    private static CountDownLatch frameGainedFocusLatch =
        new CountDownLatch(1);

    public static void main(String[] args) throws Exception {
        try {
            createTestImages();
            createUI();
            f.requestFocus();
            if (!frameGainedFocusLatch.await(5, TimeUnit.SECONDS)) {
                throw new RuntimeException("Waited too long, but can't gain" +
                    " focus for frame");
            }
            robot = new Robot();
            for (AbstractButton b : buttons) {
                testEnabledButton(b);
            }
            for (AbstractButton b : buttons) {
                b.setEnabled(false);
                robot.delay(1000);
                testDisabledButton(b);
            }
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                if (f != null) {
                    f.dispose();
                }
            });
        }
    }

    private static void createTestImages() {
        int imageWidth = 100;
        int imageHeight = 100;
        BufferedImage redImg = new BufferedImage(imageWidth, imageHeight,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = redImg.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        RED = new ImageIcon(redImg);
        BufferedImage greenImg = new BufferedImage(imageWidth, imageHeight,
            BufferedImage.TYPE_INT_RGB);
        g = greenImg.createGraphics();
        g.setColor(Color.GREEN);
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        GREEN = new ImageIcon(greenImg);
    }

    private static void createUI() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            f = new JFrame("ButtonIconsTest");
            buttons = new AbstractButton[] {
                new JToggleButton(),
                new JRadioButton(),
                new JCheckBox()
            };

            JPanel buttonPanel = new JPanel();
            for (int i = 0; i < buttons.length; i++) {
                AbstractButton b = buttons[i];
                b.setIcon(RED);
                b.setSelected(true);
                b.setRolloverSelectedIcon(GREEN);
                buttonPanel.add(b);
            }
            f.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    frameGainedFocusLatch.countDown();
                }
            });
            f.setLayout(new GridLayout(2, 1));
            f.add(buttonPanel);
            f.pack();
            f.setLocationRelativeTo(null);
            f.setAlwaysOnTop(true);
            f.setVisible(true);
        });
    }

    private static void testEnabledButton(AbstractButton button)
        throws Exception {
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            buttonLocation = button.getLocationOnScreen();
            buttonWidth = button.getWidth();
            buttonHeight = button.getHeight();
        });
        robot.mouseMove(buttonLocation.x + buttonWidth / 2,
            buttonLocation.y + ROLLOVER_Y_OFFSET);
        robot.delay(1000);
        Color buttonColor = robot.getPixelColor(buttonLocation.x +
            buttonWidth / 2, buttonLocation.y + buttonHeight / 2);
        if (!buttonColor.equals(Color.GREEN)) {
            throw new RuntimeException("Button roll over color is : " +
                buttonColor + " but it should be : " + Color.GREEN);
        }
    }

    private static void testDisabledButton(AbstractButton button)
        throws Exception {
        robot.waitForIdle();
        SwingUtilities.invokeAndWait(() -> {
            buttonLocation = button.getLocationOnScreen();
            buttonWidth = button.getWidth();
            buttonHeight = button.getHeight();
        });
        robot.delay(200);
        Color buttonColor = robot.getPixelColor(buttonLocation.x +
            buttonWidth / 2, buttonLocation.y + buttonHeight / 2);
        if (buttonColor.equals(Color.GREEN) ||
            buttonColor.equals(Color.RED)) {
            throw new RuntimeException("Disabled button color should not be : "
                + buttonColor);
        }
    }
}
