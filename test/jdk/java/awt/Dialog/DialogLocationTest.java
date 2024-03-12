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
  @bug 4101437
  @summary Dialog.setLocation(int,int) works unstable when the dialog is visible
  @key headful
  @run main DialogLocationTest
*/

import java.awt.AWTException;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

public class DialogLocationTest extends Panel {
    private volatile int count = 0;
    private Dialog my_dialog;
    private volatile boolean waitingForEvent = false;
    private volatile int newX, newY;
    Random random = new Random();

    public void init() {
        Container f = getParent();

        while (!(f instanceof Frame)) {
            f = f.getParent();
        }

        my_dialog = new Dialog((Frame) f, "TestDialog");
        my_dialog.setSize(150, 100);

        setSize(200, 200);
    }

    public void start() throws InterruptedException,
            InvocationTargetException {
        Robot robot;
        try {
            robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                my_dialog.setLocationRelativeTo(null);
                my_dialog.setVisible(true);
            });
            robot.waitForIdle();
            robot.delay(1000);
            my_dialog.addComponentListener(new CL());
            setDialogLocation(my_dialog);
        } catch (AWTException e) {
            throw new RuntimeException(e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                my_dialog.setVisible(false);
                my_dialog.dispose();
            });
        }
    }

    public void setDialogLocation(Dialog dialog) {
        int height, width, insetX, insetY;
        Point curLoc;
        int i;

        Rectangle screen = GraphicsEnvironment
                .getLocalGraphicsEnvironment()
                .getMaximumWindowBounds();
        height = screen.height;
        width = screen.width;
        insetX = screen.x;
        insetY = screen.y;

        String message = "Failed on iteration %d expect:[%d,%d] "
                + "reported:[%d,%d] diff:[%d,%d]";

        for (i = 0; i < 100; i++) {
            newX = random.nextInt(insetX, width - 300);
            newY = random.nextInt(insetY, height - 400);

            if (newX == 0 && newY == 0) {
                i--;
                continue;
            }

            waitingForEvent = true;

            EventQueue.invokeLater(() -> {
                dialog.setLocation(newX, newY);
            });

            while (waitingForEvent) {
                Thread.yield();
            }

            curLoc = dialog.getLocation();
            if (curLoc.x != newX || curLoc.y != newY) {
                count++;
                System.out.println(message.formatted(i, newX, newY,
                        curLoc.x, curLoc.y, curLoc.x - newX, curLoc.y - newY));
                System.out.flush();
            }
        }

        if (count > 0) {
            throw new RuntimeException("Dialog Location was set incorrectly");
        }
    }

    public class CL extends ComponentAdapter {
        int lastX, lastY;
        String message = "Failed in componentMoved() expect:[%d,%d]"
                + " reported: [%d,%d] diff [%d,%d]";

        public void componentMoved(ComponentEvent e) {
            if (e.getComponent() == my_dialog) {
                Point eventLoc = e.getComponent().getLocation();
                if (lastX != eventLoc.x || lastY != eventLoc.y) {
                    lastX = eventLoc.x;
                    lastY = eventLoc.y;
                    if (newX != 0 && newY != 0 && (eventLoc.x != newX || eventLoc.y != newY)) {
                        count++;
                        System.out.println(message.formatted(newX, newY,
                                eventLoc.x, eventLoc.y,
                                eventLoc.x - newX, eventLoc.y - newY));
                        System.out.flush();
                    }
                    waitingForEvent = false;
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        Frame frame = new Frame("DialogLocationTest");
        try {
            DialogLocationTest test = new DialogLocationTest();
            EventQueue.invokeAndWait(() -> {
                frame.add(test);
                test.init();
                frame.setVisible(true);
            });
            test.start();
        } finally {
            EventQueue.invokeLater(() -> {
                frame.setVisible(false);
                frame.dispose();
            });
        }
    }
}

