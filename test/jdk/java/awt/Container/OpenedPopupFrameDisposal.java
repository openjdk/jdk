/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4852790
  @summary Frame disposal must remove opened popup without exception
  @key headful
*/

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JMenuBar;
import javax.swing.JPanel;


public class OpenedPopupFrameDisposal {
    public static final int SIZE = 300;

    volatile JFrame jf = null;
    volatile JComboBox<String> jcb = null;

    public void start() {
        jf = new JFrame("OpenedPopupFrameDisposal - Frame to dispose");
        // Note that original bug cannot be reproduced without JMenuBar present.
        jf.setJMenuBar(new JMenuBar());
        jf.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        jf.setLocationRelativeTo(null);
        jf.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent evt) {
                jf.setVisible(false);
                jf.dispose();
            }
        });


        JPanel panel = new JPanel(new FlowLayout());
        jcb = new JComboBox<>();
        jcb.addItem("one");
        jcb.addItem("two");
        jcb.addItem("Three");
        panel.add(jcb);

        jf.getContentPane().add(panel, BorderLayout.CENTER);
        jf.pack();
        jf.setSize(new Dimension(SIZE, SIZE));

        jf.setVisible(true);

    }

    public void test() throws Exception {
        Robot robot  = new Robot();
        robot.delay(1000); // wait for jf visible
        Point pt = jf.getLocationOnScreen();

        int x, y;

        x = pt.x + SIZE / 2;
        y = pt.y + SIZE / 2;

        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);

        pt = jcb.getLocationOnScreen();
        x = pt.x + jcb.getWidth() / 2;
        y = pt.y + jcb.getHeight() / 2;

        robot.mouseMove(x, y);
        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.delay(1000);

        // Here on disposal we had a NullPointerException
        EventQueue.invokeAndWait(() -> {
            if (jf != null) {
                jf.setVisible(false);
                jf.dispose();
            }
        });
    }

    public static void main(String[] args) throws Exception {
        OpenedPopupFrameDisposal imt = new OpenedPopupFrameDisposal();
        try {
            EventQueue.invokeAndWait(imt::start);
            imt.test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (imt.jf != null) {
                    imt.jf.dispose();
                }
            });
        }
    }
}
