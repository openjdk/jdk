/*
 * Copyright (c) 2013, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Label;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import javax.swing.JFrame;

/*
 * @test
 * @bug 8028212
 * @summary [macosx] Custom Cursor HiDPI support
 * @requires (os.family == "mac")
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual MultiResolutionCursorTest
 */
public class MultiResolutionCursorTest {
    static final int sizes[] = {8, 16, 32, 128};

    private static JFrame initialize() {
        final Image image = new BaseMultiResolutionImage(
                createResolutionVariant(0),
                createResolutionVariant(1),
                createResolutionVariant(2),
                createResolutionVariant(3)
        );

        int center = sizes[0] / 2;
        Cursor cursor = Toolkit.getDefaultToolkit().createCustomCursor(
                image, new Point(center, center), "multi-resolution cursor");

        JFrame frame = new JFrame("Multi-resolution Cursor Test Frame");
        frame.setSize(300, 300);
        frame.add(new Label("Move cursor here"));
        frame.setCursor(cursor);
        return frame;
    }

    private static BufferedImage createResolutionVariant(int i) {
        BufferedImage resolutionVariant = new BufferedImage(sizes[i], sizes[i],
                BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = resolutionVariant.createGraphics();
        Color colors[] = {Color.WHITE, Color.RED, Color.GREEN, Color.BLUE};
        g2.setColor(colors[i]);
        g2.fillRect(0, 0, sizes[i], sizes[i]);
        g2.dispose();
        return resolutionVariant;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        String instructions = """
                   Verify that high resolution custom cursor is used
                   on HiDPI displays.
                   1) Run the test on Retina display or enable the Quartz Debug
                      and select the screen resolution with (HiDPI) label
                   2) Move the cursor to the Test Frame
                   3) Check that cursor has red, green or blue color
                   If so, press Pass, else press Fail.
                   """;

        PassFailJFrame.builder()
                .title("Multi-resolution Cursor Test Instructions")
                .instructions(instructions)
                .rows((int) instructions.lines().count() + 1)
                .columns(40)
                .testUI(MultiResolutionCursorTest::initialize)
                .build()
                .awaitAndCheck();
    }
}
