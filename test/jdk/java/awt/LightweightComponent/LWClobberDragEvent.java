/*
 * Copyright (c) 1998, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4092418
  @summary Test for drag events been taking by Lightweight Component
  @key headful
  @run main LWClobberDragEvent
*/

import java.awt.AWTException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

public class LWClobberDragEvent implements MouseListener, MouseMotionListener {
    boolean isDragging;

    static Frame frame;
    LightweightComp lc;
    final static int LWWidth = 200;
    final static int LWHeight = 100;
    final static int MAX_COUNT = 100;
    Point locationOfLW;

    public static void main(String[] args) throws Exception {
        LWClobberDragEvent test = new LWClobberDragEvent();
        try {
            test.init();
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void init() throws Exception {
        EventQueue.invokeAndWait(() -> {
            frame = new Frame();
            frame.setLayout(new BorderLayout());
            isDragging = false;
            frame.addMouseMotionListener(this);
            frame.addMouseListener(this);

            frame.setBackground(Color.white);

            lc = new LightweightComp();
            lc.setSize(LWWidth, LWHeight);
            lc.setLocation(50, 50);
            lc.addMouseListener(this);
            lc.addMouseMotionListener(this);
            frame.add(lc);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public void start() throws Exception {
        Robot robot = new Robot();
        robot.delay(1000);
        robot.waitForIdle();

        EventQueue.invokeAndWait(() -> {
            locationOfLW = getLocation(lc);
            robot.mouseMove(locationOfLW.x + lc.getWidth() / 2,
                    locationOfLW.y - lc.getHeight() / 2);
        });

        robot.mousePress(InputEvent.BUTTON1_MASK);
        robot.delay(1000);
        //move mouse till the bottom of LWComponent
        for (int i = 1; i < LWHeight + lc.getHeight() / 2; i++) {
            robot.mouseMove(locationOfLW.x + lc.getWidth() / 2,
                    locationOfLW.y - lc.getHeight() / 2 + i);
        }
        robot.mouseRelease(InputEvent.BUTTON1_MASK);
        robot.delay(1000);
        System.out.println("Test Passed.");
    }

    public void mouseClicked(MouseEvent evt) { }

    public void mouseReleased(MouseEvent evt) {
        if (evt.getSource() == this) {
            if (isDragging) {
                isDragging = false;
            }
        } else {
        }
    }
    public Point getLocation( Component co ) throws RuntimeException {
       Point pt = null;
       boolean bFound = false;
       int count = 0;
       while ( !bFound ) {
          try {
             pt = co.getLocationOnScreen();
             bFound = true;
          } catch ( Exception ex ) {
             bFound = false;
             count++;
          }
          if ( !bFound && count > MAX_COUNT ) {
             throw new RuntimeException("don't see a component to get location");
          }
       }
       return pt;
    }

    public void mousePressed(MouseEvent evt) {    }
    public void mouseEntered(MouseEvent evt) {    }
    public void mouseExited(MouseEvent evt) {    }
    public void mouseMoved(MouseEvent evt) {    }

    public void mouseDragged(MouseEvent evt) {
        if (evt.getSource() == this) {
            if (!isDragging) {
                isDragging = true;
            }
        } else {
            if (isDragging) {
                throw new RuntimeException("Test failed: Lightweight got dragging event.");
            }
        }
    }
}

class LightweightComp extends Component {
    public void paint(Graphics g) {
        Dimension d = getSize();
        g.setColor(Color.red);
        g.fillRect(0, 0, d.width, d.height);
    }
}
