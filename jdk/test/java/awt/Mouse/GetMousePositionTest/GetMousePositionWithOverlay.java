/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @test
 * @bug 8012026
 * @summary Component.getMousePosition() does not work in an applet on MacOS
 * @author Petr Pchelko
 * @library ../../regtesthelpers
 * @build Util
 * @compile GetMousePositionWithOverlay.java
 * @run main/othervm GetMousePositionWithOverlay
 */

public class GetMousePositionWithOverlay {

    static Frame backFrame;
    static Frame frontFrame;

    public static void main(String[] args) throws Throwable {
        try {
            SwingUtilities.invokeAndWait(new Runnable() {
                @Override
                public void run() {
                    constructTestUI();
                }
            });
            Util.waitForIdle(null);

            Robot r = new Robot();
            Util.pointOnComp(frontFrame, r);
            Util.waitForIdle(null);

            Point pos = getMousePosition(backFrame);
            if (pos != null) {
                throw new RuntimeException("Test failed. Mouse position should be null but was" + pos);
            }

            pos = getMousePosition(frontFrame);
            if (pos == null) {
                throw new RuntimeException("Test failed. Mouse position should not be null");
            }

            r.mouseMove(189, 189);
            Util.waitForIdle(null);

            pos = getMousePosition(backFrame);
            if (pos == null) {
                throw new RuntimeException("Test failed. Mouse position should not be null");
            }
        } finally {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    backFrame.dispose();
                    frontFrame.dispose();
                }
            });
        }
    }

    private static Point getMousePosition(final Component component) throws Exception {
        final AtomicReference<Point> pos = new AtomicReference<Point>();
        SwingUtilities.invokeAndWait(new Runnable() {
            @Override
            public void run() {
                pos.set(component.getMousePosition());
            }
        });
        return pos.get();
    }

    private static void constructTestUI() {
        backFrame = new Frame();
        backFrame.setBounds(100, 100, 100, 100);
        backFrame.setVisible(true);

        frontFrame = new Frame();
        frontFrame.setBounds(120, 120, 60, 60);
        frontFrame.setVisible(true);
    }
}
