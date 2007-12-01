/*
 * Copyright 1995-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.event.*;
import java.awt.peer.*;

class MCheckboxMenuItemPeer extends MMenuItemPeer
                            implements CheckboxMenuItemPeer {
    private boolean inUpCall=false;
    private boolean inInit=false;

    native void pSetState(boolean t);
    native boolean getState();

    void create(MMenuPeer parent) {
        super.create(parent);
        inInit=true;
        setState(((CheckboxMenuItem)target).getState());
        inInit=false;
    }

    MCheckboxMenuItemPeer(CheckboxMenuItem target) {
        this.target = target;
        isCheckbox = true;
        MMenuPeer parent = (MMenuPeer) MToolkit.targetToPeer(getParent_NoClientCode(target));
        create(parent);
    }

    public void setState(boolean t) {
        if (!nativeCreated) {
            return;
        }
        if (!inUpCall && (t != getState())) {
            pSetState(t);
            if (!inInit) {
                // 4135725 : do not notify on programatic changes
                // notifyStateChanged(t);
            }
        }
    }

    void notifyStateChanged(boolean state) {
        CheckboxMenuItem cb = (CheckboxMenuItem)target;
        ItemEvent e = new ItemEvent(cb,
                          ItemEvent.ITEM_STATE_CHANGED,
                          cb.getLabel(),
                          state ? ItemEvent.SELECTED : ItemEvent.DESELECTED);
        postEvent(e);
    }


    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void action(long when, int modifiers, boolean state) {
        final CheckboxMenuItem cb = (CheckboxMenuItem)target;
        final boolean newState = state;

        MToolkit.executeOnEventHandlerThread(cb, new Runnable() {
            public void run() {
                cb.setState(newState);
                notifyStateChanged(newState);
            }
        });
        //Fix for 5005195: MAWT: CheckboxMenuItem fires action events
        //super.action() is not invoked
    } // action()
} // class MCheckboxMenuItemPeer
