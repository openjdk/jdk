/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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

package sun.lwawt.macosx;

import java.awt.*;
import java.awt.peer.MenuPeer;

public class CMenu extends CMenuItem implements MenuPeer {
    public CMenu(Menu target) {
        super(target);
    }

    // This way we avoiding invocation of the setters twice
    @Override
    protected void initialize(MenuItem target) {
        setLabel(target.getLabel());
        setEnabled(target.isEnabled());
    }

    @Override
    protected long createModel() {
        CMenuComponent parent = (CMenuComponent)
            LWCToolkit.targetToPeer(getTarget().getParent());

        if (parent instanceof CMenu ||
            parent instanceof CPopupMenu)
        {
            return nativeCreateSubMenu(parent.getModel());
        } else if (parent instanceof CMenuBar) {
            MenuBar parentContainer = (MenuBar)getTarget().getParent();
            boolean isHelpMenu = parentContainer.getHelpMenu() == getTarget();
            int insertionLocation = ((CMenuBar)parent).getNextInsertionIndex();
            return nativeCreateMenu(parent.getModel(),
                                    isHelpMenu, insertionLocation);
        } else {
            throw new InternalError("Parent must be CMenu or CMenuBar");
        }
    }

    @Override
    public void addItem(MenuItem item) {
        // Nothing to do here -- we added it when we created the
        // menu item's peer.
    }

    @Override
    public void delItem(int index) {
        nativeDeleteItem(getModel(), index);
    }

    @Override
    public void setLabel(String label) {
        nativeSetMenuTitle(getModel(), label);
        super.setLabel(label);
    }

    // Note that addSeparator is never called directly from java.awt.Menu,
    // though it is required in the MenuPeer interface.
    @Override
    public void addSeparator() {
        nativeAddSeparator(getModel());
    }

    // Used by ScreenMenuBar to get to the native menu for event handling.
    public long getNativeMenu() {
        return nativeGetNSMenu(getModel());
    }

    private native long nativeCreateMenu(long parentMenuPtr,
                                         boolean isHelpMenu,
                                         int insertionLocation);
    private native long nativeCreateSubMenu(long parentMenuPtr);
    private native void nativeSetMenuTitle(long menuPtr, String title);
    private native void nativeAddSeparator(long menuPtr);
    private native void nativeDeleteItem(long menuPtr, int index);

    // Returns a retained NSMenu object! We have to explicitly
    // release at some point!
    private native long nativeGetNSMenu(long menuPtr);
}
