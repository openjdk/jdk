/*
 * Copyright (c) 2013, 2021, Oracle and/or its affiliates. All rights reserved.
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

import test.java.awt.regtesthelpers.Util;

import javax.swing.SwingUtilities;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

/**
 * @test
 * @key headful
 * @bug 8012026 8027154
 * @summary Component.getMousePosition() does not work in an applet on MacOS
 * @library ../../regtesthelpers
 * @build Util
 * @compile GetMousePositionWithPopup.java
 * @run main GetMousePositionWithPopup
 */

public class GetMousePositionWithPopup {

    private static Frame frame1;
    private static Frame frame2;

    public static void main(String[] args) throws Exception {
        try {
        Robot r = Util.createRobot();
        r.setAutoDelay(100);
        r.mouseMove(0, 0);
        Util.waitForIdle(null);

        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                constructTestUI();
            }
        });

        Util.waitForIdle(null);
        r.mouseMove(149, 149);
        Util.waitForIdle(null);
        r.mouseMove(170, 170);
        Util.waitForIdle(null);

        } finally {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    frame1.dispose();
                    frame2.dispose();
                }
            });
        }
    }

    private static void constructTestUI() {
        frame1 = new Frame();
        frame1.setBounds(100, 100, 100, 100);
        frame1.setVisible(true);
        frame1.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                if (frame2 != null) {
                    return;
                }
                frame2 = new Frame();
                frame2.setBounds(120, 120, 120, 120);
                frame2.setVisible(true);
                frame2.addMouseMotionListener(new MouseMotionAdapter() {
                    @Override
                    public void mouseMoved(MouseEvent e)
                    {
                        Point positionInFrame2 = frame2.getMousePosition();
                        int deltaX = Math.abs(50 - positionInFrame2.x);
                        int deltaY = Math.abs(50 - positionInFrame2.y);
                        if (deltaX > 2 || deltaY > 2) {
                            throw new RuntimeException("Wrong position reported. Should be [50, 50] but was [" +
                                    positionInFrame2.x + ", " + positionInFrame2.y + "]");
                        }

                        Point positionInFrame1 = frame1.getMousePosition();
                        if (positionInFrame1 != null) {
                            throw new RuntimeException("Wrong position reported. Should be null");
                        }
                    }
                });
            }
        });
    }
}
