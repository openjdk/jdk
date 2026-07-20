/*
 * Copyright (c) 2000, 2025, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4087762
  @summary Sometimes click events are missing when you click the color components alternately.
  @key headful
  @library /test/jdk/java/awt/regtesthelpers
  @build Util
  @run main ClickEventsTest
*/

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Robot;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import test.java.awt.regtesthelpers.Util;

public class ClickEventsTest {
    static Frame frame;
    static ColorComponent redComponent;
    static ColorComponent blueComponent;
    static Robot robot;

    public static void main(String[] args) throws Exception {
        try {
            EventQueue.invokeAndWait(ClickEventsTest::createAndShowGUI);
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (frame != null) {
                    frame.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        robot = new Robot();
        robot.waitForIdle();
        robot.delay(500);

        for (int i = 0; i < 10; i++) {
            redComponent.clickAndCheck();
            blueComponent.clickAndCheck();
        }
    }

    private static void createAndShowGUI() {
        frame = new Frame("ClickEventsTest");
        redComponent = new ColorComponent(Color.RED);
        blueComponent = new ColorComponent(Color.BLUE);

        frame.add("North", redComponent);
        frame.add("South", blueComponent);

        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    static class ColorComponent extends Component {
        public Color myColor;

        private final CyclicBarrier barrier = new CyclicBarrier(2);

        private final MouseAdapter mouseAdapter = new MouseAdapter() {
            public void mouseClicked(MouseEvent event) {
                System.out.println(myColor + " area clicked");
                try {
                    barrier.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
                    throw new RuntimeException(e);
                }
            }
        };

        public ColorComponent(Color c) {
            myColor = c;
            addMouseListener(mouseAdapter);
        }

        public Dimension getPreferredSize() {
            return new Dimension(200, 100);
        }

        public void paint(Graphics g) {
            g.setColor(myColor);
            g.fillRect(0, 0, 200, 100);
        }

        public void clickAndCheck() throws InterruptedException, BrokenBarrierException {
            barrier.reset();
            Util.clickOnComp(this, robot);
            try {
                barrier.await(1, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                throw new RuntimeException(myColor + " was not clicked");
            }
        }
    }
}
