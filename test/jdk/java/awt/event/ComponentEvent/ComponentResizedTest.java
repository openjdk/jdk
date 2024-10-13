/*
 * Copyright (c) 2000, 2023, Oracle and/or its affiliates. All rights reserved.
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
  @bug 4267393
  @summary Ensures minimal amount of paints
  @key headful
  @run main ComponentResizedTest
*/

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.ComponentEvent;
import java.lang.reflect.InvocationTargetException;

public class ComponentResizedTest extends Frame {
    volatile int paintCount = 0;

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        ComponentResizedTest componentResizedTest = new ComponentResizedTest();
        EventQueue.invokeAndWait(componentResizedTest::init);
        componentResizedTest.start();
        if (componentResizedTest != null) EventQueue.invokeAndWait(()
                -> componentResizedTest.dispose());
    }

    public void paint(Graphics g) {
        System.out.println("Paint called");
        ++paintCount;
    }

    public void init() {
        setTitle("ComponentResizedTest");
        setSize(100, 100);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    public void start () throws InterruptedException {
        Thread.sleep(1000);

        paintCount = 0;
        dispatchEvent(new ComponentEvent(this, ComponentEvent.COMPONENT_RESIZED));

        Thread.sleep(1000);

        if (paintCount > 0) {
            throw new RuntimeException("ComponentResizedTest failed. " +
                    "Paint called.");
        }
    }
}
