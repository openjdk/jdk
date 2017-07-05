/*
 * Copyright 2000-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package javax.swing;

import java.util.Comparator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Window;


/**
 * Comparator which attempts to sort Components based on their size and
 * position. Code adapted from original javax.swing.DefaultFocusManager
 * implementation.
 *
 * @author David Mendenhall
 */
final class LayoutComparator implements Comparator<Component>, java.io.Serializable {

    private static final int ROW_TOLERANCE = 10;

    private boolean horizontal = true;
    private boolean leftToRight = true;

    void setComponentOrientation(ComponentOrientation orientation) {
        horizontal = orientation.isHorizontal();
        leftToRight = orientation.isLeftToRight();
    }

    public int compare(Component a, Component b) {
        if (a == b) {
            return 0;
        }

        // Row/Column algorithm only applies to siblings. If 'a' and 'b'
        // aren't siblings, then we need to find their most inferior
        // ancestors which share a parent. Compute the ancestory lists for
        // each Component and then search from the Window down until the
        // hierarchy branches.
        if (a.getParent() != b.getParent()) {
            LinkedList<Component> aAncestory = new LinkedList<Component>();

            for(; a != null; a = a.getParent()) {
                aAncestory.add(a);
                if (a instanceof Window) {
                    break;
                }
            }
            if (a == null) {
                // 'a' is not part of a Window hierarchy. Can't cope.
                throw new ClassCastException();
            }

            LinkedList<Component> bAncestory = new LinkedList<Component>();

            for(; b != null; b = b.getParent()) {
                bAncestory.add(b);
                if (b instanceof Window) {
                    break;
                }
            }
            if (b == null) {
                // 'b' is not part of a Window hierarchy. Can't cope.
                throw new ClassCastException();
            }

            for (ListIterator<Component>
                     aIter = aAncestory.listIterator(aAncestory.size()),
                     bIter = bAncestory.listIterator(bAncestory.size()); ;) {
                if (aIter.hasPrevious()) {
                    a = aIter.previous();
                } else {
                    // a is an ancestor of b
                    return -1;
                }

                if (bIter.hasPrevious()) {
                    b = bIter.previous();
                } else {
                    // b is an ancestor of a
                    return 1;
                }

                if (a != b) {
                    break;
                }
            }
        }

        int ax = a.getX(), ay = a.getY(), bx = b.getX(), by = b.getY();

        int zOrder = a.getParent().getComponentZOrder(a) - b.getParent().getComponentZOrder(b);
        if (horizontal) {
            if (leftToRight) {

                // LT - Western Europe (optional for Japanese, Chinese, Korean)

                if (Math.abs(ay - by) < ROW_TOLERANCE) {
                    return (ax < bx) ? -1 : ((ax > bx) ? 1 : zOrder);
                } else {
                    return (ay < by) ? -1 : 1;
                }
            } else { // !leftToRight

                // RT - Middle East (Arabic, Hebrew)

                if (Math.abs(ay - by) < ROW_TOLERANCE) {
                    return (ax > bx) ? -1 : ((ax < bx) ? 1 : zOrder);
                } else {
                    return (ay < by) ? -1 : 1;
                }
            }
        } else { // !horizontal
            if (leftToRight) {

                // TL - Mongolian

                if (Math.abs(ax - bx) < ROW_TOLERANCE) {
                    return (ay < by) ? -1 : ((ay > by) ? 1 : zOrder);
                } else {
                    return (ax < bx) ? -1 : 1;
                }
            } else { // !leftToRight

                // TR - Japanese, Chinese, Korean

                if (Math.abs(ax - bx) < ROW_TOLERANCE) {
                    return (ay < by) ? -1 : ((ay > by) ? 1 : zOrder);
                } else {
                    return (ax > bx) ? -1 : 1;
                }
            }
        }
    }
}
