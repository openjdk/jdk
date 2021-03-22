/*
 * Copyright (c) 2012, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class MyMacCanvas extends Canvas {

    static {
        try {
            System.loadLibrary("mylib");
        } catch (Throwable t) {
            System.out.println("Test failed!!");
            t.printStackTrace();
            System.exit(1);
        }
    }

    public void addNotify() {
        super.addNotify();
        addNativeCoreAnimationLayer();
    }

    public native void addNativeCoreAnimationLayer();

    static JAWTFrame f;
    public static void main(String[] args) {
        try {
            Robot robot = new Robot();
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    f = new JAWTFrame("JAWTExample");
                    f.setBackground(Color.white);
                    f.setLayout(new BorderLayout(10, 20));
                    f.setLocation(50, 50);
                    ((JComponent) f.getContentPane()).setBorder(BorderFactory.createMatteBorder(10, 10, 10, 10, Color.cyan));
                    f.addNotify();
                    f.pack();
                    f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    f.setVisible(true);
                }
            });
            robot.delay(5000);
            Color col1 = new Color(0, 0, 0);
            Color col2 = robot.getPixelColor(f.getX()+50, f.getY()+50);
            if (col1.equals(col2)) {
                System.out.println("Test passed!");
            } else {
                System.out.println("col1 " + col1 + " col2 " + col2);
                throw new RuntimeException("Color of JAWT canvas is wrong or " +
                        "it was not rendered. " + "Check that other windows " +
                        "do not block the test frame.");
            }
            System.exit(0);
        } catch (Throwable t) {
            System.out.println("Test failed!");
            t.printStackTrace();
            System.exit(1);
        }
    }

    private static class JAWTFrame extends JFrame {
        public JAWTFrame(final String title) {
            super(title);
        }

        public void addNotify() {
            super.addNotify(); // ensures native component hierarchy is setup

            final Component layerBackedCanvas = new MyMacCanvas();
            layerBackedCanvas.setPreferredSize(new Dimension(400, 200));
            add(layerBackedCanvas, BorderLayout.CENTER);

            invalidate();
        }
    }
}
