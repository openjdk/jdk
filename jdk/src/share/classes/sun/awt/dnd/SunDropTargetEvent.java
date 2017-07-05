/*
 * Copyright 2000-2004 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.awt.dnd;

import java.awt.Component;
import java.awt.dnd.InvalidDnDOperationException;
import java.awt.event.MouseEvent;

public class SunDropTargetEvent extends MouseEvent {

    public static final int MOUSE_DROPPED = MouseEvent.MOUSE_RELEASED;

    private final SunDropTargetContextPeer.EventDispatcher dispatcher;

    public SunDropTargetEvent(Component source, int id, int x, int y,
                              SunDropTargetContextPeer.EventDispatcher d) {
        super(source, id, System.currentTimeMillis(), 0, x, y, 0, 0, 0,
              false,  MouseEvent.NOBUTTON);
        dispatcher = d;
        dispatcher.registerEvent(this);
    }

    public void dispatch() {
        try {
            dispatcher.dispatchEvent(this);
        } finally {
            dispatcher.unregisterEvent(this);
        }
    }

    public void consume() {
        boolean was_consumed = isConsumed();
        super.consume();
        if (!was_consumed && isConsumed()) {
            dispatcher.unregisterEvent(this);
        }
    }

    public SunDropTargetContextPeer.EventDispatcher getDispatcher() {
        return dispatcher;
    }

    public String paramString() {
        String typeStr = null;

        switch (id) {
        case MOUSE_DROPPED:
            typeStr = "MOUSE_DROPPED"; break;
        default:
            return super.paramString();
        }
        return typeStr + ",(" + getX() + "," + getY() + ")";
    }
}
