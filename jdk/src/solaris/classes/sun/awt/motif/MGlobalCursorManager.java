/*
 * Copyright 1999-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.motif;

import java.awt.*;
import sun.awt.GlobalCursorManager;
import sun.awt.GlobalCursorManager.*;

public final class MGlobalCursorManager extends GlobalCursorManager {

    static {
        cacheInit();
    }

    private native static void cacheInit();

    // cached nativeContainer
    private Component nativeContainer;


    /**
     * The MGlobalCursorManager is a singleton.
     */
    private static MGlobalCursorManager manager;


    static GlobalCursorManager getCursorManager() {
        if (manager == null) {
            manager = new MGlobalCursorManager();
        }
        return manager;
    }

    /**
     * Should be called in response to a native mouse enter or native mouse
     * button released message. Should not be called during a mouse drag.
     */
    static void nativeUpdateCursor(Component heavy) {
        MGlobalCursorManager.getCursorManager().updateCursorLater(heavy);
    }


    protected void setCursor(Component comp, Cursor cursor, boolean useCache) {
        if (comp == null) {
            return;
        }

        Cursor cur = useCache ? cursor : getCapableCursor(comp);

        Component nc = useCache ? nativeContainer : getNativeContainer(comp);

        // System.out.println(" set cursor="+cursor+"  on "+comp+"  new curs="+cur);
        if (nc != null && nc.isDisplayable()) {
            nativeContainer = nc;
            ((MComponentPeer)nc.getPeer()).pSetCursor(cur);
        }
    }

    private Component getNativeContainer(Component comp) {
        while (comp != null && comp.isLightweight()) {
            comp = comp.getParent();
        }
        return comp;
    }

    protected native void getCursorPos(Point p);
    protected native Component findHeavyweightUnderCursor();

    /*
     * two native methods to call corresponding methods in Container and
     * Component
     */
    protected native Component findComponentAt(Container con, int x, int y);
    protected native Point getLocationOnScreen(Component com);

    protected Component findHeavyweightUnderCursor(boolean useCache) {
        return findHeavyweightUnderCursor();
    }

    private Cursor getCapableCursor(Component comp) {
        Component c = comp;
        while ((c != null) && !(c instanceof Window) &&
               c.isEnabled() && c.isVisible() && c.isDisplayable()) {
            c = c.getParent();
        }
        if (c instanceof Window) {
            return (c.isEnabled() && c.isVisible() && c.isDisplayable() && comp.isEnabled()) ?
                    comp.getCursor() :
                    Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR);
        } else if (c == null) {
            return null;
        }
        return getCapableCursor(c.getParent());
    }
}
