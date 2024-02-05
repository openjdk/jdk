/*
 * Copyright (c) 2001, 2023, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Robot;
import java.util.concurrent.atomic.AtomicInteger;

/*
 * @test
 * @bug 4394287
 * @key headful
 * @summary Paint pending on heavyweight component move
 */

public class RepaintTest {
    private static Frame frame;
    private static Panel panel;
    private static volatile IncrementComponent counter;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(RepaintTest::createAndShowUI);
            robot.waitForIdle();
            robot.delay(1000);

            EventQueue.invokeAndWait(() -> panel.setLocation(panel.getX() + 10,
                                                             panel.getY() + 10));
            robot.waitForIdle();
            robot.delay(500);

            int count = counter.getCount().get();

            EventQueue.invokeAndWait(panel::repaint);
            robot.waitForIdle();
            robot.delay(1000);

            if (counter.getCount().get() == count) {
                throw new RuntimeException("Failed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void createAndShowUI() {
        frame = new MyFrame("Repaint Test");
        frame.setLayout(null);

        counter = new IncrementComponent();
        panel = new Panel();
        panel.add(counter);
        frame.add(panel);
        panel.setBounds(0, 0, 100, 100);
        frame.setSize(200, 200);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static class MyFrame extends Frame {

         public MyFrame(String title) {
            super(title);
        }

        public void update(Graphics g) {
            System.out.println("UPDATE: " + g.getClipBounds());
            super.update(g);
        }

        public void paint(Graphics g) {
            System.out.println("PAINT: " + g.getClipBounds());
            super.paint(g);
        }
    }

    // Subclass of Component, everytime paint is invoked a counter
    // is incremented, this counter is displayed in the component.
    private static class IncrementComponent extends Component {
        private static final AtomicInteger paintCount = new AtomicInteger(0);

        public Dimension getPreferredSize() {
            return new Dimension(100, 100);
        }

        public AtomicInteger getCount() {
            return paintCount;
        }

        public void paint(Graphics g) {
            g.setColor(Color.red);
            g.fillRect(0, 0, getWidth(), getHeight());
            g.setColor(Color.white);

            String string = Integer.toString(paintCount.getAndIncrement());
            FontMetrics metrics = g.getFontMetrics();
            int x = (getWidth() - metrics.stringWidth(string)) / 2;
            int y = (getHeight() + metrics.getHeight()) / 2;
            g.drawString(string, x, y);
        }
    }
}
