/*
 * Copyright 1996-2004 Sun Microsystems, Inc.  All Rights Reserved.
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
import java.awt.peer.FramePeer;
import sun.awt.EmbeddedFrame;
import java.awt.peer.ComponentPeer;
import sun.awt.*;
import java.awt.*;

public class MEmbeddedFrame extends EmbeddedFrame {

    /**
     * Widget id of the shell widget
     */
    long handle;

    public enum IDKind {
        WIDGET,
        WINDOW
    };

    public MEmbeddedFrame() {
    }

    /**
     * Backward-compatible implementation. This constructor takes widget which represents Frame's
     * shell and uses it as top-level to build hierarchy of top-level widgets upon. It assumes that
     * no XEmbed support is provided.
     * @param widget a valid Xt widget pointer.
     */
    public MEmbeddedFrame(long widget) {
        this(widget, IDKind.WIDGET, false);
    }

    /**
     * New constructor, gets X Window id and allows to specify whether XEmbed is supported by parent
     * X window. Creates hierarchy of top-level widgets under supplied window ID.
     * @param winid a valid X window
     * @param supportsXEmbed whether the host application supports XEMBED protocol
     */
    public MEmbeddedFrame(long winid, boolean supportsXEmbed) {
        this(winid, IDKind.WINDOW, supportsXEmbed);
    }

    /**
     * Creates embedded frame using ID as parent.
     * @param ID parent ID
     * @param supportsXEmbed whether the host application supports XEMBED protocol
     * @param kind if WIDGET, ID represents a valid Xt widget pointer; if WINDOW, ID is a valid X Window
     * ID
     */
    public MEmbeddedFrame(long ID, IDKind kind, boolean supportsXEmbed) {
        super(supportsXEmbed);
        if (kind == IDKind.WIDGET) {
            this.handle = ID;
        } else {
            this.handle = getWidget(ID);
        }
        MToolkit toolkit = (MToolkit)Toolkit.getDefaultToolkit();
        setPeer(toolkit.createEmbeddedFrame(this));
        /*
         * addNotify() creates a LightweightDispatcher that propagates
         * SunDropTargetEvents to subcomponents.
         * NOTE: show() doesn't call addNotify() for embedded frames.
         */
        addNotify();
        show();
    }

    public void synthesizeWindowActivation(boolean b) {
        MEmbeddedFramePeer peer = (MEmbeddedFramePeer)getPeer();
        if (peer != null) {
            if (peer.supportsXEmbed()) {
                if (peer.isXEmbedActive()) {
                    // If XEmbed is active no synthetic focus events are allowed - everything
                    // should go through XEmbed
                    if (b) {
                        peer.requestXEmbedFocus();
                    }
                }
            } else {
                peer.synthesizeFocusInOut(b);
            }
        }
    }

    public void show() {
        if (handle != 0) {
            mapWidget(handle);
        }
        super.show();
    }

    protected boolean traverseOut(boolean direction) {
        MEmbeddedFramePeer xefp = (MEmbeddedFramePeer) getPeer();
        xefp.traverseOut(direction);
        return true;
    }

    // Native methods to handle widget <-> X Windows mapping
    //
    static native long getWidget(long winid);
    static native int mapWidget(long widget);
    public void registerAccelerator(AWTKeyStroke stroke) {
        MEmbeddedFramePeer xefp = (MEmbeddedFramePeer) getPeer();
        if (xefp != null) {
            xefp.registerAccelerator(stroke);
        }
    }
    public void unregisterAccelerator(AWTKeyStroke stroke) {
        MEmbeddedFramePeer xefp = (MEmbeddedFramePeer) getPeer();
        if (xefp != null) {
            xefp.unregisterAccelerator(stroke);
        }
    }
}
