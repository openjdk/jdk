/*
 * Copyright (c) 2003, Oracle and/or its affiliates. All rights reserved.
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

/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.util;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.NoSuchElementException;

/**
 * A hashtable enumerator class.  This class should remain opaque
 * to the client. It will use the Enumeration interface.
 */
class IdentityHashtableEnumerator implements Enumeration {
    boolean keys;
    int index;
    IdentityHashtableEntry table[];
    IdentityHashtableEntry entry;

    IdentityHashtableEnumerator(IdentityHashtableEntry table[], boolean keys) {
        this.table = table;
        this.keys = keys;
        this.index = table.length;
    }

    public boolean hasMoreElements() {
        if (entry != null) {
            return true;
        }
        while (index-- > 0) {
            if ((entry = table[index]) != null) {
                return true;
            }
        }
        return false;
}

public Object nextElement() {
    if (entry == null) {
        while ((index-- > 0) && ((entry = table[index]) == null));
    }
    if (entry != null) {
            IdentityHashtableEntry e = entry;
        entry = e.next;
        return keys ? e.key : e.value;
    }
        throw new NoSuchElementException("IdentityHashtableEnumerator");
    }
}
