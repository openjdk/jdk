/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4188744
 * @summary This test verifies that copyArea performs correctly for negative offset values.
 *          The correct output shows that the text area is moved to the left and down,
 *          leaving some garbage on the right and the top.
 *          The incorrect copy would show the text area garbled and no text is legible.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual CopyNegative
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Panel;

public class CopyNegative extends Panel {

   private static final String INSTRUCTIONS = """
       This test verifies that copyArea performs correctly for negative offset values.
       The test draws text in an image, then copies the contents repeatedly.
       The correct output shows that the text is moved to the left and down,
       leaving some garbage on the top / right and some legible text at the bottom left.
       The incorrect copy would show the whole text area garbled and no text is legible.
       """;

    public static void main(String[] argv) throws Exception {
       PassFailJFrame.builder()
            .title("CopyNegativeTest")
            .instructions(INSTRUCTIONS)
            .testUI(CopyNegative::createUI)
            .testTimeOut(5)
            .rows(10)
            .columns(50)
            .build()
            .awaitAndCheck();
    }

    Image img;

    static final int W = 200, H = 200;

    static Frame createUI() {
        Frame f = new Frame("CopyNegative");
        f.add(new CopyNegative());
        f.pack();
        return f;
    }

    public Dimension getPreferredSize() {
        return new Dimension(W, H);
    }

    private void doCopy() {
        Graphics g = img.getGraphics();
        g.setColor(Color.white);
        g.fillRect(0, 0, W, H);
        g.setColor(Color.black);
        String text = "Some Text To Display, it is long enough to fill the entire display line.";
        StringBuffer sb = new StringBuffer(text);

        for (int i = 1; i < 50; i++) {
            g.drawString(sb.toString(), 5,20 * i - 10);
            sb.insert(0, Integer.toString(i));
        }
        for (int i = 0 ; i < 20 ; i++ ) {
            g.copyArea(0, 0, W, H, -3, 3);
        }
    }

    public void paint(Graphics g) {
        img = createImage(W, H);
        doCopy();
        g.drawImage(img, 0, 0, this);
    }

}
