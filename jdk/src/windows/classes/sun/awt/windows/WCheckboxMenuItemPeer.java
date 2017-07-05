/*
 * Copyright 1996-2006 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.windows;

import java.awt.*;
import java.awt.peer.*;
import java.awt.event.ItemEvent;

class WCheckboxMenuItemPeer extends WMenuItemPeer implements CheckboxMenuItemPeer {

    // CheckboxMenuItemPeer implementation

    public native void setState(boolean t);

    // Toolkit & peer internals

    WCheckboxMenuItemPeer(CheckboxMenuItem target) {
        super(target, true);
        setState(target.getState());
    }

    // native callbacks

    public void handleAction(final boolean state) {
        final CheckboxMenuItem target = (CheckboxMenuItem)this.target;
        WToolkit.executeOnEventHandlerThread(target, new Runnable() {
            public void run() {
                target.setState(state);
                postEvent(new ItemEvent(target, ItemEvent.ITEM_STATE_CHANGED,
                                        target.getLabel(), (state)
                                          ? ItemEvent.SELECTED
                                          : ItemEvent.DESELECTED));
            }
        });
    }
}
