/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.text.AttributedString;
import java.awt.font.TextAttribute;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4188328
 * @summary In this bug, a text string is displayed rotated. Without the
 *          fix, on Windows, the string was not displayed (boxes were
 *          displayed which denote an unprintable character). On Solaris
 *          2.5.1, the characters were displayed, but not rotated. Now
 *          on all platforms, the string is displayed correctly rotated.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual RotateTest1
 */

public class RotateTest1 extends JPanel {
    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                In this test, a text string is displayed rotated.

                Without the fix, on Windows, the string was not displayed
                (boxes were displayed which denote an unprintable character).

                On Solaris 2.5.1, the characters were displayed, but not rotated.

                Now on all platforms, the string is displayed rotated.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(RotateTest1::new)
                .build()
                .awaitAndCheck();
    }

    public RotateTest1() {
        setBackground(Color.WHITE);
        setDoubleBuffered(true);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(400, 520);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        Dimension d = getSize();

        g.setColor(getBackground());
        g.fillRect(0, 0, d.width, d.height);

        // start java2d test code
        paintRotatedTextTest(g2d);
    }

    private void paintRotatedTextTest(Graphics2D g2d) {
        AttributedString testString =
                new AttributedString("This is some text. Blablablabla");
        testString.addAttribute(TextAttribute.SIZE, 32f);

        g2d.setPaint(Color.BLACK);
        g2d.rotate(Math.PI / 3);
        g2d.drawString(testString.getIterator(), 100.0f, 10.0f);
    }
}
