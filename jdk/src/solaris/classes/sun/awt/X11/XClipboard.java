/*
 * Copyright 2003-2007 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.X11;

import java.awt.datatransfer.Transferable;

import java.util.SortedMap;
import java.util.Set;
import java.util.Iterator;
import java.util.HashSet;

import java.io.IOException;

import java.security.AccessController;

import sun.awt.datatransfer.DataTransferer;
import sun.awt.datatransfer.SunClipboard;
import sun.awt.datatransfer.ClipboardTransferable;

import sun.security.action.GetIntegerAction;



/**
 * A class which interfaces with the X11 selection service in order to support
 * data transfer via Clipboard operations.
 */
public class XClipboard extends SunClipboard implements Runnable {
    private final XSelection selection;

    private static final Object classLock = new Object();

    private static final int defaultPollInterval = 200;

    private static int pollInterval;

    private static Set listenedClipboards;


    /**
     * Creates a system clipboard object.
     */
    public XClipboard(String name, String selectionName) {
        super(name);
        selection = new XSelection(XAtom.get(selectionName), this);
    }

    /**
     * The action to be run when we lose ownership
     * NOTE: This method may be called by privileged threads.
     *       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
     */
    public void run() {
        lostOwnershipImpl();
    }

    protected synchronized void setContentsNative(Transferable contents) {
        SortedMap formatMap = DataTransferer.getInstance().getFormatsForTransferable
                (contents, DataTransferer.adaptFlavorMap(flavorMap));
        long[] formats =
            DataTransferer.getInstance().keysToLongArray(formatMap);

        if (!selection.setOwner(contents, formatMap, formats,
                                XToolkit.getCurrentServerTime())) {
            this.owner = null;
            this.contents = null;
        }
    }

    public long getID() {
        return selection.getSelectionAtom().getAtom();
    }

    public synchronized Transferable getContents(Object requestor) {
        if (contents != null) {
            return contents;
        }
        return new ClipboardTransferable(this);
    }

    /* Caller is synchronized on this. */
    protected void clearNativeContext() {
        selection.reset();
    }


    protected long[] getClipboardFormats() {
        return selection.getTargets(XToolkit.getCurrentServerTime());
    }

    protected byte[] getClipboardData(long format) throws IOException {
        return selection.getData(format, XToolkit.getCurrentServerTime());
    }

    // Called on the toolkit thread under awtLock.
    public void checkChange(long[] formats) {
        if (!selection.isOwner()) {
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
        selection.initializeSelectionForTrackingChanges();
        boolean mustSchedule = false;
        synchronized (XClipboard.classLock) {
            if (listenedClipboards == null) {
                listenedClipboards = new HashSet(2);
            }
            mustSchedule = listenedClipboards.isEmpty();
            listenedClipboards.add(this);
        }
        if (mustSchedule) {
            XToolkit.schedule(new CheckChangeTimerTask(), pollInterval);
        }
    }

    private static class CheckChangeTimerTask implements Runnable {
        public void run() {
            for (Iterator iter = listenedClipboards.iterator(); iter.hasNext();) {
                XClipboard clpbrd = (XClipboard)iter.next();
                clpbrd.selection.getTargetsDelayed();
            }
            synchronized (XClipboard.classLock) {
                if (listenedClipboards != null && !listenedClipboards.isEmpty()) {
                    XToolkit.schedule(this, pollInterval);
                }
            }
        }
    }

    protected void unregisterClipboardViewerChecked() {
        selection.deinitializeSelectionForTrackingChanges();
        synchronized (XClipboard.classLock) {
            listenedClipboards.remove(this);
        }
    }

}
