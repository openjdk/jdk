/*
 * Copyright (c) 2002, 2003, Oracle and/or its affiliates. All rights reserved.
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
package sun.swing.plaf.synth;

import javax.swing.plaf.synth.*;
import java.awt.*;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.UIResource;

/**
 * An icon that is passed a SynthContext. Subclasses need only implement
 * the variants that take a SynthContext, but must be prepared for the
 * SynthContext to be null.
 *
 * @author Scott Violet
 */
public abstract class SynthIcon implements Icon {
    public static int getIconWidth(Icon icon, SynthContext context) {
        if (icon == null) {
            return 0;
        }
        if (icon instanceof SynthIcon) {
            return ((SynthIcon)icon).getIconWidth(context);
        }
        return icon.getIconWidth();
    }

    public static int getIconHeight(Icon icon, SynthContext context) {
        if (icon == null) {
            return 0;
        }
        if (icon instanceof SynthIcon) {
            return ((SynthIcon)icon).getIconHeight(context);
        }
        return icon.getIconHeight();
    }

    public static void paintIcon(Icon icon, SynthContext context, Graphics g,
                                 int x, int y, int w, int h) {
        if (icon instanceof SynthIcon) {
            ((SynthIcon)icon).paintIcon(context, g, x, y, w, h);
        }
        else if (icon != null) {
            icon.paintIcon(context.getComponent(), g, x, y);
        }
    }

    /**
     * Paints the icon at the specified location.
     *
     * @param context Identifies hosting region, may be null.
     * @param x x location to paint to
     * @param y y location to paint to
     * @param w Width of the region to paint to, may be 0
     * @param h Height of the region to paint to, may be 0
     */
    public abstract void paintIcon(SynthContext context, Graphics g, int x,
                                   int y, int w, int h);

    /**
     * Returns the desired width of the Icon.
     *
     * @param context SynthContext requesting the Icon, may be null.
     * @return Desired width of the icon.
     */
    public abstract int getIconWidth(SynthContext context);

    /**
     * Returns the desired height of the Icon.
     *
     * @param context SynthContext requesting the Icon, may be null.
     * @return Desired height of the icon.
     */
    public abstract int getIconHeight(SynthContext context);

    /**
     * Paints the icon. This is a cover method for
     * <code>paintIcon(null, g, x, y, 0, 0)</code>
     */
    public void paintIcon(Component c, Graphics g, int x, int y) {
        paintIcon(null, g, x, y, 0, 0);
    }

    /**
     * Returns the icon's width. This is a cover methods for
     * <code>getIconWidth(null)</code>.
     *
     * @return an int specifying the fixed width of the icon.
     */
    public int getIconWidth() {
        return getIconWidth(null);
    }

    /**
     * Returns the icon's height. This is a cover method for
     * <code>getIconHeight(null)</code>.
     *
     * @return an int specifying the fixed height of the icon.
     */
    public int getIconHeight() {
        return getIconHeight(null);
    }
}
