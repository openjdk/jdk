/*
 * Copyright (c) 1994, 2003, Oracle and/or its affiliates. All rights reserved.
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

package sun.tools.asm;

import sun.tools.java.*;
import java.util.Hashtable;
import java.util.Enumeration;
import java.util.Arrays;

/**
 * WARNING: The contents of this source file are not part of any
 * supported API.  Code that depends on them does so at its own risk:
 * they are subject to change or removal without notice.
 */
public final
class SwitchData {
    int minValue, maxValue;
    Label defaultLabel = new Label();
    Hashtable tab = new Hashtable();
// JCOV
    Hashtable whereCaseTab = null;
// end JCOV

    /**
     * Get a label
     */
    public Label get(int n) {
        return (Label)tab.get(new Integer(n));
    }

    /**
     * Get a label
     */
    public Label get(Integer n) {
        return (Label)tab.get(n);
    }

    /**
     * Add a label
     */
    public void add(int n, Label lbl) {
        if (tab.size() == 0) {
            minValue = n;
            maxValue = n;
        } else {
            if (n < minValue) {
                minValue = n;
            }
            if (n > maxValue) {
                maxValue = n;
            }
        }
        tab.put(new Integer(n), lbl);
    }

    /**
     * Get the default label
     */
    public Label getDefaultLabel() {
        return defaultLabel;
    }

    /**
     * Return the keys of this enumaration sorted in ascending order
     */
    public synchronized Enumeration sortedKeys() {
        return new SwitchDataEnumeration(tab);
    }

// JCOV
    public void initTableCase() {
        whereCaseTab = new Hashtable();
    }
    public void addTableCase(int index, long where) {
        if (whereCaseTab != null)
            whereCaseTab.put(new Integer(index), new Long(where));
    }
    public void addTableDefault(long where) {
        if (whereCaseTab != null)
            whereCaseTab.put("default", new Long(where));
    }
    public long whereCase(Object key) {
        Long i = (Long) whereCaseTab.get(key);
        return (i == null) ? 0 : i.longValue();
    }
    public boolean getDefault() {
         return (whereCase("default") != 0);
    }
// end JCOV
}

class SwitchDataEnumeration implements Enumeration {
    private Integer table[];
    private int current_index = 0;

    /**
     * Create a new enumeration from the hashtable.  Each key in the
     * hash table will be an Integer, with the value being a label.  The
     * enumeration returns the keys in sorted order.
     */
    SwitchDataEnumeration(Hashtable tab) {
        table = new Integer[tab.size()];
        int i = 0;
        for (Enumeration e = tab.keys() ; e.hasMoreElements() ; ) {
            table[i++] = (Integer)e.nextElement();
        }
        Arrays.sort(table);
        current_index = 0;
    }

    /**
     * Are there more keys to return?
     */
    public boolean hasMoreElements() {
        return current_index < table.length;
    }

    /**
     * Return the next key.
     */
    public Object nextElement() {
        return table[current_index++];
    }
}
