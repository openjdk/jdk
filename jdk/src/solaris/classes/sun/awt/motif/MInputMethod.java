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

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.awt.peer.ComponentPeer;
import sun.awt.X11InputMethod;
import sun.awt.SunToolkit;

/**
 * Input Method Adapter for XIM (with Motif)
 *
 * @author JavaSoft International
 */
public class MInputMethod extends X11InputMethod {

    public MInputMethod() throws AWTException {
        super();
    }

    protected boolean openXIM() {
        return openXIMNative();
    }

    protected boolean createXIC() {
        MComponentPeer peer = (MComponentPeer)getPeer(clientComponentWindow);
        if (peer == null) {
            return false;
        }
        MComponentPeer tc = null;
        if (peer instanceof MInputMethodControl) {
            tc = ((MInputMethodControl)peer).getTextComponent();
        }
        if (!createXICNative(peer, tc)) {
            return false;
        }
        if (peer instanceof MInputMethodControl) {
            ((MInputMethodControl)peer).addInputMethod(this);
        }
        return true;
    }

    protected void setXICFocus(ComponentPeer peer,
                                    boolean value, boolean active) {
        setXICFocusNative((MComponentPeer)peer, value, active);
    }

    protected Container getParent(Component client) {
        // SECURITY: Use _NoClientCode(), because this thread may
        //           be privileged
        return MComponentPeer.getParent_NoClientCode(client);
    }

    /**
     * Returns peer of the given client component. If the given client component
     * doesn't have peer, peer of the native container of the client is returned.
     */
    protected ComponentPeer getPeer(Component client) {
        MComponentPeer peer = (MComponentPeer)MToolkit.targetToPeer(client);
        if (peer != null)
            return peer;

        Container nativeContainer = MToolkit.getNativeContainer(client);
        peer = (MComponentPeer)MToolkit.targetToPeer(nativeContainer);
        return peer;
    }

    /**
     * Changes the status area configuration that is to be requested
     * by Frame or Dialog.
     */
    void configureStatus() {
        if (isDisposed()) {
            return;
        }

        MComponentPeer peer = (MComponentPeer)getPeer((Window) clientComponentWindow);
        MComponentPeer tc = ((MInputMethodControl)peer).getTextComponent();
        if (tc != null) {
            configureStatusAreaNative(tc);
        }
    }

    /*
     * Subclasses should override disposeImpl() instead of dispose(). Client
     * code should always invoke dispose(), never disposeImpl().
     */
    protected synchronized void disposeImpl() {
        if (clientComponentWindow != null) {
            MComponentPeer peer = (MComponentPeer)getPeer(clientComponentWindow);
            if (peer instanceof MInputMethodControl)
                ((MInputMethodControl)peer).removeInputMethod(this);
            clientComponentWindow = null;
        }

        super.disposeImpl();
    }

    /**
     * @see java.awt.im.spi.InputMethod#removeNotify
     */
    public synchronized void removeNotify() {
        if (MToolkit.targetToPeer(getClientComponent()) != null) {
            dispose();
        } else {
            // We do not have to dispose XICs in case of lightweight component.
            resetXIC();
        }
    }

    /**
     * Changes the internal XIC configurations. This is required the
     * case that addition or elimination of text components has
     * happened in the containment hierarchy. This method is invoked
     * by Frame or Dialog.
     */
    synchronized void reconfigureXIC(MInputMethodControl control) {
        if (!isDisposed()) {
            // Some IM servers require to reset XIC before destroying
            // the XIC. I.e., Destroying XIC doesn't reset the internal
            // state of the IM server. endComposition() takes care of
            // resetting XIC and preedit synchronization. However,
            // there is no client at this point. It is assumed that
            // the previous client is still available for dispatching
            // committed text which maintains client's composition
            // context.
            endComposition();
            resetXICifneeded();
            reconfigureXICNative((MComponentPeer) control, control.getTextComponent());
        }
    }

    protected void awtLock() {
        SunToolkit.awtLock();
    }

    protected void awtUnlock() {
        SunToolkit.awtUnlock();
    }

    /*
     * Native methods
     */
    private native boolean openXIMNative();
    private native boolean createXICNative(MComponentPeer peer, MComponentPeer tc);
    private native void reconfigureXICNative(MComponentPeer peer,
                                            MComponentPeer tc);
    private native void configureStatusAreaNative(MComponentPeer tc);
    private native void setXICFocusNative(MComponentPeer peer,
                                    boolean value, boolean active);
}
