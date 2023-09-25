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
  @bug 4653170
  @summary Make sure setCursor does not produce Arithmetic Exception.
  @key headful
  @run main SingleColorCursorTest
*/

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Cursor;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;

public class SingleColorCursorTest extends Panel {
    public void init() {
        setLayout (new BorderLayout());
        setSize (200,200);
        add(new Button("JButton"));
    }

    public void start () {
        Cursor singleColorCursor = Toolkit.getDefaultToolkit()
                .createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_BINARY),
                                    new Point(0,0), "Single Color Cursor");
        try {
            setCursor(singleColorCursor);
        } catch (ArithmeticException ae) {
            throw new RuntimeException("Setting a 1x1 custom cursor causes arithmetic exception");
        }
    }

    public static void main(String[] args) throws InterruptedException, InvocationTargetException {
        EventQueue.invokeAndWait(() -> {
            Frame frame = new Frame("Test window");
            try {
                SingleColorCursorTest test = new SingleColorCursorTest();
                test.init();
                frame.add(test);
                frame.pack();
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                test.start();
                frame.setVisible(false);
            } finally {
                frame.dispose();
            }
        });
    }
}
