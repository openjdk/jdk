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
import java.awt.peer.ComponentPeer;
import java.awt.dnd.DropTarget;

import sun.lwawt.LWComponentPeer;
import sun.lwawt.PlatformWindow;


public final class CDropTarget {

    Component        fComponent;
    ComponentPeer    fPeer;
    DropTarget        fDropTarget;
    private long    fNativeDropTarget;

    public static CDropTarget createDropTarget(DropTarget dropTarget, Component component, ComponentPeer peer) {
        return new CDropTarget(dropTarget, component, peer);
    }

    private CDropTarget(DropTarget dropTarget, Component component, ComponentPeer peer) {
        super();

        fDropTarget = dropTarget;
        fComponent = component;
        fPeer = peer;

        long nativePeer = CPlatformWindow.getNativeViewPtr(((LWComponentPeer) peer).getPlatformWindow());
        if (nativePeer == 0L) return; // Unsupported for a window without a native view (plugin)

        // Create native dragging destination:
        fNativeDropTarget = this.createNativeDropTarget(dropTarget, component, peer, nativePeer);
        if (fNativeDropTarget == 0) {
            throw new IllegalStateException("CDropTarget.createNativeDropTarget() failed.");
        }
    }

    public DropTarget getDropTarget() {
        return fDropTarget;
    }

    public void dispose() {
        // Release native dragging destination, if any:
        if (fNativeDropTarget != 0) {
            this.releaseNativeDropTarget(fNativeDropTarget);
            fNativeDropTarget = 0;
        }
    }

    protected native long createNativeDropTarget(DropTarget dropTarget, Component component, ComponentPeer peer, long nativePeer);
    protected native void releaseNativeDropTarget(long nativeDropTarget);
}
