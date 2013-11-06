/*
 * Copyright (c) 1997, 2008, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing.plaf.basic;

import javax.swing.*;
import java.awt.Component;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.InputEvent;

import sun.swing.SwingUtilities2;


/*
 * @author Hans Muller
 */

public class BasicGraphicsUtils
{

    private static final Insets GROOVE_INSETS = new Insets(2, 2, 2, 2);
    private static final Insets ETCHED_INSETS = new Insets(2, 2, 2, 2);

    public static void drawEtchedRect(Graphics g, int x, int y, int w, int h,
                                      Color shadow, Color darkShadow,
                                      Color highlight, Color lightHighlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        g.setColor(shadow);
        g.drawLine(0, 0, w-1, 0);      // outer border, top
        g.drawLine(0, 1, 0, h-2);      // outer border, left

        g.setColor(darkShadow);
        g.drawLine(1, 1, w-3, 1);      // inner border, top
        g.drawLine(1, 2, 1, h-3);      // inner border, left

        g.setColor(lightHighlight);
        g.drawLine(w-1, 0, w-1, h-1);  // outer border, bottom
        g.drawLine(0, h-1, w-1, h-1);  // outer border, right

        g.setColor(highlight);
        g.drawLine(w-2, 1, w-2, h-3);  // inner border, right
        g.drawLine(1, h-2, w-2, h-2);  // inner border, bottom

        g.translate(-x, -y);
        g.setColor(oldColor);
    }


    /**
     * Returns the amount of space taken up by a border drawn by
     * <code>drawEtchedRect()</code>
     *
     * @return  the inset of an etched rect
     */
    public static Insets getEtchedInsets() {
        return ETCHED_INSETS;
    }


