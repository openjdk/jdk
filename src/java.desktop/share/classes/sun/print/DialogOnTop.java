/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package sun.print;

import javax.print.attribute.Attribute;
import javax.print.attribute.PrintRequestAttribute;

/*
 * An implementation class used to request the dialog be set always-on-top.
 * It needs to be read and honoured by the dialog code which will use
 * java.awt.Window.setAlwaysOnTop(true) in cases where it is supported.
 */
public class DialogOnTop implements PrintRequestAttribute {

    private static final long serialVersionUID = -1901909867156076547L;

    long id;

    public DialogOnTop() {
    }

    public DialogOnTop(long id) {
        this.id = id;
    }

    public final Class<? extends Attribute> getCategory() {
        return DialogOnTop.class;
    }

    public long getID() {
        return id;
    }

    public final String getName() {
        return "dialog-on-top";
    }

    public String toString() {
       return "dialog-on-top";
    }
}
