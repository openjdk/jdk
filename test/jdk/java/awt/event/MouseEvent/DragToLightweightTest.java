/*
 * Copyright (c) 2001, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4417964
 * @summary tests that drag events continue to arrive to heavyweight
 *          when the mouse is moved to lightweight while dragging.
 * @key headful
 * @library /lib/client /java/awt/regtesthelpers
 * @build ExtendedRobot Util
 * @run main DragToLightweightTest
*/

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.HeadlessException;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import test.java.awt.regtesthelpers.Util;

public class DragToLightweightTest {

    private static final CountDownLatch latch = new CountDownLatch(1);
    private static volatile MouseTest mouseTest;

    public static void main(String[] args) throws Exception {

        EventQueue.invokeAndWait(() -> mouseTest = new MouseTest());

        try {
            test();
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (mouseTest != null) {
                    mouseTest.dispose();
                }
            });
        }
    }

    private static void test() throws Exception {
        ExtendedRobot robot = new ExtendedRobot();
        robot.waitForIdle();
        robot.delay(500);

        Rectangle componentBounds = mouseTest.getLightweightComponentBounds();

        robot.dragAndDrop(
                componentBounds.x + componentBounds.width / 2, componentBounds.y + componentBounds.height + 30,
                componentBounds.x + componentBounds.width / 2, componentBounds.y + 2 * componentBounds.height / 3
        );

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new RuntimeException("The test failed: no mouse release event received");
        }

        System.out.println("Mouse release event received, the test PASSED");
    }

    private static class MouseTest extends Frame {

        final Foo foo;

        public MouseTest() throws HeadlessException {
            super("DragToLightweightTest");

            setLayout(new FlowLayout());

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseReleased(MouseEvent e) {
                    System.out.println("mouseReleased");
                    latch.countDown();
                }
            });

            // Create a Component that will be a child of the Frame and add
            // a MouseListener to it.
            foo = new Foo();
            foo.setBackground(Color.red);

            System.out.println(foo.getPreferredSize());
            foo.setPreferredSize(new Dimension(350, 200));
            System.out.println(foo.getPreferredSize());

            foo.addMouseListener(new DummyAdapter());

            add(foo);

            setSize(400, 400);
            setLocationRelativeTo(null);
            setVisible(true);
        }

        public Rectangle getLightweightComponentBounds() throws Exception {
            return Util.invokeOnEDT(() -> {
                Point locationOnScreen = foo.getLocationOnScreen();
                Dimension size = foo.getSize();
                return new Rectangle(locationOnScreen.x, locationOnScreen.y, size.width, size.height);
            });
        }

        private static class Foo extends Container {
            public void paint(Graphics g) {
                g.setColor(getBackground());
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.white);
                g.drawString(getBounds().toString(), 5, 20);
                super.paint(g);
            }
        }

        private static class DummyAdapter extends MouseAdapter {}
    }
}
