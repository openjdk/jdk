/*
 * Copyright (c) 2000, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4371134
 * @key headful
 * @summary displays an animating fps (frames per second)
 *  counter.  When the window is dragged from monitor to monitor,
 *  the speed of the animation should not change too greatly.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultimonVImage
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JFrame;
import javax.swing.JViewport;

public class MultimonVImage {
    private static final String instructionsText =
            "This test should be run on any Windows platform that\n" +
            "supports multiple monitors.\n" +
            "You will see an animating fps (frames per second) counter at\n" +
            "the bottom of the window.  Drag the window into the other monitor\n" +
            "and that counter should not change drastically.  If the counter\n" +
            "is much lower on one monitor than the other (barring situations\n" +
            "described below) then the back buffer may not be accelerated\n" +
            "on the second monitor and the test fails.\n" +
            "Situations in which performance will differ even though there\n" +
            "is acceleration on both monitors include:\n" +
            "  - different bit depths on each monitor.  The higher the bits\n" +
            "    per pixel, the more data to push and the lower the fps number.\n" +
            "    Set the bit depths to be the same on both monitors to work\n" +
            "    around this issue.\n" +
            "  - the amount of acceleration available on each video card differs,\n" +
            "    so if your system uses different video cards then you should\n" +
            "    expect some difference between the cards.  To work around this\n" +
            "    issue, try to use the same or similar video cards for each monitor.";

    public static void main(String[] args) throws Exception {
        PassFailJFrame.builder()
                .title("MultimonVImage Instructions")
                .instructions(instructionsText)
                .testTimeOut(5)
                .rows(25)
                .columns(50)
                .testUI(() -> {
                    AnimatingFrame af = new AnimatingFrame();
                    af.test();
                    af.run();
                    return af;
                })
                .build()
                .awaitAndCheck();
    }
}

class FrameCounter {

    String fpsString = "Calculating...";
    long startTime, endTime;
    int numFrames;

    public FrameCounter() {
        startTime = System.currentTimeMillis();
    }

    public String addFrame() {
        ++numFrames;
        return calculateFPS();
    }

    String calculateFPS() {
        endTime = System.currentTimeMillis();
        double seconds = ((double) endTime - (double) startTime) / 1000;
        if (seconds > 1) {
            int fps = (int) (numFrames / seconds);
            fpsString = fps + " fps";
            startTime = endTime;
            numFrames = 0;
        }
        return fpsString;
    }
}

class AnimatingComponent extends JViewport {

    FrameCounter frameCounter;
    int boxX, boxY;
    int boxW, boxH;
    int xStep = 1;

    public AnimatingComponent() {
        frameCounter = new FrameCounter();
        boxX = 0;
        boxY = 0;
        boxW = 100;
        boxH = 100;
    }

    public void paintComponent(Graphics g) {
        boxX += xStep;
        if (boxX <= 0 || (boxX + boxW) > getWidth()) {
            xStep = -xStep;
            boxX += (2 * xStep);
        }
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        g.setColor(Color.green);
        for (int i = 0; i < 100; ++i) {
            g.fillRect(boxX, boxY, 100, 100);
        }
        g.setColor(Color.black);
        g.drawString(frameCounter.addFrame(), 200, getHeight() - 30);
    }
}

class AnimatingFrame extends JFrame implements Runnable {
    JViewport component;
    Thread thread;

    public AnimatingFrame() {
        setSize(500, 500);
        setTitle("MultimonVImage Demo");
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        component = new AnimatingComponent();
        component.setPreferredSize(new Dimension(500, 500));
        setContentPane(component);
        component.setVisible(true);

        pack();
    }

    public void test() {
        thread = new Thread(this);
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.start();
    }

    public void run() {
        Thread me = Thread.currentThread();
        while (thread == me) {
            component.repaint();
        }
    }
}

