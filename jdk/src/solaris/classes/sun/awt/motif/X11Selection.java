/*
 * Copyright 1996-2005 Sun Microsystems, Inc.  All Rights Reserved.
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

import java.awt.Toolkit;

import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.FlavorMap;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.SystemFlavorMap;

import java.util.Map;
import java.util.SortedMap;
import java.util.Vector;

import sun.awt.AppContext;
import sun.awt.SunToolkit;

import sun.awt.datatransfer.DataTransferer;

/*
 * Implements a general interface to the X11 selection mechanism.
 *
 * @author Amy Fowler
 * @author Roger Brinkley
 * @author Danila Sinopalnikov
 * @author Alexander Gerasimov
 *
 * @since JDK1.1
 */
public class X11Selection {

    static FlavorMap flavorMap = SystemFlavorMap.getDefaultFlavorMap();

    static Vector selections;

    long atom;

    private X11Clipboard clipboard;
    private X11SelectionHolder holder;
    private Transferable contents;

    private boolean disposed = false;
    private byte[] data = null;
    private boolean dataAvailable = false;
    private static final Object source = new Object();

    static {
        // 4154170:  Need to ensure the the toolkit is initialized prior
        // to executing this initializer
        Toolkit toolkit = Toolkit.getDefaultToolkit();

        selections = new Vector();

        initIDs();
        init();

    }

    private static native void initIDs();
    static native void init();

    public X11Selection(String name, X11Clipboard clipboard) {
        atom = ((MDataTransferer)DataTransferer.getInstance()).getAtomForTarget(name);
        selections.addElement(this);
        this.clipboard = clipboard;
    }

    private static Object[] getSelectionsArray() {
        return selections.toArray();
    }

   /*
    * methods for acting as selection provider
    */
    native boolean pGetSelectionOwnership(Object source,
                                          Transferable transferable,
                                          long[] formats,
                                          Map formatMap,
                                          X11SelectionHolder holder);

    boolean getSelectionOwnership(Transferable contents,
                                  X11SelectionHolder holder) {
        SortedMap formatMap =
            DataTransferer.getInstance().getFormatsForTransferable
                (contents, DataTransferer.adaptFlavorMap(flavorMap));
        long[] formats =
            DataTransferer.getInstance().keysToLongArray(formatMap);
        SunToolkit.insertTargetMapping(source, AppContext.getAppContext());

        /*
         * Update 'contents' and 'holder' fields in the native code under
         * AWTLock protection to prevent race with lostSelectionOwnership().
         */
        SunToolkit.awtLock();
        try {
            boolean isOwnerSet = pGetSelectionOwnership(source, contents, formats, formatMap,
                                                        holder);
            if (isOwnerSet) {
                clipboard.checkChangeHere(contents);
            }
            return isOwnerSet;
        } finally {
            SunToolkit.awtUnlock();
        }
    }

    // To be MT-safe this method should be called under awtLock.
    boolean isOwner() {
        return holder != null;
    }

    // NOTE: This method may be called by privileged threads.
    //       DO NOT INVOKE CLIENT CODE ON THIS THREAD!
    private void lostSelectionOwnership() {
        if (holder != null) {
            holder.lostSelectionOwnership();
            holder = null;
        }
        contents = null;
    }

    native void clearNativeContext();

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    protected void disposeImpl() {
        selections.removeElement(this);
    }

    public final void dispose() {
        boolean call_disposeImpl = false;

        if (!disposed) {
            synchronized (this) {
                if (!disposed) {
                    disposed = call_disposeImpl = true;
                }
            }
        }

        if (call_disposeImpl) {
            disposeImpl();
        }
    }

    /**
     * Finds out all selections that have flavor listeners registered
     * and returns their atoms.
     * Upcall from native code.
     *
     * @return an array of selection atoms
     */
    private static long[] getSelectionAtomsToCheckChange() {
        Object[] sels = getSelectionsArray();
        long[] idArray = new long[sels.length];
        int count = 0;

        for (int i = 0; i < sels.length; i++) {
            X11Clipboard clipboard = ((X11Selection)sels[i]).clipboard;
            if (clipboard.areFlavorListenersRegistered()) {
                idArray[count++] = clipboard.getID();
            }
        }

        long[] atomArray = new long[count];
        System.arraycopy(idArray, 0, atomArray, 0, atomArray.length);

        return atomArray;
    }

    /**
     * Upcall from native code.
     */
    private void checkChange(long[] formats) {
        clipboard.checkChange(formats);
    }
}
