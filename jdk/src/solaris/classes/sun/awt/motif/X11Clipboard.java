/*
 * Copyright 1996-2003 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;

import java.io.IOException;

import java.security.AccessController;

import sun.awt.datatransfer.SunClipboard;
import sun.awt.datatransfer.TransferableProxy;
import sun.awt.datatransfer.DataTransferer;

import sun.security.action.GetIntegerAction;


/**
 * A class which interfaces with the X11 selection service in order to support
 * data transfer via Clipboard operations. Most of the work is provided by
 * sun.awt.datatransfer.DataTransferer.
 *
 * @author Amy Fowler
 * @author Roger Brinkley
 * @author Danila Sinopalnikov
 * @author Alexander Gerasimov
 *
 * @since JDK1.1
 */
public class X11Clipboard extends SunClipboard implements X11SelectionHolder {

    private final X11Selection clipboardSelection;

    private static final Object classLock = new Object();

    private static final int defaultPollInterval = 200;

    private static int pollInterval;

    private static int listenedClipboardsCount;

    /**
     * Creates a system clipboard object.
     */
    public X11Clipboard(String name, String selectionName) {
        super(name);
        clipboardSelection = new X11Selection(selectionName, this);
    }

    protected void setContentsNative(Transferable contents) {
        if (!clipboardSelection.getSelectionOwnership(contents, this)) {
            // Need to figure out how to inform owner the request failed...
            this.owner = null;
            this.contents = null;
        }
    }

    public long getID() {
        return clipboardSelection.atom;
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    public void lostSelectionOwnership() {
        lostOwnershipImpl();
    }

    protected void clearNativeContext() {
        clipboardSelection.clearNativeContext();
    }

    protected long[] getClipboardFormats() {
        return getClipboardFormats(getID());
    }
    private static native long[] getClipboardFormats(long clipboardID);

    protected byte[] getClipboardData(long format)
          throws IOException {
        return getClipboardData(getID(), format);
    }
    private static native byte[] getClipboardData(long clipboardID, long format)
            throws IOException;


    // Called on the toolkit thread under awtLock.
    public void checkChange(long[] formats) {
        if (!clipboardSelection.isOwner()) {
            super.checkChange(formats);
        }
    }

    void checkChangeHere(Transferable contents) {
        if (areFlavorListenersRegistered()) {
            super.checkChange(DataTransferer.getInstance().
                        getFormatsForTransferableAsArray(contents, flavorMap));
        }
    }

    protected void registerClipboardViewerChecked() {
        if (pollInterval <= 0) {
            pollInterval = ((Integer)AccessController.doPrivileged(
                    new GetIntegerAction("awt.datatransfer.clipboard.poll.interval",
                                         defaultPollInterval))).intValue();
            if (pollInterval <= 0) {
                pollInterval = defaultPollInterval;
            }
        }
        synchronized (X11Clipboard.classLock) {
            if (listenedClipboardsCount++ == 0) {
                registerClipboardViewer(pollInterval);
            }
        }
    }

    private native void registerClipboardViewer(int pollInterval);

    protected void unregisterClipboardViewerChecked() {
        synchronized (X11Clipboard.classLock) {
            if (--listenedClipboardsCount == 0) {
                unregisterClipboardViewer();
            }
        }
    }

    private native void unregisterClipboardViewer();

}
