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

package com.apple.eawt;

import java.awt.*;
import java.lang.reflect.*;

import sun.lwawt.macosx.*;
import sun.lwawt.macosx.CImage.Creator;

class _AppDockIconHandler {
    private static native void nativeSetDockMenu(final long cmenu);
    private static native void nativeSetDockIconImage(final long image);
    private static native long nativeGetDockIconImage();
    private static native void nativeSetDockIconBadge(final String badge);

    PopupMenu fDockMenu = null;

    _AppDockIconHandler() { }

    @SuppressWarnings("deprecation")
    public void setDockMenu(final PopupMenu menu) {
        fDockMenu = menu;

        // clear the menu if explicitly passed null
        if (menu == null) {
            nativeSetDockMenu(0);
            return;
        }

        // check if the menu needs a parent (8343136)
        final MenuContainer container = menu.getParent();
        if (container == null) {
            final MenuBar newParent = new MenuBar();
            newParent.add(menu);
            newParent.addNotify();
        }

        // instantiate the menu peer and set the native fDockMenu ivar
        menu.addNotify();
        final long nsMenuPtr = ((CMenu)fDockMenu.getPeer()).getNativeMenu();
        nativeSetDockMenu(nsMenuPtr);
    }

    public PopupMenu getDockMenu() {
        return fDockMenu;
    }

    public void setDockIconImage(final Image image) {
        try {
            final CImage cImage = getCImageCreator().createFromImage(image);
            final long nsImagePtr = getNSImagePtrFrom(cImage);
            nativeSetDockIconImage(nsImagePtr);
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    Image getDockIconImage() {
        try {
            final long dockNSImage = nativeGetDockIconImage();
            if (dockNSImage == 0) return null;
            return getCImageCreator().createImageUsingNativeSize(dockNSImage);
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    void setDockIconBadge(final String badge) {
        nativeSetDockIconBadge(badge);
    }

    static Creator getCImageCreator() {
        try {
            final Method getCreatorMethod = CImage.class.getDeclaredMethod("getCreator", new Class[] {});
            getCreatorMethod.setAccessible(true);
            return (Creator)getCreatorMethod.invoke(null, new Object[] {});
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    static long getNSImagePtrFrom(final CImage cImage) {
        if (cImage == null) return 0;

        try {
            final Field cImagePtrField = CFRetainedResource.class.getDeclaredField("ptr");
            cImagePtrField.setAccessible(true);
            return cImagePtrField.getLong(cImage);
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
