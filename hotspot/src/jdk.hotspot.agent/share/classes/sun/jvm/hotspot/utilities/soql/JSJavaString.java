/*
 * Copyright (c) 2004, 2007, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

package sun.jvm.hotspot.utilities.soql;

import sun.jvm.hotspot.oops.*;

/**
 * Wraps a java.lang.String instance of the target VM.
 */
public class JSJavaString extends JSJavaInstance {
    public JSJavaString(Instance instance, JSJavaFactory fac) {
        super(instance, fac);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("String (address=");
        buf.append(getOop().getHandle());
        buf.append(", value=");
        buf.append("'");
        buf.append(getString());
        buf.append('\'');
        buf.append(')');
        return buf.toString();
    }

    protected Object getFieldValue(String name) {
        if (name.equals("stringValue")) {
            return getString();
        } else {
            return super.getFieldValue(name);
        }
    }

    protected String[] getFieldNames() {
        String[] fields = super.getFieldNames();
        String[] res = new String[fields.length + 1];
        System.arraycopy(fields, 0, res, 0, fields.length);
        res[fields.length] = "stringValue";
        return res;
    }

    protected boolean hasField(String name) {
        if (name.equals("stringValue")) {
            return true;
        } else {
            return super.hasField(name);
        }
    }

    //-- Internals only below this point
    private String getString() {
        return OopUtilities.stringOopToString(getOop());
    }
}
