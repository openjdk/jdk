/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
 * @key headful
 * @bug 8275715
 * @summary Tests that paint method is not called twice
 * @run main/othervm MultiPaintEventTest
 */

import java.awt.*;

public class MultiPaintEventTest extends Canvas {

    private int count = 0;
    private final Object lock = new Object();

    public void paint(Graphics g) {
        synchronized(lock) {
            count++;
        }

        int w = getWidth();
        int h = getHeight();

        Graphics2D g2d = (Graphics2D)g;
        if (count % 2 == 1) {
            g2d.setColor(Color.green);
        } else {
            g2d.setColor(Color.red);
        }
        g2d.fillRect(0, 0, w, h);
    }

    public int getCount() {
        synchronized(lock) {
            return count;
        }
    }

    public Dimension getPreferredSize() {
        return new Dimension(400, 400);
    }

    public static void main(String[] args) {
        MultiPaintEventTest test = new MultiPaintEventTest();
        Frame frame = new Frame();
        frame.setUndecorated(true);
        frame.add(test);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        try {
            Thread.sleep(2000);
            if (test.getCount() > 1) {
                throw new RuntimeException("Processed unnecessary paint().");
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException("Failed: Interrupted");
        } finally {
            frame.dispose();
        }
    }
}
