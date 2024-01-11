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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.Robot;

import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.KEY_TEXT_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;
import static java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON;

/*
 * @test
 * @bug 4357180
 * @key headful
 * @summary When both KEY_ANTIALIASING and KEY_TEXT_ANTIALIASING hints
 *          were turned on, java aborts with EXCEPTION_ACCESS_VIOLATION
 *          at attempt to draw characters in Hebrew or Arabic.
 *          This could happen immediately or after several draws,
 *          depending on th locale and platform. This test draws
 *          large number of characters that are among this range repeatedly.
 */

public class DoubleAntialiasTest extends Panel {
    private static Frame frame;

    public static void main(String[] args) throws Exception {
        try {
            Robot robot = new Robot();
            EventQueue.invokeAndWait(() -> {
                frame = new Frame();
                frame.setTitle("DoubleAntialiasTest");
                frame.add(new DoubleAntialiasTest());
                frame.pack();
                frame.setSize(500, 500);
                frame.setVisible(true);
            });

            robot.waitForIdle();
            robot.delay(2000);
        } catch (Exception e) {
            throw new RuntimeException("Following exception occurred" +
                    " when testing Antialiasing Rendering hints: ", e);
        } finally {
            EventQueue.invokeAndWait(() -> {
                 if (frame != null) {
                     frame.dispose();
                 }
            });
        }
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        int y = 50;
        for (int i = 0; i < 2; i++) {
            int k = 5;
            for (int j = 0x500; j < 0x700; j++) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING,
                                    VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(KEY_ANTIALIASING,
                                    VALUE_ANTIALIAS_ON);
                g2.drawString(String.valueOf((char) j), (5 + k), y);
                k = k + 15;
            }
            k = 5;
            y += 50;
            for (int j = 0x700; j > 0x500; j--) {
                g2.setRenderingHint(KEY_TEXT_ANTIALIASING,
                                    VALUE_TEXT_ANTIALIAS_ON);
                g2.setRenderingHint(KEY_ANTIALIASING,
                                    VALUE_ANTIALIAS_ON);
                g2.drawString(String.valueOf((char) j), (5 + k), y);
                k = k + 15;
            }
            y += 50;
        }
    }
}
