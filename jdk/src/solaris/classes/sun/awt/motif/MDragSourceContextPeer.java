/*
 * Copyright 1997-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Image;
import java.awt.Point;

import java.awt.datatransfer.Transferable;

import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;

import java.awt.event.InputEvent;

import java.awt.peer.ComponentPeer;
import java.awt.peer.LightweightPeer;

import java.util.Map;
import sun.awt.SunToolkit;
import sun.awt.dnd.SunDragSourceContextPeer;

/**
 * <p>
 * TBC
 * </p>
 *
 * @since JDK1.2
 *
 */

final class MDragSourceContextPeer extends SunDragSourceContextPeer {

    private static final MDragSourceContextPeer theInstance =
        new MDragSourceContextPeer(null);

    /**
     * construct a new MDragSourceContextPeer
     */

    private MDragSourceContextPeer(DragGestureEvent dge) {
        super(dge);
    }

    static MDragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        theInstance.setTrigger(dge);
        return theInstance;
    }

    protected void startDrag(Transferable transferable,
                             long[] formats, Map formatMap) {
        try {
            long nativeCtxtLocal = startDrag(getTrigger().getComponent(),
                                             transferable,
                                             getTrigger().getTriggerEvent(),
                                             getCursor(),
                                             getCursor() == null ? 0 : getCursor().getType(),
                                             getDragSourceContext().getSourceActions(),
                                             formats,
                                             formatMap);
            setNativeContext(nativeCtxtLocal);
        } catch (Exception e) {
            throw new InvalidDnDOperationException("failed to create native peer: " + e);
        }

        if (getNativeContext() == 0) {
            throw new InvalidDnDOperationException("failed to create native peer");
        }

        MDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(transferable);
    }

    /**
     * downcall into native code
     */

    private native long startDrag(Component component,
                                  Transferable transferable,
                                  InputEvent nativeTrigger,
                                  Cursor c, int ctype, int actions,
                                  long[] formats, Map formatMap);

    /**
     * set cursor
     */

    public void setCursor(Cursor c) throws InvalidDnDOperationException {
        SunToolkit.awtLock();
        super.setCursor(c);
        SunToolkit.awtUnlock();
    }

    protected native void setNativeCursor(long nativeCtxt, Cursor c, int cType);

}
