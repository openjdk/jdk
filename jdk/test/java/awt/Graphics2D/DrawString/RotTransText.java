/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

/**
 * @test
 * @bug 6683472
 * @summary Transformed fonts using drawString and TextLayout should be in
 *          the same position.
 */

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.*;
import java.awt.image.*;
import java.util.HashMap;

public class RotTransText  {

    public static void main(String[] args) {

        int wid=400, hgt=400;
        BufferedImage bi =
            new BufferedImage(wid, hgt, BufferedImage.TYPE_INT_RGB);

        Graphics2D g2d = bi.createGraphics();

        g2d.setColor(Color.white);
        g2d.fillRect(0, 0, wid, hgt);

        int x=130, y=130;
        String s = "Text";

        int xt=90, yt=50;
        for (int angle=0;angle<360;angle+=30) {
            AffineTransform aff = AffineTransform.getTranslateInstance(50,90);
            aff.rotate(angle * Math.PI/180.0);

            Font fnt = new Font("SansSerif", Font.PLAIN, 60);
            fnt = fnt.deriveFont(Font.PLAIN, aff);
            g2d.setFont(fnt);
            g2d.setColor(Color.blue);
            g2d.drawString(s, x, y);

            g2d.setColor(Color.red);
            FontRenderContext frc = g2d.getFontRenderContext();
            HashMap attrMap = new HashMap();
            attrMap.put(TextAttribute.STRIKETHROUGH,
                    TextAttribute.STRIKETHROUGH_ON);
            fnt = fnt.deriveFont(attrMap);
            TextLayout tl = new TextLayout(s, fnt, frc);
            tl.draw(g2d, (float)x, (float)y);
        }
        // Test BI: should be no blue: only red and white.
        int red = Color.red.getRGB();
        int blue = Color.blue.getRGB();
        int white = Color.white.getRGB();
        for (int px=0;px<wid;px++) {
            for (int py=0;py<hgt;py++) {
                int rgb = bi.getRGB(px, py);
                if (rgb == blue || ( rgb != red && rgb != white)) {
                    throw new RuntimeException
                        ("Unexpected color : " + Integer.toHexString(rgb) +
                         " at x=" + x + " y="+ y);
                }
            }
        }
    }
}
