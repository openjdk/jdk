/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @summary Make sure that on changing state of Undecorated Frame,
 *          all the components on it are repainted correctly
 * @author Jitender(jitender.singh@eng.sun.com) area=AWT
 * @author yan
 * @library /lib/client /test/lib
 * @build ExtendedRobot jdk.test.lib.Platform
 * @run main RepaintTest
 */

import jdk.test.lib.Platform;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.TextField;
import javax.swing.JFrame;
import javax.swing.JButton;
import javax.swing.JTextField;
import javax.swing.JPanel;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.PixelGrabber;
import java.io.File;

public class RepaintTest {
    private static final int delay = 150;

    private Frame frame;
    private Component button;
    private Component textField;
    private ExtendedRobot robot;
    private final Object buttonLock = new Object();
    private volatile boolean buttonClicked = false;
    private final int MAX_TOLERANCE_LEVEL = 10;

    public static void main(String[] args) throws Exception {
        RepaintTest test = new RepaintTest();
        try {
            test.doTest(false);
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
        try {
            test.doTest(true);
        } finally {
            EventQueue.invokeAndWait(test::dispose);
        }
    }

    private void initializeGUI(boolean swingControl) {
        frame = swingControl ? new JFrame() : new Frame();
        frame.setLayout(new BorderLayout());

        frame.setSize(300, 300);
        frame.setUndecorated(true);

        button = createButton(swingControl, (swingControl ? "Swing Button" : "AWT Button"));
        textField = swingControl ? new JTextField("TextField") : new TextField("TextField");
        Container panel1 = swingControl ? new JPanel() : new Panel();
        Container panel2 = swingControl ? new JPanel() : new Panel();
        panel1.add(button);
        panel2.add(textField);
        frame.add(panel2, BorderLayout.SOUTH);
        frame.add(panel1, BorderLayout.NORTH);
        frame.setLocationRelativeTo(null);

        frame.setBackground(Color.green);
        frame.setVisible(true);
    }

    private void dispose() {
        if (frame != null) {
            frame.dispose();
        }
    }

    private Component createButton(boolean swingControl, String txt) {
        ActionListener actionListener = e -> {
            buttonClicked = true;
            System.out.println("Clicked!!");
            synchronized (buttonLock) {
                try {
                    buttonLock.notifyAll();
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        if(swingControl) {
            JButton jbtn = new JButton(txt);
            jbtn.addActionListener(actionListener);
            return jbtn;
        } else {
            Button btn = new Button(txt);
            btn.addActionListener(actionListener);
            return btn;
        }
    }

    public void doTest(boolean swingControl) throws Exception {

        robot = new ExtendedRobot();
        robot.setAutoDelay(50);

        EventQueue.invokeAndWait(() -> initializeGUI(swingControl));
        robot.waitForIdle(1000);

        robot.mouseMove(button.getLocationOnScreen().x + button.getSize().width / 2,
                        button.getLocationOnScreen().y + button.getSize().height / 2);

        robot.click();
        robot.waitForIdle(delay);

        if (! buttonClicked) {
            synchronized (buttonLock) {
                try {
                    buttonLock.wait(delay * 10);
                } catch (Exception e) {
                }
            }
        }
        if (! buttonClicked) {
            System.err.println("ActionEvent not triggered when " +
                    "button is clicked!");
            throw new RuntimeException("ActionEvent not triggered");
        }

        robot.waitForIdle(1000); // Need to wait until look of the button
                                      // returns to normal undepressed

        if (!paintAndRepaint(button, (swingControl ? "J" : "") + "Button")
            || !paintAndRepaint(textField, (swingControl ? "J" : "") + "TextField")) {
            throw new RuntimeException("Test failed");
        }
    }
    private boolean paintAndRepaint(Component comp, String prefix) throws Exception {
        boolean passed = true;
        //Capture the component & compare it's dimensions
        //before iconifying & after frame comes back from
        //iconified to normal state
        System.out.printf("paintAndRepaint %s %s\n", prefix, comp);
        Point p = comp.getLocationOnScreen();
        Rectangle bRect = new Rectangle((int)p.getX(), (int)p.getY(),
                                                comp.getWidth(), comp.getHeight());
        BufferedImage capturedImage = robot.createScreenCapture(bRect);
        BufferedImage frameImage = robot.createScreenCapture(frame.getBounds());

        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.ICONIFIED));
        robot.waitForIdle(1500);
        EventQueue.invokeAndWait(() -> frame.setExtendedState(Frame.NORMAL));
        robot.waitForIdle(1500);

        if (Platform.isOnWayland()) {
            // Robot.mouseMove does not move the actual mouse cursor on the
            // screen in X11 compatibility mode on Wayland, but only within
            // the XWayland server.
            // This can cause the test to fail if the actual mouse cursor on
            // the screen is somewhere over the test window, so that when the
            // test window is restored from the iconified state, it's detected
            // that the mouse cursor has moved to the mouse cursor position on
            // the screen, and is no longer hovering over the button, so the
            // button is painted differently.
            robot.mouseMove(button.getLocationOnScreen().x + button.getSize().width / 2,
                    button.getLocationOnScreen().y + button.getSize().height / 2);
            robot.waitForIdle();
        }

        if (! p.equals(comp.getLocationOnScreen())) {
            passed = false;
            System.err.println("FAIL: Frame or component did not get positioned in the same place");
        }

        p = comp.getLocationOnScreen();
        bRect = new Rectangle((int)p.getX(), (int)p.getY(),
                                  comp.getWidth(), comp.getHeight());
        BufferedImage capturedImage2 = robot.createScreenCapture(bRect);
        BufferedImage frameImage2 = robot.createScreenCapture(frame.getBounds());

        if (!compareImages(capturedImage, capturedImage2)) {
            passed = false;

            try {
                javax.imageio.ImageIO.write(capturedImage, "png",
                        new File(prefix + "BeforeMinimize.png"));
                javax.imageio.ImageIO.write(capturedImage2, "png",
                        new File(prefix + "AfterMinimize.png"));
                javax.imageio.ImageIO.write(frameImage, "png",
                        new File("Frame" + prefix + "BeforeMinimize.png"));
                javax.imageio.ImageIO.write(frameImage2, "png",
                        new File("Frame" + prefix + "AfterMinimize.png"));
            } catch (Exception e) {
                e.printStackTrace();
            }

            System.err.println("FAIL: The frame or component did not get repainted correctly");
        }
        return passed;
    }

    //method for comparing two images
    public boolean compareImages(BufferedImage capturedImg, BufferedImage realImg) {
        int capturedPixels[], realPixels[];
        int imgWidth, imgHeight;
        boolean comparison = true;
        int toleranceLevel = 0;

        imgWidth = capturedImg.getWidth(null);
        imgHeight = capturedImg.getHeight(null);
        capturedPixels = new int[imgWidth * imgHeight];
        realPixels = new int[imgWidth * imgHeight];

        try {
            PixelGrabber pgCapturedImg = new PixelGrabber(capturedImg, 0, 0,
                              imgWidth, imgHeight, capturedPixels, 0, imgWidth);
            pgCapturedImg.grabPixels();

            PixelGrabber pgRealImg = new PixelGrabber(realImg, 0, 0,
                              imgWidth, imgHeight, realPixels, 0, imgWidth);
            pgRealImg.grabPixels();

            for(int i=0; i<(imgWidth * imgHeight); i++) {
                if(capturedPixels[i] != realPixels[i]) {
                    toleranceLevel++;
                }
            }

            if (toleranceLevel > MAX_TOLERANCE_LEVEL) {
                comparison = false;
            }
        } catch(Exception ie) {
            ie.printStackTrace();
            comparison = false;
        }
        return comparison;
    }
}
