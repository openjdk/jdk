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

import sun.awt.dnd.SunDropTargetContextPeer;
import sun.awt.dnd.SunDropTargetEvent;

import javax.swing.*;


final class CDropTargetContextPeer extends SunDropTargetContextPeer {

    private long    fNativeDropTransfer = 0;
    private long    fNativeDataAvailable = 0;
    private Object  fNativeData    = null;
    private boolean insideTarget = false;

    Object awtLockAccess = new Object();

    static CDropTargetContextPeer getDropTargetContextPeer() {
        return new CDropTargetContextPeer();
    }

    private CDropTargetContextPeer() {
        super();
    }

    // We block, waiting for an empty event to get posted (CToolkit.invokeAndWait)
    // This is so we finish dispatching DropTarget events before we dispatch the dragDropFinished event (which is a higher priority).
    private void flushEvents(Component c) {
        try {
            LWCToolkit.invokeAndWait(new Runnable() {
                public synchronized void run() {
                }
            }, c);
        }
        catch(Exception e) {
            e.printStackTrace();
        }
    }

    protected Object getNativeData(long format) {
        long nativeDropTarget = this.getNativeDragContext();

        synchronized (awtLockAccess) {
            fNativeDataAvailable = 0;

            if (fNativeDropTransfer == 0) {
                fNativeDropTransfer = startTransfer(nativeDropTarget, format);
            } else {
                addTransfer(nativeDropTarget, fNativeDropTransfer, format);
            }

            while (format != fNativeDataAvailable) {
                try {
                    awtLockAccess.wait();
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        }

        return fNativeData;
    }

    // We need to take care of dragExit message because for some reason it is not being
    // generated for lightweight components
    @Override
    protected void processMotionMessage(SunDropTargetEvent event, boolean operationChanged) {
        Component eventSource = (Component)event.getComponent();
        Point screenPoint = event.getPoint();
        SwingUtilities.convertPointToScreen(screenPoint, eventSource);
        Rectangle screenBounds = new Rectangle(eventSource.getLocationOnScreen().x,
                eventSource.getLocationOnScreen().y,
                eventSource.getWidth(), eventSource.getHeight());
        if(insideTarget) {
            if(!screenBounds.contains(screenPoint)) {
                processExitMessage(event);
                insideTarget = false;
                return;
            }
        } else {
            if(screenBounds.contains(screenPoint)) {
                processEnterMessage(event);
                insideTarget = true;
            } else {
                return;
            }
        }
        super.processMotionMessage(event, operationChanged);
    }

    @Override
    protected void processDropMessage(SunDropTargetEvent event) {
        Component eventSource = (Component)event.getComponent();
        Point screenPoint = event.getPoint();
        SwingUtilities.convertPointToScreen(screenPoint, eventSource);
        Rectangle screenBounds = new Rectangle(eventSource.getLocationOnScreen().x,
                eventSource.getLocationOnScreen().y,
                eventSource.getWidth(), eventSource.getHeight());
        if(screenBounds.contains(screenPoint)) {
            super.processDropMessage(event);
        }
    }

    // Signal drop complete:
    protected void doDropDone(boolean success, int dropAction, boolean isLocal) {
        long nativeDropTarget = this.getNativeDragContext();

        dropDone(nativeDropTarget, fNativeDropTransfer, isLocal, success, dropAction);
    }

    // Notify transfer complete - this is an upcall from getNativeData's native calls:
    private void newData(long format, byte[] data) {
        fNativeDataAvailable = format;
        fNativeData          = data;

        awtLockAccess.notifyAll();
    }

    // Notify transfer failed - this is an upcall from getNativeData's native calls:
    private void transferFailed(long format) {
        fNativeDataAvailable = format;
        fNativeData          = null;

        awtLockAccess.notifyAll();
    }

    // Schedule a native dnd transfer:
    private native long startTransfer(long nativeDropTarget, long format);

    // Schedule a native dnd data transfer:
    private native void addTransfer(long nativeDropTarget, long nativeDropTransfer, long format);

    // Notify drop completed:
    private native void dropDone(long nativeDropTarget, long nativeDropTransfer, boolean isLocal, boolean success, int dropAction);
}
