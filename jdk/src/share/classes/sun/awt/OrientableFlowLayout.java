/*
 * Copyright (c) 1996, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt;

import java.awt.*;

/**
 * Extends the FlowLayout class to support both vertical and horizontal
 * layout of components.  Orientation can be changed dynamically after
 * creation by calling either of the methods @method orientHorizontally or
 * @method orientVertically.  Separate values for alignment, vertical gap,
 * and horizontal gap can be specified for horizontal and vertical
 * orientation.
 *
 * @author Terry Cline
 */
public class OrientableFlowLayout extends FlowLayout {
    /**
     * The horizontal orientation constant.
     */
    public static final int HORIZONTAL = 0;

    /**
     * The vertical orientation constant.
     */
    public static final int VERTICAL   = 1;

    /**
     * The top vertical alignment constant.
     */
    public static final int TOP        = 0;

    /**
     * The bottom vertical alignment constant.
     */
    public static final int BOTTOM     = 2; // CENTER == 1

    int orientation;
    int vAlign;
    int vHGap;
    int vVGap;

    /**
     * Constructs a new flow layout with a horizontal orientation and
     * centered alignment.
     */
    public OrientableFlowLayout() {
        this(HORIZONTAL, CENTER, CENTER, 5, 5, 5, 5);
    }

    /**
     * Constructs a new flow layout with the specified orientation and
     * a centered alignment.
     *
     * @param orientation the orientation, one of HORIZONTAL or VERTICAL.
     */
    public OrientableFlowLayout(int orientation) {
        this(orientation, CENTER, CENTER, 5, 5, 5, 5);
    }

    /**
     * Constructs a new flow layout with the specified orientation and
     * alignment.
     *
     * @param orientation the orientation, one of HORIZONTAL or VERTICAL.
     * @param hAlign the horizontal alignment, one of LEFT, CENTER, or RIGHT.
     * @param vAlign the vertical alignment, one of TOP, CENTER, or BOTTOM.
     */
    public OrientableFlowLayout(int orientation, int hAlign, int vAlign) {
        this(orientation, hAlign, vAlign, 5, 5, 5, 5);
    }

    /**
     * Constructs a new flow layout with the specified orientation,
     * alignment, and gap values.
     *
     * @param orientation the orientation, one of HORIZONTAL or VERTICAL.
     * @param hAlign the horizontal alignment, one of LEFT, CENTER, or RIGHT.
     * @param vAlign the vertical alignment, one of TOP, CENTER, or BOTTOM.
     * @param hHGap the horizontal gap between components in HORIZONTAL.
     * @param hVGap the vertical gap between components in HORIZONTAL.
     * @param vHGap the horizontal gap between components in VERTICAL.
     * @param vVGap the vertical gap between components in VERTICAL.
     */
    public OrientableFlowLayout(int orientation, int hAlign, int vAlign, int hHGap, int hVGap, int vHGap, int vVGap) {
        super(hAlign, hHGap, hVGap);
        this.orientation = orientation;
        this.vAlign      = vAlign;
        this.vHGap       = vHGap;
        this.vVGap       = vVGap;
    }

    /**
     * Set the layout's current orientation to horizontal.
     */
    public synchronized void orientHorizontally() {
        orientation = HORIZONTAL;
    }

    /**
     * Set the layout's current orientation to vertical.
     */
    public synchronized void orientVertically() {
        orientation = VERTICAL;
    }

    /**
     * Returns the preferred dimensions for this layout given the
     * components in the specified target container.
     *
     * @param target the component which needs to be laid out.
     * @see Container
     * @see FlowLayout
     * @see #minimumLayoutSize
     */
    public Dimension preferredLayoutSize(Container target) {
        if (orientation == HORIZONTAL) {
            return super.preferredLayoutSize(target);
        }
        else {
            Dimension dim = new Dimension(0, 0);

            int n = target.countComponents();
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension cDim = c.preferredSize();
                    dim.width = Math.max(dim.width, cDim.width);
                    if (i > 0) {
                        dim.height += vVGap;
                    }
                    dim.height += cDim.height;
                }
            }

