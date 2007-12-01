/*
 * Copyright 1995-1999 Sun Microsystems, Inc.  All Rights Reserved.
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

public class MMenuPeer extends MMenuItemPeer implements MenuPeer {
    native void createMenu(MMenuBarPeer parent);
    native void createSubMenu(MMenuPeer parent);

    void create(MMenuPeer parent) {
        if (parent.nativeCreated) {
            createSubMenu(parent);
            nativeCreated = true;
        }
    }

    protected MMenuPeer() {
    }

    public MMenuPeer(Menu target) {
        this.target = target;
        MenuContainer parent = getParent_NoClientCode(target);

        if (parent instanceof MenuBar) {
            MMenuBarPeer mb = (MMenuBarPeer) MToolkit.targetToPeer(parent);
            createMenu(mb);
            nativeCreated = true;
        } else if (parent instanceof Menu) {
            MMenuPeer m = (MMenuPeer) MToolkit.targetToPeer(parent);
            create(m);
        } else {
            throw new IllegalArgumentException("unknown menu container class");
        }
    }

    public void addSeparator() {
    }
    public void addItem(MenuItem item) {
    }
    public void delItem(int index) {
    }

    void destroyNativeWidget() {
        // We do not need to synchronize this method because the caller
        // always holds the tree lock

        if (nativeCreated) {
            Menu menu = (Menu) target;
            int nitems = menu.getItemCount();
            for (int i = 0 ; i < nitems ; i++) {
                MMenuItemPeer mipeer =
                    (MMenuItemPeer) MToolkit.targetToPeer(menu.getItem(i));
                mipeer.destroyNativeWidget();
            }
            super.destroyNativeWidget();
        }
    }
    native void pDispose();
}
