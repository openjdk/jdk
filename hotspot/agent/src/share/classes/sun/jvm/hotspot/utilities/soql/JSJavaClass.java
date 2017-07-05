/*
 * Copyright 2004-2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 *
 */

package sun.jvm.hotspot.utilities.soql;

import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.utilities.*;

public class JSJavaClass extends JSJavaInstance {
    public JSJavaClass(Instance instance, JSJavaKlass jk,  JSJavaFactory fac) {
        super(instance, fac);
        this.jklass = jk;
    }

    public JSJavaKlass getJSJavaKlass() {
        return jklass;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Class (address=");
        buf.append(getOop().getHandle());
        buf.append(", name=");
        buf.append(jklass.getName());
        buf.append(')');
        return buf.toString();
    }

    protected Object getFieldValue(String name) {
        return jklass.getMetaClassFieldValue(name);
    }

    protected String[] getFieldNames() {
        return jklass.getMetaClassFieldNames();
    }

    protected boolean hasField(String name) {
        return jklass.hasMetaClassField(name);
    }

    private JSJavaKlass jklass;
}