            Insets insets = target.insets();;
            dim.width  += insets.left + insets.right  + vHGap*2;
            dim.height += insets.top  + insets.bottom + vVGap*2;

            return dim;
        }
    }

    /**
     * Returns the minimum dimensions needed to layout the components
     * contained in the specified target container.
     *
     * @param target the component which needs to be laid out.
     * @see #preferredLayoutSize.
     */
    public Dimension minimumLayoutSize(Container target) {
        if (orientation == HORIZONTAL) {
            return super.minimumLayoutSize(target);
        }
        else {
            Dimension dim = new Dimension(0, 0);

            int n = target.countComponents();
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension cDim = c.minimumSize();
                    dim.width = Math.max(dim.width, cDim.width);
                    if (i > 0) {
                        dim.height += vVGap;
                    }
                    dim.height += cDim.height;
                }
            }

            Insets insets = target.insets();
            dim.width  += insets.left + insets.right  + vHGap*2;
            dim.height += insets.top  + insets.bottom + vVGap*2;

            return dim;
        }
    }

    /**
     * Lays out the container.  This method will reshape the
     * components in the target to satisfy the constraints of the
     * layout.
     *
     * @param target the specified component being laid out.
     * @see Container.
     */
    public void layoutContainer(Container target) {
        if (orientation == HORIZONTAL) {
            super.layoutContainer(target);
        }
        else {
            Insets insets = target.insets();
            Dimension targetDim = target.size();
            int maxHeight = targetDim.height - (insets.top + insets.bottom + vVGap*2);
            int x = insets.left + vHGap;
            int y = 0;
            int colWidth = 0;
            int start = 0;

            int n = target.countComponents();
            for (int i = 0; i < n; i++) {
                Component c = target.getComponent(i);
                if (c.isVisible()) {
                    Dimension cDim = c.preferredSize();
                    c.resize(cDim.width, cDim.height);

                    if ((y == 0) || ((y + cDim.height) <= maxHeight)) {
                        if (y > 0) {
                            y += vVGap;
                        }
                        y += cDim.height;
                        colWidth = Math.max(colWidth, cDim.width);
                    }
                    else {
                        moveComponents(target,
                                       x,
                                       insets.top + vVGap,
                                       colWidth,
                                       maxHeight - y,
                                       start,
                                       i);
                        x += vHGap + colWidth;
                        y = cDim.width;
                        colWidth = cDim.width;
                        start = i;
                    }
                }
            }

            moveComponents(target,
                           x,
                           insets.top + vVGap,
                           colWidth,
                           maxHeight - y,
                           start,
                           n);
        }
    }

    /**
     * Aligns the components vertically if there is any slack.
     *
     * @param target the container whose components need to be moved.
     * @param x the x coordinate.
     * @param y the y coordinate.
     * @param width the width available.
     * @param height the height available.
     * @param colStart the beginning of the column.
     * @param colEnd the end of the column.
     */
    private void moveComponents(Container target, int x, int y, int width, int height, int colStart, int colEnd) {
        switch (vAlign) {
        case TOP:
            break;
        case CENTER:
            y += height/2;
            break;
        case BOTTOM:
            y += height;
        }

        for (int i = colStart; i < colEnd; i++) {
            Component c = target.getComponent(i);
            Dimension cDim = c.size();
            if (c.isVisible()) {
                c.move(x + (width - cDim.width)/2, y);
                y += vVGap + cDim.height;
            }
        }
    }

    /**
     * Returns the String representation of this layout's values.
     */
    public String toString() {
        String str = "";
        switch (orientation) {
        case HORIZONTAL:
            str = "orientation=horizontal, ";
            break;
        case VERTICAL:
            str = "orientation=vertical, ";
            break;
        }

        return getClass().getName() + "[" + str + super.toString() + "]";
    }
}
