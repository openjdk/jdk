/*
 * Copyright (c) 2002, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Panel;
import java.awt.RenderingHints;
import java.awt.image.VolatileImage;
import java.lang.reflect.InvocationTargetException;

/*
 * @test
 * @bug 4505650
 * @summary Check that you can render solid text after doing XOR mode
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TextAfterXor
 */

public class TextAfterXor extends Panel {
    public static final int TESTW = 300;
    public static final int TESTH = 100;
    static String INSTRUCTIONS = """
            In the window called "Text After XOR Test" there should be two
            composite components, at the bottom of each component the green text
            "Test passes if this is green!" should be visible.

            On the top component this text should be green on all platforms.
            On the bottom component it is possible that on non-Windows
            platforms text can be of other color or not visible at all.
            That does not constitute a problem.

            So if platform is Windows and green text appears twice or on any
            other platform green text appears at least once press "Pass",
            otherwise press "Fail".
            """;

    VolatileImage vimg;

    public void paint(Graphics g) {
        render(g);
        g.drawString("(Drawing to screen)", 10, 60);
        if (vimg == null) {
            vimg = createVolatileImage(TESTW, TESTH);
        }
        do {
            vimg.validate(null);
            Graphics g2 = vimg.getGraphics();
            render(g2);
            String not = vimg.getCapabilities().isAccelerated() ? "" : "not ";
            g2.drawString("Image was " + not + "accelerated", 10, 55);
            g2.drawString("(only matters on Windows)", 10, 65);
            g2.dispose();
            g.drawImage(vimg, 0, TESTH, null);
        } while (vimg.contentsLost());
    }

    public void render(Graphics g) {
        g.setColor(Color.black);
        g.fillRect(0, 0, TESTW, TESTH);
        g.setColor(Color.white);
        g.fillRect(5, 5, TESTW-10, TESTH-10);

        g.setColor(Color.black);
        g.drawString("Test only passes if green string appears", 10, 20);

        g.setColor(Color.white);
        g.setXORMode(Color.blue);
        g.drawRect(30, 30, 10, 10);
        g.setPaintMode();
        g.setColor(Color.green);

        ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                          RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.drawString("Test passes if this is green!", 10, 80);

        g.setColor(Color.black);
    }

    public Dimension getPreferredSize() {
        return new Dimension(TESTW, TESTH*2);
    }

    public static Frame createFrame() {
        Frame f = new Frame("Text After XOR Test");
        f.add(new TextAfterXor());
        f.pack();
        return f;
    }

    public static void main(String[] args) throws InterruptedException,
            InvocationTargetException {
        PassFailJFrame.builder()
                .title("Text After XOR Instructions")
                .instructions(INSTRUCTIONS)
                .testUI(TextAfterXor::createFrame)
                .build()
                .awaitAndCheck();
    }
}
