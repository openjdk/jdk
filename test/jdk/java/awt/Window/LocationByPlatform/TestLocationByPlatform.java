/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6318630
 * @summary Test that location by platform works
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestLocationByPlatform
 */

import java.awt.BorderLayout;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Graphics;

public class TestLocationByPlatform {
    public static void main(String[] args) throws Exception {
        String INSTRUCTIONS = """
            You should see two frames. One has locationByPlatform set, it
            should be displayed somewhere on the screen most probably without
            intersecting other Frames or stacked over normal frame with some
            offset. Another has its location explicitly set to (0, 450).
            Please verify that the frames are located correctly on the screen.

            Also verify that the picture inside of frames looks the same
            and consists of red descending triangle occupying exactly the bottom
            half of the frame. Make sure that there is a blue rectangle exactly
            surrounding the client area of frame with no pixels between it and
            the frame's decorations. Press Pass if this all is true,
            otherwise press Fail.
            """;

        PassFailJFrame passFailJFrame = PassFailJFrame.builder()
            .title("Test Instructions")
            .instructions(INSTRUCTIONS)
            .rows(13)
            .columns(40)
            .build();
        EventQueue.invokeAndWait(TestLocationByPlatform::createUI);
        passFailJFrame.awaitAndCheck();
    }
    private static void createUI() {
        Frame frame = new Frame("Normal");
        frame.setLocation(0, 450);
        Canvas c = new MyCanvas();
        frame.add(c, BorderLayout.CENTER);
        frame.pack();
        PassFailJFrame.addTestWindow(frame);
        frame.setVisible(true);

        frame = new Frame("Location by platform");
        frame.setLocationByPlatform(true);
        c = new MyCanvas();
        frame.add(c, BorderLayout.CENTER);
        frame.pack();
        PassFailJFrame.addTestWindow(frame);
        frame.setVisible(true);
    }

    static class MyCanvas extends Canvas {
        @Override
        public Dimension getPreferredSize() {
            return new Dimension(400, 400);
        }

        @Override
        public void paint(Graphics g) {
            g.setColor(Color.red);
            for (int i = 399; i >= 0; i--) {
                g.drawLine(400 - i - 1, 400 - i - 1,
                    400 - i - 1, 399);
            }
            g.setColor(Color.blue);
            g.drawLine(0, 0, 399, 0);
            g.drawLine(0, 0, 0, 399);
            g.drawLine(0, 399, 399, 399);
            g.drawLine(399, 0, 399, 399);
        }
    }
}
