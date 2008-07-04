/*
 * Copyright 1995-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.util.Vector;
import java.awt.*;
import java.awt.peer.*;
import java.awt.event.*;
import sun.awt.motif.MInputMethodControl;
import sun.awt.im.*;

class MDialogPeer extends MWindowPeer implements DialogPeer, MInputMethodControl {

    static Vector allDialogs = new Vector();

    MDialogPeer(Dialog target) {

        /* create MWindowPeer object */
        super();

        winAttr.nativeDecor = !target.isUndecorated();
        winAttr.initialFocus = true;
        winAttr.isResizable =  target.isResizable();
        winAttr.initialState = MWindowAttributes.NORMAL;
        winAttr.title = target.getTitle();
        winAttr.icon = null;
        if (winAttr.nativeDecor) {
            winAttr.decorations = winAttr.AWT_DECOR_ALL |
                                  winAttr.AWT_DECOR_MINIMIZE |
                                  winAttr.AWT_DECOR_MAXIMIZE;
        } else {
            winAttr.decorations = winAttr.AWT_DECOR_NONE;
        }
        /* create and init native component */
        init(target);
        allDialogs.addElement(this);
    }

    public void setTitle(String title) {
        pSetTitle(title);
    }

    protected void disposeImpl() {
        allDialogs.removeElement(this);
        super.disposeImpl();
    }

    // NOTE: This method is called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleMoved(int x, int y) {
        postEvent(new ComponentEvent(target, ComponentEvent.COMPONENT_MOVED));
    }

    public void show() {
        pShowModal( ((Dialog)target).isModal() );
        updateAlwaysOnTop(alwaysOnTop);
    }


    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleIconify() {
// Note: These routines are necessary for Coaleseing of native implementations
//       As Dialogs do not currently send Iconify/DeIconify messages but
//       Windows/Frames do.  If this should be made consistent...to do so
//       uncomment the postEvent.
//       postEvent(new WindowEvent((Window)target, WindowEvent.WINDOW_ICONIFIED));
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void handleDeiconify() {
// Note: These routines are necessary for Coaleseing of native implementations
//       As Dialogs do not currently send Iconify/DeIconify messages but
//       Windows/Frames do. If this should be made consistent...to do so
//       uncomment the postEvent.
//       postEvent(new WindowEvent((Window)target, WindowEvent.WINDOW_DEICONIFIED));
    }

    public void blockWindows(java.util.List<Window> toBlock) {
        // do nothing
    }

    @Override
    final boolean isTargetUndecorated() {
        return ((Dialog)target).isUndecorated();
    }
}
