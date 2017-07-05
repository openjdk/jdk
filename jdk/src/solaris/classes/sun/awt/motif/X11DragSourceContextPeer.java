/*
 * Copyright 2003-2005 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.Window;

import java.awt.datatransfer.Transferable;

import java.awt.dnd.DragSourceContext;
import java.awt.dnd.DragSourceDragEvent;
import java.awt.dnd.DragSourceDropEvent;
import java.awt.dnd.DragSourceEvent;
import java.awt.dnd.DragGestureEvent;
import java.awt.dnd.InvalidDnDOperationException;

import java.awt.event.InputEvent;

import java.util.Map;

import sun.awt.SunToolkit;

import sun.awt.dnd.SunDragSourceContextPeer;
import sun.awt.dnd.SunDropTargetContextPeer;

/**
 * The X11DragSourceContextPeer class is the class responsible for handling
 * the interaction between the XDnD/Motif DnD subsystem and Java drag sources.
 *
 * @since 1.5
 */
final class X11DragSourceContextPeer extends SunDragSourceContextPeer {

    private static final X11DragSourceContextPeer theInstance =
        new X11DragSourceContextPeer(null);

    /**
     * construct a new X11DragSourceContextPeer
     */

    private X11DragSourceContextPeer(DragGestureEvent dge) {
        super(dge);
    }

    static X11DragSourceContextPeer createDragSourceContextPeer(DragGestureEvent dge) throws InvalidDnDOperationException {
        theInstance.setTrigger(dge);
        return theInstance;
    }

    protected void startDrag(Transferable transferable,
                             long[] formats, Map formatMap) {
        Component component = getTrigger().getComponent();
        Component c = null;
        MWindowPeer wpeer = null;

        for (c = component; c != null && !(c instanceof java.awt.Window);
             c = MComponentPeer.getParent_NoClientCode(c));

        if (c instanceof Window) {
            wpeer = (MWindowPeer)c.getPeer();
        }

        if (wpeer == null) {
            throw new InvalidDnDOperationException(
                "Cannot find top-level for the drag source component");
        }

        startDrag(component,
                  wpeer,
                  transferable,
                  getTrigger().getTriggerEvent(),
                  getCursor(),
                  getCursor() == null ? 0 : getCursor().getType(),
                  getDragSourceContext().getSourceActions(),
                  formats,
                  formatMap);

        /* This implementation doesn't use native context */
        setNativeContext(0);

        SunDropTargetContextPeer.setCurrentJVMLocalSourceTransferable(transferable);
    }

    /**
     * downcall into native code
     */

    private native long startDrag(Component component,
                                  MWindowPeer wpeer,
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
