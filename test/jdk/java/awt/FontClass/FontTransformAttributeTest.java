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
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.font.TextAttribute;
import java.awt.font.TransformAttribute;
import java.awt.geom.AffineTransform;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4650042
 * @summary Draw text using a transform to simulate superscript, it should look like a superscript
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual FontTransformAttributeTest
 */

public class FontTransformAttributeTest extends JPanel {
    AttributedCharacterIterator iter;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                This test should display a string ending with the superscripted number '11'.
                Pass the test if you see the superscript.""";

        PassFailJFrame.builder()
                .title("FontTransformAttributeTest Instruction")
                .instructions(INSTRUCTIONS)
                .columns(35)
                .splitUI(FontTransformAttributeTest::new)
                .build()
                .awaitAndCheck();
    }

    FontTransformAttributeTest() {
        AffineTransform superTransform = AffineTransform.getScaleInstance(0.65, 0.65);
        superTransform.translate(0, -7);
        TransformAttribute superAttribute = new TransformAttribute(superTransform);
        String s = "a big number 7 11";
        AttributedString as = new AttributedString(s);
        as.addAttribute(TextAttribute.TRANSFORM, superAttribute, 15, 17);
        iter = as.getIterator();
        setBackground(Color.WHITE);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(200, 100);
    }

    @Override
    public void paint(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        Dimension d = getSize();
        g2.drawString(iter, 20, d.height / 2 + 8);
    }
}
