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

import java.awt.Component;
import java.awt.Insets;

import sun.lwawt.PlatformComponent;
import sun.lwawt.PlatformWindow;
import sun.lwawt.LWComponentPeer;

import sun.lwawt.macosx.CFRetainedResource;

public class CPlatformComponent extends CFRetainedResource implements PlatformComponent {

    Component target;
    LWComponentPeer peer;
    PlatformWindow platformWindow;

    private native long nativeCreateComponent(long windowLayer);
    private native long nativeSetBounds(long ptr, int x, int y, int width, int height);

    public CPlatformComponent() {
        super(0, true);
    }

    public long getPointer() {
        return ptr;
    }

    public void initialize(Component target, LWComponentPeer peer, PlatformWindow platformWindow) {
        this.target = target;
        this.peer = peer;
        this.platformWindow = platformWindow;

        long windowLayerPtr = platformWindow.getLayerPtr();
        setPtr(nativeCreateComponent(windowLayerPtr));
    }

    // TODO: visibility, z-order

    @Override
    public void setBounds(int x, int y, int width, int height) {
        // translates values from the coordinate system of the top-level window
        // to the coordinate system of the content view
        Insets insets = platformWindow.getPeer().getInsets();
        nativeSetBounds(getPointer(), x - insets.left, y - insets.top, width, height);
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
