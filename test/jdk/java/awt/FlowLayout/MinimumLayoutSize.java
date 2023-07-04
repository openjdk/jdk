/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 6257219
  @summary FlowLayout gives a wrong minimum size if the first component is hidden.
  @key headful
  @run main MinimumLayoutSize
*/


import java.awt.AWTException;
import java.awt.Button;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.LayoutManager;
import java.awt.Panel;
import java.awt.Robot;
import java.lang.reflect.InvocationTargetException;

public class MinimumLayoutSize {
    Frame frame;
    Button b1;
    Button b2;
    Panel panel;

    public void start() throws AWTException,
            InterruptedException, InvocationTargetException {
        try {
            Robot robot = new Robot();
            LayoutManager layout = new FlowLayout(FlowLayout.LEFT, 100, 0);
            final int[] widths = new int[2];
            EventQueue.invokeAndWait(() -> {
                frame = new Frame("MinimumLayoutSize");
                b1 = new Button("B1");
                b2 = new Button("B2");
                panel = new Panel();
                panel.add(b2);
                frame.add(panel);
                frame.pack();
                frame.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            //add hidden component b1
            EventQueue.invokeAndWait(() -> {
                widths[0] = layout.minimumLayoutSize(panel).width;
                b1.setVisible(false);
                panel.add(b1, 0);
            });
            robot.waitForIdle();
            robot.delay(1000);
            EventQueue.invokeAndWait(() -> {
                widths[1] = layout.minimumLayoutSize(panel).width;
                frame.setVisible(false);
            });
            System.out.println("TRACE: w1 = " + widths[0] + " w2 = " + widths[1]);

            if (widths[0] != widths[1]) {
                throw new RuntimeException("Test FAILED. Minimum sizes are not equal."
                        + " w1 = " + widths[0] + " w2 = " + widths[1]);
            }
        } finally {
            if (frame != null) {
                frame.dispose();
            }
        }
        System.out.println("Test passed");
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException, AWTException {
        MinimumLayoutSize test = new MinimumLayoutSize();
        test.start();
    }
}
