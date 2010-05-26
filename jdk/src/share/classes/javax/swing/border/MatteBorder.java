/*
 * Copyright (c) 1997, 2006, Oracle and/or its affiliates. All rights reserved.
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
package javax.swing.border;

import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Component;
import java.awt.Color;

import javax.swing.Icon;

/**
 * A class which provides a matte-like border of either a solid color
 * or a tiled icon.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases. The current serialization support is
 * appropriate for short term storage or RMI between applications running
 * the same version of Swing.  As of 1.4, support for long term storage
 * of all JavaBeans<sup><font size="-2">TM</font></sup>
 * has been added to the <code>java.beans</code> package.
 * Please see {@link java.beans.XMLEncoder}.
 *
 * @author Amy Fowler
 */
public class MatteBorder extends EmptyBorder
{
    protected Color color;
    protected Icon tileIcon;

    /**
     * Creates a matte border with the specified insets and color.
     * @param top the top inset of the border
     * @param left the left inset of the border
     * @param bottom the bottom inset of the border
     * @param right the right inset of the border
     * @param matteColor the color rendered for the border
     */
    public MatteBorder(int top, int left, int bottom, int right, Color matteColor)   {
        super(top, left, bottom, right);
        this.color = matteColor;
    }

    /**
     * Creates a matte border with the specified insets and color.
     * @param borderInsets the insets of the border
     * @param matteColor the color rendered for the border
     * @since 1.3
     */
    public MatteBorder(Insets borderInsets, Color matteColor)   {
        super(borderInsets);
        this.color = matteColor;
    }

    /**
     * Creates a matte border with the specified insets and tile icon.
     * @param top the top inset of the border
     * @param left the left inset of the border
     * @param bottom the bottom inset of the border
     * @param right the right inset of the border
     * @param tileIcon the icon to be used for tiling the border
     */
    public MatteBorder(int top, int left, int bottom, int right, Icon tileIcon)   {
        super(top, left, bottom, right);
        this.tileIcon = tileIcon;
    }

    /**
     * Creates a matte border with the specified insets and tile icon.
     * @param borderInsets the insets of the border
     * @param tileIcon the icon to be used for tiling the border
     * @since 1.3
     */
    public MatteBorder(Insets borderInsets, Icon tileIcon)   {
        super(borderInsets);
        this.tileIcon = tileIcon;
    }

    /**
     * Creates a matte border with the specified tile icon.  The
     * insets will be calculated dynamically based on the size of
     * the tile icon, where the top and bottom will be equal to the
     * tile icon's height, and the left and right will be equal to
     * the tile icon's width.
     * @param tileIcon the icon to be used for tiling the border
     */
    public MatteBorder(Icon tileIcon)   {
        this(-1,-1,-1,-1, tileIcon);
    }

    /**
     * Paints the matte border.
     */
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Insets insets = getBorderInsets(c);
        Color oldColor = g.getColor();
        g.translate(x, y);

        // If the tileIcon failed loading, paint as gray.
        if (tileIcon != null) {
            color = (tileIcon.getIconWidth() == -1) ? Color.gray : null;
        }

        if (color != null) {
            g.setColor(color);
            g.fillRect(0, 0, width - insets.right, insets.top);
            g.fillRect(0, insets.top, insets.left, height - insets.top);
            g.fillRect(insets.left, height - insets.bottom, width - insets.left, insets.bottom);
            g.fillRect(width - insets.right, 0, insets.right, height - insets.bottom);

        } else if (tileIcon != null) {

            int tileW = tileIcon.getIconWidth();
            int tileH = tileIcon.getIconHeight();
            int xpos, ypos, startx, starty;
            Graphics cg;

            // Paint top matte edge
            cg = g.create();
            cg.setClip(0, 0, width, insets.top);
            for (ypos = 0; insets.top - ypos > 0; ypos += tileH) {
                for (xpos = 0; width - xpos > 0; xpos += tileW) {
                    tileIcon.paintIcon(c, cg, xpos, ypos);
                }
            }
            cg.dispose();

            // Paint left matte edge
            cg = g.create();
            cg.setClip(0, insets.top, insets.left, height - insets.top);
            starty = insets.top - (insets.top%tileH);
            startx = 0;
            for (ypos = starty; height - ypos > 0; ypos += tileH) {
                for (xpos = startx; insets.left - xpos > 0; xpos += tileW) {
                    tileIcon.paintIcon(c, cg, xpos, ypos);
                }
            }
            cg.dispose();

            // Paint bottom matte edge
            cg = g.create();
            cg.setClip(insets.left, height - insets.bottom, width - insets.left, insets.bottom);
            starty = (height - insets.bottom) - ((height - insets.bottom)%tileH);
            startx = insets.left - (insets.left%tileW);
            for (ypos = starty; height - ypos > 0; ypos += tileH) {
                for (xpos = startx; width - xpos > 0; xpos += tileW) {
                    tileIcon.paintIcon(c, cg, xpos, ypos);
                }
            }
            cg.dispose();

            // Paint right matte edge
            cg = g.create();
            cg.setClip(width - insets.right, insets.top, insets.right, height - insets.top - insets.bottom);
            starty = insets.top - (insets.top%tileH);
            startx = width - insets.right - ((width - insets.right)%tileW);
            for (ypos = starty; height - ypos > 0; ypos += tileH) {
                for (xpos = startx; width - xpos > 0; xpos += tileW) {
                    tileIcon.paintIcon(c, cg, xpos, ypos);
                }
            }
            cg.dispose();
        }
        g.translate(-x, -y);
        g.setColor(oldColor);

    }

    /**
     * Reinitialize the insets parameter with this Border's current Insets.
     * @param c the component for which this border insets value applies
     * @param insets the object to be reinitialized
     * @since 1.3
     */
    public Insets getBorderInsets(Component c, Insets insets) {
        return computeInsets(insets);
    }

    /**
     * Returns the insets of the border.
     * @since 1.3
     */
    public Insets getBorderInsets() {
        return computeInsets(new Insets(0,0,0,0));
    }

    /* should be protected once api changes area allowed */
    private Insets computeInsets(Insets insets) {
        if (tileIcon != null && top == -1 && bottom == -1 &&
            left == -1 && right == -1) {
            int w = tileIcon.getIconWidth();
            int h = tileIcon.getIconHeight();
            insets.top = h;
            insets.right = w;
            insets.bottom = h;
            insets.left = w;
        } else {
            insets.left = left;
            insets.top = top;
            insets.right = right;
            insets.bottom = bottom;
        }
        return insets;
    }

    /**
     * Returns the color used for tiling the border or null
     * if a tile icon is being used.
     * @since 1.3
     */
    public Color getMatteColor() {
        return color;
    }

   /**
     * Returns the icon used for tiling the border or null
     * if a solid color is being used.
     * @since 1.3
     */
    public Icon getTileIcon() {
        return tileIcon;
    }

    /**
     * Returns whether or not the border is opaque.
     */
    public boolean isBorderOpaque() {
        // If a tileIcon is set, then it may contain transparent bits
        return color != null;
    }

}
