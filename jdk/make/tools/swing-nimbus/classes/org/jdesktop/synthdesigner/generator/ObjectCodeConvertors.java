/*
 * Copyright 2002-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
package org.jdesktop.synthdesigner.generator;

import java.awt.*;

/**
 * ObjectCodeConvertors
 *
 * @author  Richard Bair
 * @author  Jasper Potts
 */
public class ObjectCodeConvertors {
    static java.math.MathContext ctx = new java.math.MathContext(3);

    /**
     * Given a value (x), encode it such that 0 -> 1 is to the left of a, 1 -> 2 is between a and b, and 2 -> 3
     * is to the right of b.
     *
     * @param w width in the case of the x axis, height in the case of the y axis.
     */
    static float encode(float x, float a, float b, float w) {
        float r = 0;
        if (x < a) {
            r = (x / a);
        } else if (x > b) {
            r = 2 + ((x - b) / (w - b));
        } else if (x == a && x == b) {
            return 1.5f;
        } else {
            r = 1 + ((x - a) / (b - a));
        }

        if (Float.isNaN(r)) {
            System.err.println("[Error] Encountered NaN: encode(" + x + ", " + a + ", " + b + ", " + w + ")");
            return 0;
        } else if (Float.isInfinite(r)) {
            System.err.println("[Error] Encountered Infinity: encode(" + x + ", " + a + ", " + b + ", " + w + ")");
            return 0;
        } else if (r < 0) {
            System.err.println("[Error] encoded value was less than 0: encode(" + x + ", " + a + ", " + b + ", " + w + ")");
            return 0;
        } else if (r > 3) {
            System.err.println("[Error] encoded value was greater than 3: encode(" + x + ", " + a + ", " + b + ", " + w + ")");
            return 3;
        } else {
            //for prettyness sake (and since we aren't really going to miss
            //any accuracy here) I'm rounding this to 3 decimal places
//                return java.math.BigDecimal.valueOf(r).round(ctx).doubleValue();
            return r;
        }
    }

    static String convert(Paint paint) {
        //TODO need to support writing out other Paints, such as gradients
        if (paint instanceof Color) {
            return convert((Color) paint);
        } else {
            System.err.println("[WARNING] Unable to encode a paint in the encode(Paint) method: " + paint);
            return "null";
        }
    }

    /**
     * Given a Color, write out the java code required to create a new Color.
     *
     * @param color The color to convert
     * @return String of the code for the color
     */
    static String convert(Color color) {
        return "new Color(" +
                color.getRed() + ", " +
                color.getGreen() + ", " +
                color.getBlue() + ", " +
                color.getAlpha() + ")";
    }

    static String convert(Insets i) {
        return "new Insets(" + i.top + ", " + i.left + ", " + i.bottom + ", " + i.right + ")";
    }

    static String convert(Dimension d) {
        return "new Dimension(" + d.width + ", " + d.height + ")";
    }

}
