/*
 * Copyright 1996-1998 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.peer.*;

public class MPopupMenuPeer extends MMenuPeer implements PopupMenuPeer {

    static {
        initIDs();
    }

    /* initialize the methodIDs of methods that may be accessed from C */
    private native static void initIDs();

    native void createMenu(MComponentPeer parent);

    void createPopupMenu() {
        if (MMenuItemPeer.getParent_NoClientCode(target) instanceof Component) {
            Component parent = (Component)getParent_NoClientCode(target);
            MComponentPeer parentPeer = (MComponentPeer) MToolkit.targetToPeer(parent);
            if (parentPeer == null) {
                // because the menu isn't a component (sigh) we first have to wait
                // for a failure to map the peer which should only happen for a
                // lightweight container, then find the actual native parent from
                // that component.
                parent = MToolkit.getNativeContainer(parent);
                parentPeer = (MComponentPeer) MToolkit.targetToPeer(parent);
            }
            createMenu(parentPeer);
            nativeCreated = true;
            createItems((Menu)target);

        } else {
            throw new IllegalArgumentException("illegal popup menu container class");
        }
    }

    void createItems(Menu target) {
        int nitems = target.getItemCount();
        MMenuPeer parent = (MMenuPeer)MToolkit.targetToPeer(target);
        for (int i = 0 ; i < nitems ; i++) {
            MenuItem mitem = target.getItem(i);
            MMenuItemPeer mipeer = (MMenuItemPeer)MToolkit.targetToPeer(mitem);
            mipeer.create(parent);
            if (mitem instanceof Menu) {
                createItems((Menu)mitem);
            }
        }
    }

    public MPopupMenuPeer(PopupMenu target) {
        // Do NOT instantiate native widget until just before showing the
        // menu, else right mouse click will cause display to lock up
        // (because of passive grab in Motif)
        //
        this.target = target;
    }

    native void pShow(Event evt, int x, int y, MComponentPeer origin);

    public void show(Event evt) {

        if (!nativeCreated)
            createPopupMenu();

        Component origin = (Component)evt.target;
        MComponentPeer peer = (MComponentPeer) MToolkit.targetToPeer(origin);
        int x = evt.x;
        int y = evt.y;
        if (peer == null) {
            // A failure to map the peer should only happen for a
            // lightweight component, then find the actual native parent from
            // that component.  The event coorinates are going to have to be
            Component nativeOrigin = MToolkit.getNativeContainer(origin);
            peer = (MComponentPeer) MToolkit.targetToPeer(nativeOrigin);

            // remove the event coordinates
            for (Component c = origin; c != nativeOrigin;
                              c = MComponentPeer.getParent_NoClientCode(c)) {
                Point p = c.getLocation();
                x += p.x;
                y += p.y;
            }
        }
        pShow(evt, x, y, peer);
    }

    /**
     * This is the callback function called on the Motif thread by
     * Popup_popdownCB(Widget, XtPointer, XtPointer) in awt_PopupMenu.c.
     */
    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    private void destroyNativeWidgetAfterGettingTreeLock() {

        MToolkit.executeOnEventHandlerThread(target, new Runnable() {
            public void run() {

                Object treeLock = new Button().getTreeLock();
                synchronized (treeLock) {
                    destroyNativeWidget();
                }
            }
        });
    }

    native void pDispose();
} // class MPopupMenuPeer
