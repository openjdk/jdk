/*
 * Copyright (c) 1999, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4050138
  @summary Lightweight components: Enter/Exit mouse events
  incorrectly reported during drag
  @key headful
  @run main LightweightDragTest
*/

import java.awt.AWTException;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

public class LightweightDragTest {
    MyComponent c,c2;
    static Frame frame;
    volatile int x = 0;
    volatile int y = 0;
    volatile int x2 = 0;
    volatile int y2 = 0;

    public static void main(String[] args) throws Exception {
        LightweightDragTest test = new LightweightDragTest();
        try {
            EventQueue.invokeAndWait(() -> {
                test.init();
            });
            test.start();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    public void init() {
        frame = new Frame("Test LightWeight Component Drag");
        c = new MyComponent();
        c2 = new MyComponent();
        frame.add(c, BorderLayout.WEST);
        frame.add(c2, BorderLayout.EAST);
        frame.setSize(250, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

   public void start() throws Exception {
       Robot rb;
       try {
           rb = new Robot();
       } catch (AWTException e) {
           throw new Error("Could not create robot");
       }
       rb.setAutoDelay(10);
       rb.delay(1000);
       rb.waitForIdle();

       EventQueue.invokeAndWait(() -> {
           x = c.getLocationOnScreen().x + c.getWidth() / 2;
           y = c.getLocationOnScreen().y + c.getHeight() / 2;
           x2 = c2.getLocationOnScreen().x + c2.getWidth() / 2;
           y2 = c2.getLocationOnScreen().y + c2.getHeight() / 2;
       });
       int xt = x;
       int yt = y;
       rb.mouseMove(xt, yt);
       rb.mousePress(InputEvent.BUTTON1_MASK);
       EventQueue.invokeAndWait(() -> {
           c.isInside = true;
           c2.isInside = false;
       });
       // drag
       while (xt != x2 || yt != y2) {
           if (x2 > xt) ++xt;
           if (x2 < xt) --xt;
           if (y2 > yt) ++yt;
           if (y2 < yt) --yt;
           rb.mouseMove(xt, yt);
       }
       rb.mouseRelease(InputEvent.BUTTON1_MASK);
       EventQueue.invokeAndWait(() -> {
           if (c.isInside || !c2.isInside) {
               throw new Error("Test failed: mouse events did not arrive");
           }
       });
   }
}

class MyComponent extends Component {
    public boolean isInside;
    public void paint(Graphics g) {
        g.setColor(getBackground());
        g.fillRect(0,0,getSize().width,getSize().height);
    }
    public MyComponent() {
        enableEvents(AWTEvent.MOUSE_EVENT_MASK);
        setBackground(Color.blue);
    }

    public Dimension getPreferredSize() {
        return new Dimension(100, 100);
    }

    public void processEvent(AWTEvent e) {
        int eventID = e.getID();
        if ((eventID == MouseEvent.MOUSE_ENTERED)) {
            setBackground(Color.red);
            repaint();
            isInside = true;
        } else if (eventID == MouseEvent.MOUSE_EXITED) {
            setBackground(Color.blue);
            repaint();
            isInside = false;
        }
        super.processEvent(e);
    }
}
