/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
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
   @bug 8041470
   @summary JButtons stay pressed after they have lost focus if you use the mouse wheel
 */

import java.awt.AWTEvent;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class WheelModifier {

    JFrame f;
    JButton fb;

    CountDownLatch pressSema = new CountDownLatch(1);
    CountDownLatch exitSema = new CountDownLatch(1);
    CountDownLatch releaseSema = new CountDownLatch(1);
    volatile CountDownLatch wheelSema;

    private volatile Point sLoc;
    private volatile Dimension bSize;

    void createGui() {
        f = new JFrame("frame");
        fb = new JButton("frame_button");
        fb.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseReleased(MouseEvent e) {
                System.out.println("WheelModifier.mouseReleased: " + e);
                releaseSema.countDown();
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                System.out.println("WheelModifier.mouseEntered: " + e);

            }

            @Override
            public void mouseExited(MouseEvent e) {
                System.out.println("WheelModifier.mouseExited: " + e);
                exitSema.countDown();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                System.out.println("WheelModifier.mousePressed: " + e);
                pressSema.countDown();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                System.out.println("WheelModifier.mouseDragged: " + e);
            }
        });

        Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
            @Override
            public void eventDispatched(AWTEvent event) {
                System.out.println("WheelModifier.mouseWheel: " + event);
                wheelSema.countDown();
            }
        }, MouseEvent.MOUSE_WHEEL_EVENT_MASK);

        f.setLayout(new FlowLayout());
        f.add(fb);
        f.setSize(200, 200);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.setVisible(true);
    }

    void run() throws Exception {
        Robot r = new Robot();
        r.waitForIdle();
        System.out.println("# Started");

        SwingUtilities.invokeAndWait(() -> {
            sLoc = fb.getLocationOnScreen();
            bSize = fb.getSize();
        });

        r.mouseMove(sLoc.x + bSize.width / 2, sLoc.y + bSize.height / 2);
        r.mousePress(MouseEvent.BUTTON1_DOWN_MASK);
        pressSema.await();
        System.out.println("# Pressed");

        r.mouseMove(sLoc.x + bSize.width / 2, sLoc.y + bSize.height * 2);
        exitSema.await();
        System.out.println("# Exited");

        wheelSema = new CountDownLatch(1);
        r.mouseWheel(1);
        wheelSema.await();
        System.out.println("# Wheeled 1");

        wheelSema = new CountDownLatch(1);
        r.mouseWheel(-1);
        wheelSema.await();
        System.out.println("# Wheeled 2");

        r.mouseRelease(MouseEvent.BUTTON1_DOWN_MASK);
        releaseSema.await();
        System.out.println("# Released!");
    }

    public static void main(String[] args) throws Exception {
        WheelModifier test = new WheelModifier();

        SwingUtilities.invokeAndWait(() -> test.createGui());
        test.run();

        System.out.println("Done.");
    }
}
