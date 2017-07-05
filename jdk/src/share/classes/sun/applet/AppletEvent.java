/*
 * Copyright 1997 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.applet;

import java.util.EventObject;


/**
 * AppletEvent class.
 *
 * @author  Sunita Mani
 */

public class AppletEvent extends EventObject {

    private Object arg;
    private int id;


    public AppletEvent(Object source, int id, Object argument) {
        super(source);
        this.arg = argument;
        this.id = id;
    }

    public int getID() {
        return id;
    }

    public Object getArgument() {
        return arg;
    }

    public String toString() {
        String str = getClass().getName() + "[source=" + source + " + id="+ id;
        if (arg != null) {
            str += " + arg=" + arg;
        }
        str += " ]";
        return str;
    }
}
