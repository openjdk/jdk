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
  @bug 4274360
  @summary Ensures that Dialogs receive COMPONENT_SHOWN events
  @key headful
  @run main ComponentShownEvent
*/

import java.awt.AWTException;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Robot;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;

public class ComponentShownEvent {

    volatile boolean componentShown = false;
    Frame f;
    Dialog d;

    public void start() throws InterruptedException,
                        InvocationTargetException, AWTException {
        Robot robot = new Robot();
        try {
            EventQueue.invokeAndWait(() -> {
                f = new Frame();
                d = new Dialog(f);

                d.addComponentListener(new ComponentAdapter() {
                    public void componentShown(ComponentEvent e) {
                        componentShown = true;
                    }
                });

                f.setSize(100, 100);
                f.setLocationRelativeTo(null);
                f.setVisible(true);
                d.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(1000);

            if (!componentShown) {
                throw new RuntimeException("test failed");
            }
        } finally {
            EventQueue.invokeAndWait(() -> {
                if (d != null) {
                    d.setVisible(false);
                    d.dispose();
                }
                if (f != null) {
                    f.setVisible(false);
                    f.dispose();
                }
            });
        }
    }

    public static void main(String[] args) throws InterruptedException,
                               InvocationTargetException, AWTException {
        ComponentShownEvent test = new ComponentShownEvent();
        test.start();
        System.out.println("test passed");
    }
}
