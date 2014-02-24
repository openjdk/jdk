/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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
package sun.awt.X11;

import java.awt.*;
import java.awt.peer.*;

import java.util.Vector;
import sun.util.logging.PlatformLogger;
import sun.awt.AWTAccessor;

public class XMenuPeer extends XMenuItemPeer implements MenuPeer {

    /************************************************
     *
     * Data members
     *
     ************************************************/
    private static PlatformLogger log = PlatformLogger.getLogger("sun.awt.X11.XMenuPeer");

    /**
     * Window that correspond to this menu
     */
    XMenuWindow menuWindow;

    /************************************************
     *
     * Construction
     *
     ************************************************/
    XMenuPeer(Menu target) {
        super(target);
    }

    /**
     * This function is called when menu is bound
     * to its container window. Creates submenu window
     * that fills its items vector while construction
     */
    void setContainer(XBaseMenuWindow container) {
        super.setContainer(container);
        menuWindow = new XMenuWindow(this);
    }


    /************************************************
     *
     * Implementaion of interface methods
     *
     ************************************************/

    /*
     * From MenuComponentPeer
     */

    /**
     * Disposes menu window if needed
     */
    public void dispose() {
        if (menuWindow != null) {
            menuWindow.dispose();
        }
        super.dispose();
    }

    /**
     * Resets text metrics for this item, for its menu window
     * and for all descendant menu windows
     */
    public void setFont(Font font) {
        //TODO:We can decrease count of repaints here
        //and get rid of recursion
        resetTextMetrics();

        XMenuWindow menuWindow = getMenuWindow();
        if (menuWindow != null) {
            menuWindow.setItemsFont(font);
        }

        repaintIfShowing();
    }

    /*
     * From MenuPeer
     */
    /**
     * addSeparator routines are not used
     * in peers. Shared code invokes addItem("-")
     * for adding separators
     */
    public void addSeparator() {
        if (log.isLoggable(PlatformLogger.Level.FINER)) {
            log.finer("addSeparator is not implemented");
        }
    }

    public void addItem(MenuItem item) {
        XMenuWindow menuWindow = getMenuWindow();
        if (menuWindow != null) {
            menuWindow.addItem(item);
        } else {
            if (log.isLoggable(PlatformLogger.Level.FINE)) {
                log.fine("Attempt to use XMenuWindowPeer without window");
            }
        }
    }

    public void delItem(int index) {
        XMenuWindow menuWindow = getMenuWindow();
        if (menuWindow != null) {
            menuWindow.delItem(index);
        } else {
            if (log.isLoggable(PlatformLogger.Level.FINE)) {
                log.fine("Attempt to use XMenuWindowPeer without window");
            }
        }
    }

    /************************************************
     *
     * Access to target's fields
     *
     ************************************************/
    Vector getTargetItems() {
        return AWTAccessor.getMenuAccessor().getItems((Menu)getTarget());
    }

    /************************************************
     *
     * Overriden behaviour
     *
     ************************************************/
    boolean isSeparator() {
        return false;
    }

    //Fix for 6180416: Shortcut keys are displayed against Menus on XToolkit
    //Menu should always return null as shortcutText
    String getShortcutText() {
        return null;
    }

    /************************************************
     *
     * Utility functions
     *
     ************************************************/

    /**
     * Returns menu window of this menu or null
     * it this menu has no container and so its
     * window can't be created.
     */
    XMenuWindow getMenuWindow() {
        return menuWindow;
    }

}
