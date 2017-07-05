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

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.UnsupportedFlavorException;

import java.awt.dnd.DnDConstants;
import java.awt.dnd.InvalidDnDOperationException;

import java.io.InputStream;

import java.util.Map;

import java.io.IOException;
import sun.awt.dnd.SunDropTargetContextPeer;
import sun.awt.SunToolkit;

/**
 * <p>
 * The MDropTargetContextPeer class is the class responsible for handling
 * the interaction between the Motif DnD system and Java.
 * </p>
 *
 * @since JDK1.2
 *
 */

final class MDropTargetContextPeer extends SunDropTargetContextPeer {

    private long              nativeDropTransfer;

    long                      nativeDataAvailable = 0;
    Object                    nativeData          = null;

    /**
     * create the peer
     */

    static MDropTargetContextPeer createMDropTargetContextPeer() {
        return new MDropTargetContextPeer();
    }

    /**
     * create the peer
     */

    private MDropTargetContextPeer() {
        super();
    }

    protected Object getNativeData(long format) {
        SunToolkit.awtLock();
        if (nativeDropTransfer == 0) {
            nativeDropTransfer = startTransfer(getNativeDragContext(),
                                               format);
        } else {
            addTransfer (nativeDropTransfer, format);
        }

        for (nativeDataAvailable = 0;
             format != nativeDataAvailable;) {
            try {
                SunToolkit.awtLockWait();
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        SunToolkit.awtUnlock();

        return nativeData;
    }

    /**
     * signal drop complete
     */

    protected void doDropDone(boolean success, int dropAction,
                              boolean isLocal) {
        dropDone(getNativeDragContext(), nativeDropTransfer, isLocal,
                 success, dropAction);
    }

    /**
     * notify transfer complete
     */

    private void newData(long format, String type, byte[] data) {
        nativeDataAvailable = format;
        nativeData          = data;

        SunToolkit.awtLockNotifyAll();
    }

    /**
     * notify transfer failed
     */

    private void transferFailed(long format) {
        nativeDataAvailable = format;
        nativeData          = null;

        SunToolkit.awtLockNotifyAll();
    }

    /**
     * schedule a native DnD transfer
     */

    private native long startTransfer(long nativeDragContext, long format);

    /**
     * schedule a native DnD data transfer
     */

    private native void addTransfer(long nativeDropTransfer, long format);

    /**
     * signal that drop is completed
     */

    private native void dropDone(long nativeDragContext, long nativeDropTransfer,
                                 boolean localTx, boolean success, int dropAction);
}
