/*
 * Copyright 1998-2006 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing.plaf.basic;

import javax.swing.*;
import javax.swing.plaf.UIResource;

import java.awt.Container;
import java.awt.Dimension;
import static sun.swing.SwingUtilities2.BASICMENUITEMUI_MAX_TEXT_OFFSET;

/**
 * The default layout manager for Popup menus and menubars.  This
 * class is an extension of BoxLayout which adds the UIResource tag
 * so that plauggable L&Fs can distinguish it from user-installed
 * layout managers on menus.
 *
 * @author Georges Saab
 */

public class DefaultMenuLayout extends BoxLayout implements UIResource {
    public DefaultMenuLayout(Container target, int axis) {
        super(target, axis);
    }

    public Dimension preferredLayoutSize(Container target) {
        if (target instanceof JPopupMenu) {
            JPopupMenu popupMenu = (JPopupMenu) target;

            // Before the calculation of menu preferred size
            // clear the previously calculated maximal widths and offsets
            // in menu's Client Properties
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_ACC_WIDTH, null);
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_ARROW_WIDTH, null);
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_CHECK_WIDTH, null);
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_ICON_WIDTH, null);
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_LABEL_WIDTH, null);
            popupMenu.putClientProperty(BasicMenuItemUI.MAX_TEXT_WIDTH, null);
            popupMenu.putClientProperty(BASICMENUITEMUI_MAX_TEXT_OFFSET, null);

            if (popupMenu.getComponentCount() == 0) {
                return new Dimension(0, 0);
            }
        }

        // Make BoxLayout recalculate cached preferred sizes
        super.invalidateLayout(target);

        return super.preferredLayoutSize(target);
    }
}
