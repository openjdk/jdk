/*
 * Copyright (c) 1999, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 *
 * Copyright 1999 IBM Corp.  All Rights Reserved.
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Shape;
import java.text.AttributedString;
import java.awt.font.FontRenderContext;
import java.awt.font.GraphicAttribute;
import java.awt.font.ImageGraphicAttribute;
import java.awt.font.ShapeGraphicAttribute;
import java.awt.font.TextLayout;
import java.awt.font.TextAttribute;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/*
 * @test
 * @bug 4202637
 * @summary This test ensures that graphics in a TextLayout are positioned correctly.
 * @library /java/awt/regtesthelpers
 * @build PassFailJFrame
 * @run main/manual TestGraphicPlacement
 */

public class TestGraphicPlacement extends JPanel {
    private static final int GRAPHIC_COUNT = 5;
    private static final float BASE_SIZE = 5;
    private static final boolean SHAPE = false;
    private static final boolean IMAGE = true;

    private final AttributedString[] strings;

    public static void main(String[] args) throws Exception {
        final String INSTRUCTIONS = """
                This test has text strings enclosed in boxes, in each box there is
                a sequence of square graphics to the right of the text string.

                This test is concerned with the placement of these graphics relative
                to the text string.

                Squares after 'TOP' should be placed in the top-right corner of the
                box with their tops aligned to the top of the box.

                Graphics after 'BOTTOM' should be placed in the bottom-right corner of its
                box with their bottoms aligned to the bottom of the box.

                Graphics after 'BASELINE' should have their tops (not bottoms) aligned to
                the baseline of the text.

                If all these are true, pass the test.""";

        PassFailJFrame.builder()
                .instructions(INSTRUCTIONS)
                .columns(45)
                .testUI(TestGraphicPlacement::new)
                .build()
                .awaitAndCheck();
    }

    public TestGraphicPlacement() {
        setBackground(Color.WHITE);

        strings = new AttributedString[]{
                makeString(GraphicAttribute.TOP_ALIGNMENT, SHAPE),
                makeString(GraphicAttribute.BOTTOM_ALIGNMENT, SHAPE),
                makeString(GraphicAttribute.ROMAN_BASELINE, SHAPE),
                makeString(GraphicAttribute.TOP_ALIGNMENT, IMAGE),
                makeString(GraphicAttribute.BOTTOM_ALIGNMENT, IMAGE),
                makeString(GraphicAttribute.ROMAN_BASELINE, IMAGE),
        };
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(350, 450);
    }

    private Image makeImage(int size) {
        Image img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = (Graphics2D) img.getGraphics();
        g2d.setColor(Color.BLUE);
        g2d.fillRect(0, 0, size, size);
        g2d.setColor(Color.RED);
        g2d.drawRect(0, 0, size - 1, size - 1);
        return img;
    }

    /**
     * Create an AttributedString with a descriptive word (TOP, BOTTOM,
     * or BASELINE) and several graphics of varying sizes following the
     * word.
     *
     * @param alignment the alignment of the graphics
     * @param image     whether to create Shape or Image graphics
     */
    private AttributedString makeString(int alignment, boolean image) {
        String name;
        if (alignment == GraphicAttribute.TOP_ALIGNMENT) {
            name = "TOP";
        } else if (alignment == GraphicAttribute.BOTTOM_ALIGNMENT) {
            name = "BOTTOM";
        } else {
            name = "BASELINE";
        }

        // Append the Unicode graphic replacement character to the name.
        String nameWithUnicode = name.concat("\uFFFC".repeat(GRAPHIC_COUNT));

        AttributedString as = new AttributedString(nameWithUnicode);

        // Make the descriptive text large.
        as.addAttribute(TextAttribute.SIZE, 48f, 0, name.length());

        // Add the graphic attributes to the end of the AttributedString.
        for (int i = 0; i < GRAPHIC_COUNT; i++) {

            float size = (i + 1) * BASE_SIZE;
            GraphicAttribute attribute;

            if (image == IMAGE) {
                Image img = makeImage((int) size);
                attribute = new ImageGraphicAttribute(img, alignment);
            } else {
                Shape shape = new Rectangle2D.Float(0, 0, size, size);
                attribute = new ShapeGraphicAttribute(shape,
                                                      alignment,
                                                      ShapeGraphicAttribute.STROKE);
            }

            as.addAttribute(TextAttribute.CHAR_REPLACEMENT,
                    attribute,
                    i + name.length(),
                    i + name.length() + 1);
        }

        return as;
    }

    /**
     * Draw each AttributedString, with a bounding box enclosing
     * the string.
     */
    @Override
    public void paint(Graphics g) {
        super.paint(g);
        Graphics2D g2d = (Graphics2D) g;
        FontRenderContext frc = g2d.getFontRenderContext();

        final float drawX = 20;
        float drawY = 20;

        for (int i = 0; i < strings.length; i++) {
            TextLayout layout = new TextLayout(strings[i].getIterator(), frc);
            float ascent = layout.getAscent();
            drawY += ascent;

            Rectangle2D boundsRect = new Rectangle2D.Float(drawX,
                    drawY - ascent,
                    layout.getAdvance(),
                    ascent + layout.getDescent());
            g2d.draw(boundsRect);
            layout.draw(g2d, drawX, drawY);
            drawY += layout.getDescent() + layout.getLeading();
        }
    }
}
