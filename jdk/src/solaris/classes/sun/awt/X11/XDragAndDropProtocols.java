/*
 * Copyright (c) 2003, 2007, Oracle and/or its affiliates. All rights reserved.
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

package sun.awt.X11;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * This class is a registry for the supported drag and drop protocols.
 *
 * @since 1.5
 */
final class XDragAndDropProtocols {
    private final static List dragProtocols;
    private final static List dropProtocols;

    public static final String XDnD = "XDnD";
    public static final String MotifDnD = "MotifDnD";

    static {
        // Singleton listener for all drag source protocols.
        XDragSourceProtocolListener dragSourceProtocolListener =
            XDragSourceContextPeer.getXDragSourceProtocolListener();
        // Singleton listener for all drop target protocols.
        XDropTargetProtocolListener dropTargetProtocolListener =
            XDropTargetContextPeer.getXDropTargetProtocolListener();

        List tDragSourceProtocols = new ArrayList();
        XDragSourceProtocol xdndDragSourceProtocol =
            XDnDDragSourceProtocol.createInstance(dragSourceProtocolListener);
        tDragSourceProtocols.add(xdndDragSourceProtocol);
        XDragSourceProtocol motifdndDragSourceProtocol =
            MotifDnDDragSourceProtocol.createInstance(dragSourceProtocolListener);
        tDragSourceProtocols.add(motifdndDragSourceProtocol);

        List tDropTargetProtocols = new ArrayList();
        XDropTargetProtocol xdndDropTargetProtocol =
            XDnDDropTargetProtocol.createInstance(dropTargetProtocolListener);
        tDropTargetProtocols.add(xdndDropTargetProtocol);
        XDropTargetProtocol motifdndDropTargetProtocol =
            MotifDnDDropTargetProtocol.createInstance(dropTargetProtocolListener);
        tDropTargetProtocols.add(motifdndDropTargetProtocol);

        dragProtocols = Collections.unmodifiableList(tDragSourceProtocols);
        dropProtocols = Collections.unmodifiableList(tDropTargetProtocols);
    }

    static Iterator getDragSourceProtocols() {
        return dragProtocols.iterator();
    }

    static Iterator getDropTargetProtocols() {
        return dropProtocols.iterator();
    }

    /*
     * Returns a XDragSourceProtocol whose name equals to the specified string
     * or null if no such protocol is registered.
     */
    public static XDragSourceProtocol getDragSourceProtocol(String name) {
        // Protocol name cannot be null.
        if (name == null) {
            return null;
        }

        Iterator dragProtocols = XDragAndDropProtocols.getDragSourceProtocols();
        while (dragProtocols.hasNext()) {
            XDragSourceProtocol dragProtocol =
                (XDragSourceProtocol)dragProtocols.next();
            if (dragProtocol.getProtocolName().equals(name)) {
                return dragProtocol;
            }
        }

        return null;
    }

    /*
     * Returns a XDropTargetProtocol which name equals to the specified string
     * or null if no such protocol is registered.
     */
    public static XDropTargetProtocol getDropTargetProtocol(String name) {
        // Protocol name cannot be null.
        if (name == null) {
            return null;
        }

        Iterator dropProtocols = XDragAndDropProtocols.getDropTargetProtocols();
        while (dropProtocols.hasNext()) {
            XDropTargetProtocol dropProtocol =
                (XDropTargetProtocol)dropProtocols.next();
            if (dropProtocol.getProtocolName().equals(name)) {
                return dropProtocol;
            }
        }

        return null;
    }
}
