/*
 * Copyright 1996-2008 Sun Microsystems, Inc.  All Rights Reserved.
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
package sun.awt.windows;

abstract class WObjectPeer {

    static {
        initIDs();
    }

    // The Windows handle for the native widget.
    long pData;
    // if the native peer has been destroyed
    boolean destroyed = false;
    // The associated AWT object.
    Object target;

    private volatile boolean disposed;

    // set from JNI if any errors in creating the peer occur
    protected Error createError = null;

    // used to synchronize the state of this peer
    private final Object stateLock = new Object();

    public static WObjectPeer getPeerForTarget(Object t) {
        WObjectPeer peer = (WObjectPeer) WToolkit.targetToPeer(t);
        return peer;
    }

    public long getData() {
        return pData;
    }

    public Object getTarget() {
        return target;
    }

    public final Object getStateLock() {
        return stateLock;
    }

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    abstract protected void disposeImpl();
    public final void dispose() {
        boolean call_disposeImpl = false;

        synchronized (this) {
            if (!disposed) {
                disposed = call_disposeImpl = true;
            }
        }

        if (call_disposeImpl) {
            disposeImpl();
        }
    }
    protected final boolean isDisposed() {
        return disposed;
    }

    /**
     * Initialize JNI field and method IDs
     */
    private static native void initIDs();
}