    public static void drawGroove(Graphics g, int x, int y, int w, int h,
                                  Color shadow, Color highlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        g.setColor(shadow);
        g.drawRect(0, 0, w-2, h-2);

        g.setColor(highlight);
        g.drawLine(1, h-3, 1, 1);
        g.drawLine(1, 1, w-3, 1);

        g.drawLine(0, h-1, w-1, h-1);
        g.drawLine(w-1, h-1, w-1, 0);

        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    /**
     * Returns the amount of space taken up by a border drawn by
     * <code>drawGroove()</code>
     *
     * @return  the inset of a groove border
     */
    public static Insets getGrooveInsets() {
        return GROOVE_INSETS;
    }


    public static void drawBezel(Graphics g, int x, int y, int w, int h,
                                 boolean isPressed, boolean isDefault,
                                 Color shadow, Color darkShadow,
                                 Color highlight, Color lightHighlight)
    {
        Color oldColor = g.getColor();  // Make no net change to g
        g.translate(x, y);

        if (isPressed && isDefault) {
            g.setColor(darkShadow);
            g.drawRect(0, 0, w - 1, h - 1);
            g.setColor(shadow);
            g.drawRect(1, 1, w - 3, h - 3);
        } else if (isPressed) {
            drawLoweredBezel(g, x, y, w, h,
                             shadow, darkShadow, highlight, lightHighlight);
        } else if (isDefault) {
            g.setColor(darkShadow);
            g.drawRect(0, 0, w-1, h-1);

            g.setColor(lightHighlight);
            g.drawLine(1, 1, 1, h-3);
            g.drawLine(2, 1, w-3, 1);

            g.setColor(highlight);
            g.drawLine(2, 2, 2, h-4);
            g.drawLine(3, 2, w-4, 2);

            g.setColor(shadow);
            g.drawLine(2, h-3, w-3, h-3);
            g.drawLine(w-3, 2, w-3, h-4);

            g.setColor(darkShadow);
            g.drawLine(1, h-2, w-2, h-2);
            g.drawLine(w-2, h-2, w-2, 1);
        } else {
            g.setColor(lightHighlight);
            g.drawLine(0, 0, 0, h-1);
            g.drawLine(1, 0, w-2, 0);

            g.setColor(highlight);
            g.drawLine(1, 1, 1, h-3);
            g.drawLine(2, 1, w-3, 1);

            g.setColor(shadow);
            g.drawLine(1, h-2, w-2, h-2);
            g.drawLine(w-2, 1, w-2, h-3);

            g.setColor(darkShadow);
            g.drawLine(0, h-1, w-1, h-1);
            g.drawLine(w-1, h-1, w-1, 0);
        }
        g.translate(-x, -y);
        g.setColor(oldColor);
    }

    public static void drawLoweredBezel(Graphics g, int x, int y, int w, int h,
                                        Color shadow, Color darkShadow,
                                        Color highlight, Color lightHighlight)  {
        g.setColor(darkShadow);
        g.drawLine(0, 0, 0, h-1);
        g.drawLine(1, 0, w-2, 0);

        g.setColor(shadow);
        g.drawLine(1, 1, 1, h-2);
        g.drawLine(1, 1, w-3, 1);

        g.setColor(lightHighlight);
        g.drawLine(0, h-1, w-1, h-1);
        g.drawLine(w-1, h-1, w-1, 0);

        g.setColor(highlight);
        g.drawLine(1, h-2, w-2, h-2);
        g.drawLine(w-2, h-2, w-2, 1);
     }


    /** Draw a string with the graphics <code>g</code> at location (x,y)
     *  just like <code>g.drawString</code> would.
     *  The first occurrence of <code>underlineChar</code>
     *  in text will be underlined. The matching algorithm is
     *  not case sensitive.
     */
    public static void drawString(Graphics g,String text,int underlinedChar,int x,int y) {
        int index=-1;

        if (underlinedChar != '\0') {
            char uc = Character.toUpperCase((char)underlinedChar);
            char lc = Character.toLowerCase((char)underlinedChar);
            int uci = text.indexOf(uc);
            int lci = text.indexOf(lc);

            if(uci == -1) {
                index = lci;
            }
            else if(lci == -1) {
                index = uci;
            }
            else {
                index = (lci < uci) ? lci : uci;
            }
        }
        drawStringUnderlineCharAt(g, text, index, x, y);
    }

    /**
     * Draw a string with the graphics <code>g</code> at location
     * (<code>x</code>, <code>y</code>)
     * just like <code>g.drawString</code> would.
     * The character at index <code>underlinedIndex</code>
     * in text will be underlined. If <code>index</code> is beyond the
     * bounds of <code>text</code> (including &lt; 0), nothing will be
     * underlined.
     *
     * @param g Graphics to draw with
     * @param text String to draw
     * @param underlinedIndex Index of character in text to underline
     * @param x x coordinate to draw at
     * @param y y coordinate to draw at
     * @since 1.4
     */
    public static void drawStringUnderlineCharAt(Graphics g, String text,
                           int underlinedIndex, int x,int y) {
        SwingUtilities2.drawStringUnderlineCharAt(null, g, text,
                                                  underlinedIndex, x, y);
    }

    public static void drawDashedRect(Graphics g,int x,int y,int width,int height) {
        int vx,vy;

        // draw upper and lower horizontal dashes
        for (vx = x; vx < (x + width); vx+=2) {
            g.fillRect(vx, y, 1, 1);
            g.fillRect(vx, y + height-1, 1, 1);
        }

        // draw left and right vertical dashes
        for (vy = y; vy < (y + height); vy+=2) {
            g.fillRect(x, vy, 1, 1);
            g.fillRect(x+width-1, vy, 1, 1);
        }
    }

    public static Dimension getPreferredButtonSize(AbstractButton b, int textIconGap)
    {
        if(b.getComponentCount() > 0) {
            return null;
        }

        Icon icon = b.getIcon();
        String text = b.getText();

        Font font = b.getFont();
        FontMetrics fm = b.getFontMetrics(font);

        Rectangle iconR = new Rectangle();
        Rectangle textR = new Rectangle();
        Rectangle viewR = new Rectangle(Short.MAX_VALUE, Short.MAX_VALUE);

        SwingUtilities.layoutCompoundLabel(
            b, fm, text, icon,
            b.getVerticalAlignment(), b.getHorizontalAlignment(),
            b.getVerticalTextPosition(), b.getHorizontalTextPosition(),
            viewR, iconR, textR, (text == null ? 0 : textIconGap)
        );

        /* The preferred size of the button is the size of
         * the text and icon rectangles plus the buttons insets.
         */

        Rectangle r = iconR.union(textR);

        Insets insets = b.getInsets();
        r.width += insets.left + insets.right;
        r.height += insets.top + insets.bottom;

        return r.getSize();
    }

    /*
     * Convenience function for determining ComponentOrientation.  Helps us
     * avoid having Munge directives throughout the code.
     */
    static boolean isLeftToRight( Component c ) {
        return c.getComponentOrientation().isLeftToRight();
    }

    static boolean isMenuShortcutKeyDown(InputEvent event) {
        return (event.getModifiers() &
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMask()) != 0;
    }
}
