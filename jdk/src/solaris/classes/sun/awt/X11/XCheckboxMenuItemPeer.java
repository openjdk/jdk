/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.X11;

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.*;

import java.lang.reflect.Field;
import sun.awt.SunToolkit;

class XCheckboxMenuItemPeer extends XMenuItemPeer implements CheckboxMenuItemPeer {

    /************************************************
     *
     * Data members
     *
     ************************************************/

    /*
     * CheckboxMenuItem's fields
     */
    private final static Field f_state;
    static {
        f_state = SunToolkit.getField(CheckboxMenuItem.class, "state");
    }

    /************************************************
     *
     * Construction
     *
     ************************************************/
    XCheckboxMenuItemPeer(CheckboxMenuItem target) {
        super(target);
    }

    /************************************************
     *
     * Implementaion of interface methods
     *
     ************************************************/

    //Prom CheckboxMenuItemtPeer
    public void setState(boolean t) {
        repaintIfShowing();
    }

    /************************************************
     *
     * Access to target's fields
     *
     ************************************************/
    boolean getTargetState() {
        MenuItem target = getTarget();
        if (target == null) {
            return false;
        }
        try {
            return f_state.getBoolean(target);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
        return false;
    }

    /************************************************
     *
     * Utility functions
     *
     ************************************************/

    /**
     * Toggles state and generates ItemEvent
     */
    void action(final long when) {
        XToolkit.executeOnEventHandlerThread((CheckboxMenuItem)getTarget(), new Runnable() {
                public void run() {
                    doToggleState(when);
                }
            });
    }


    /************************************************
     *
     * Private
     *
     ************************************************/
    private void doToggleState(long when) {
        CheckboxMenuItem cb = (CheckboxMenuItem)getTarget();
        boolean newState = !getTargetState();
        cb.setState(newState);
        ItemEvent e = new ItemEvent(cb,
                                    ItemEvent.ITEM_STATE_CHANGED,
                                    getTargetLabel(),
                                    getTargetState() ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
        XWindow.postEventStatic(e);
        //WToolkit does not post ActionEvent when clicking on menu item
        //MToolkit _does_ post.
        //Fix for 5005195 MAWT: CheckboxMenuItem fires action events
        //Events should not be fired
        //XWindow.postEventStatic(new ActionEvent(cb, ActionEvent.ACTION_PERFORMED,
        //                                        getTargetActionCommand(), when,
        //                                        0));
    }

} // class XCheckboxMenuItemPeer
