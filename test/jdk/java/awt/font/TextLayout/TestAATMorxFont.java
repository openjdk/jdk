/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

/* @test
 * @bug 8031462 8198406
 * @requires (os.family == "mac")
 * @summary verify rendering of MORX fonts on OS X.
 */

import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import static java.awt.image.BufferedImage.TYPE_INT_ARGB;

public class TestAATMorxFont {
    public static void main(String[] args) {
        String osName = System.getProperty("os.name");
        System.out.println("OS is " + osName);
        osName = osName.toLowerCase();
        if (!osName.startsWith("mac")) {
            return;
        }
        BufferedImage bi = new BufferedImage(1200, 400, TYPE_INT_ARGB);
        Graphics g = bi.getGraphics();
        test(g);
        g.dispose();
    }

    private static void test(Graphics g) {
        Graphics2D g2d = (Graphics2D)g;
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                             RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int y = 50;
        g.setFont(new Font("Gujarati MT", Font.PLAIN, 40));
        System.out.println(g.getFont());
        g.drawString("ક્ કકક ક્ક્ક", 20, y);
        y += 50;
        g.setFont(new Font("Tamil Sangam MN", Font.PLAIN, 40));
        System.out.println(g.getFont());
        g.drawString("க் ககக க்க்க", 20, y);
        y += 50;
        g.setFont(new Font("Telugu Sangam MN", Font.PLAIN, 40));
        System.out.println(g.getFont());
        g.drawString("క్ కకక క్క్క", 20, y);
        y += 50;
        g.setFont(new Font("Devanagari Sangam MN", Font.PLAIN, 40));
        System.out.println(g.getFont());
        g.drawString("की के कू", 20, y);
        y += 50;
        g.drawString("इर्क्क्क्क्क्क्क्क्क्क", 20, y);
        y += 50;
        g.drawString("रिव्यू के बाद विकास ओलंपिक से बाहर (देवनागरी) (हिन्दी) इर्क्क्क्क्क्क्क्क्क्क", 20, y);

    }
}

