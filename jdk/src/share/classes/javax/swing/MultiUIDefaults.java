/*
 * Copyright 1997-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package javax.swing;

import java.util.Enumeration;
import java.util.Locale;



/**
 *
 * @author Hans Muller
 */
class MultiUIDefaults extends UIDefaults
{
    private UIDefaults[] tables;

    public MultiUIDefaults(UIDefaults[] defaults) {
        super();
        tables = defaults;
    }

    public MultiUIDefaults() {
        super();
        tables = new UIDefaults[0];
    }


    public Object get(Object key)
    {
        Object value = super.get(key);
        if (value != null) {
            return value;
        }

        for (UIDefaults table : tables) {
            value = (table != null) ? table.get(key) : null;
            if (value != null) {
                return value;
            }
        }

        return null;
    }


    public Object get(Object key, Locale l)
    {
        Object value = super.get(key,l);
        if (value != null) {
            return value;
        }

        for (UIDefaults table : tables) {
            value = (table != null) ? table.get(key,l) : null;
            if (value != null) {
                return value;
            }
        }

        return null;
    }


    public int size() {
        int n = super.size();
        for (UIDefaults table : tables) {
            n += (table != null) ? table.size() : 0;
        }
        return n;
    }


    public boolean isEmpty() {
        return size() == 0;
    }


    public Enumeration<Object> keys()
    {
        Enumeration[] enums = new Enumeration[1 + tables.length];
        enums[0] = super.keys();
        for(int i = 0; i < tables.length; i++) {
            UIDefaults table = tables[i];
            if (table != null) {
                enums[i + 1] = table.keys();
            }
        }
        return new MultiUIDefaultsEnumerator(enums);
    }


    public Enumeration<Object> elements()
    {
        Enumeration[] enums = new Enumeration[1 + tables.length];
        enums[0] = super.elements();
        for(int i = 0; i < tables.length; i++) {
            UIDefaults table = tables[i];
            if (table != null) {
                enums[i + 1] = table.elements();
            }
        }
        return new MultiUIDefaultsEnumerator(enums);
    }

    protected void getUIError(String msg) {
        if (tables.length > 0) {
            tables[0].getUIError(msg);
        } else {
            super.getUIError(msg);
        }
    }

    private static class MultiUIDefaultsEnumerator implements Enumeration<Object>
    {
        Enumeration[] enums;
        int n = 0;

        MultiUIDefaultsEnumerator(Enumeration[] enums) {
            this.enums = enums;
        }

        public boolean hasMoreElements() {
            for(int i = n; i < enums.length; i++) {
                Enumeration e = enums[i];
                if ((e != null) && (e.hasMoreElements())) {
                    return true;
                }
            }
            return false;
        }

        public Object nextElement() {
            for(; n < enums.length; n++) {
                Enumeration e = enums[n];
                if ((e != null) && (e.hasMoreElements())) {
                    return e.nextElement();
                }
            }
            return null;
        }
    }


    public Object remove(Object key)
    {
        Object value = super.remove(key);
        if (value != null) {
            return value;
        }

        for (UIDefaults table : tables) {
            value = (table != null) ? table.remove(key) : null;
            if (value != null) {
                return value;
            }
        }

        return null;
    }


    public void clear() {
        super.clear();
        for (UIDefaults table : tables) {
            if (table != null) {
                table.clear();
            }
        }
    }

    public synchronized String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("{");
        Enumeration keys = keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            buf.append(key + "=" + get(key) + ", ");
        }
        int length = buf.length();
        if (length > 1) {
            buf.delete(length-2, length);
        }
        buf.append("}");
        return buf.toString();
    }
}
