/*
 * Copyright (c) 1997, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.java.swing.plaf.motif;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.*;
import javax.swing.plaf.basic.BasicArrowButton;

import java.awt.*;
import java.awt.event.*;

/**
 * Motif scroll bar button.
 * <p>
 * <strong>Warning:</strong>
 * Serialized objects of this class will not be compatible with
 * future Swing releases.  The current serialization support is appropriate
 * for short term storage or RMI between applications running the same
 * version of Swing.  A future release of Swing will provide support for
 * long term persistence.
 */
@SuppressWarnings("serial") // Superclass is not serializable across versions
public class MotifScrollBarButton extends BasicArrowButton
{
    private Color darkShadow = UIManager.getColor("controlShadow");
    private Color lightShadow = UIManager.getColor("controlLtHighlight");


    public MotifScrollBarButton(int direction)
    {
        super(direction);

        switch (direction) {
        case NORTH:
        case SOUTH:
        case EAST:
        case WEST:
            this.direction = direction;
            break;
        default:
            throw new IllegalArgumentException("invalid direction");
        }

        setRequestFocusEnabled(false);
        setOpaque(true);
        setBackground(UIManager.getColor("ScrollBar.background"));
        setForeground(UIManager.getColor("ScrollBar.foreground"));
    }


    public Dimension getPreferredSize() {
        switch (direction) {
        case NORTH:
        case SOUTH:
            return new Dimension(11, 12);
        case EAST:
        case WEST:
        default:
            return new Dimension(12, 11);
        }
    }

    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    public boolean isFocusTraversable() {
        return false;
    }

    public void paint(Graphics g)
    {
        int w = getWidth();
        int h = getHeight();

        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, w, h);
        }

        boolean isPressed = getModel().isPressed();
        Color lead = (isPressed) ? darkShadow : lightShadow;
        Color trail = (isPressed) ? lightShadow : darkShadow;
        Color fill = getBackground();

        int cx = w / 2;
        int cy = h / 2;
        int s = Math.min(w, h);

        switch (direction) {
        case NORTH:
            g.setColor(lead);
            g.drawLine(cx, 0, cx, 0);
            for (int x = cx - 1, y = 1, dx = 1; y <= s - 2; y += 2) {
                g.setColor(lead);
                g.drawLine(x, y, x, y);
                if (y >= (s - 2)) {
                    g.drawLine(x, y + 1, x, y + 1);
                }
                g.setColor(fill);
                g.drawLine(x + 1, y, x + dx, y);
                if (y < (s - 2)) {
                    g.drawLine(x, y + 1, x + dx + 1, y + 1);
                }
                g.setColor(trail);
                g.drawLine(x + dx + 1, y, x + dx + 1, y);
                if (y >= (s - 2)) {
                    g.drawLine(x + 1, y + 1, x + dx + 1, y + 1);
                }
                dx += 2;
                x -= 1;
            }
            break;

        case SOUTH:
            g.setColor(trail);
            g.drawLine(cx, s, cx, s);
            for (int x = cx - 1, y = s - 1, dx = 1; y >= 1; y -= 2) {
                g.setColor(lead);
                g.drawLine(x, y, x, y);
                if (y <= 2) {
                    g.drawLine(x, y - 1, x + dx + 1, y - 1);
                }
                g.setColor(fill);
                g.drawLine(x + 1, y, x + dx, y);
                if (y > 2) {
                    g.drawLine(x, y - 1, x + dx + 1, y - 1);
                }
                g.setColor(trail);
                g.drawLine(x + dx + 1, y, x + dx + 1, y);

                dx += 2;
                x -= 1;
            }
            break;

        case EAST:
            g.setColor(lead);
            g.drawLine(s, cy, s, cy);
            for (int y = cy - 1, x = s - 1, dy = 1; x >= 1; x -= 2) {
                g.setColor(lead);
                g.drawLine(x, y, x, y);
                if (x <= 2) {
                    g.drawLine(x - 1, y, x - 1, y + dy + 1);
                }
                g.setColor(fill);
                g.drawLine(x, y + 1, x, y + dy);
                if (x > 2) {
                    g.drawLine(x - 1, y, x - 1, y + dy + 1);
                }
                g.setColor(trail);
                g.drawLine(x, y + dy + 1, x, y + dy + 1);

                dy += 2;
                y -= 1;
            }
            break;

        case WEST:
            g.setColor(trail);
            g.drawLine(0, cy, 0, cy);
            for (int y = cy - 1, x = 1, dy = 1; x <= s - 2; x += 2) {
                g.setColor(lead);
                g.drawLine(x, y, x, y);
                if (x >= (s - 2)) {
                    g.drawLine(x + 1, y, x + 1, y);
                }
                g.setColor(fill);
                g.drawLine(x, y + 1, x, y + dy);
                if (x < (s - 2)) {
                    g.drawLine(x + 1, y, x + 1, y + dy + 1);
                }
                g.setColor(trail);
                g.drawLine(x, y + dy + 1, x, y + dy + 1);
                if (x >= (s - 2)) {
                    g.drawLine(x + 1, y + 1, x + 1, y + dy + 1);
                }
                dy += 2;
                y -= 1;
            }
            break;
        }
    }
}
