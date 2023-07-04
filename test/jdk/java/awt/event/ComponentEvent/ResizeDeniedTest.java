/*
 * Copyright (c) 2002, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4523758
  @requires (os.family == "windows")
  @summary Checks denied setBounds doesn't generate ComponentEvent
  @key headful
  @run main ResizeDeniedTest
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.lang.reflect.InvocationTargetException;

public class ResizeDeniedTest implements ComponentListener {
    static int runs = 0;
    static Frame frame;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {

        ResizeDeniedTest resizeDeniedTest = new ResizeDeniedTest();
        EventQueue.invokeAndWait(() -> {
            frame = new Frame("ResizeDeniedTest");
            frame.addComponentListener(resizeDeniedTest);
            frame.setSize(1, 1);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });

        synchronized(resizeDeniedTest) {
            resizeDeniedTest.wait(2000);
        }

        if (frame != null) {
            EventQueue.invokeAndWait(() -> frame.dispose());
        }

        if (runs > 10) {
            System.out.println("Infinite loop");
            throw new RuntimeException("Infinite loop");
        }
    }

    public void componentHidden(ComponentEvent e) {}

    public void componentMoved(ComponentEvent e) {}

    public void componentResized(ComponentEvent e) {
        frame.setSize(1, 1);
        System.out.println("Size " + frame.getSize());
        ++runs;
        if (runs > 10) {
            System.out.println("Infinite loop");
            synchronized(this) {
                this.notify();
            }
            throw new RuntimeException("Infinite loop");
        }
    }

    public void componentShown(ComponentEvent e) {}
}
