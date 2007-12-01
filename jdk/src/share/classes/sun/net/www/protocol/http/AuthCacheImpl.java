/*
 * Copyright 2003 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.net.www.protocol.http;

import java.io.IOException;
import java.net.URL;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Enumeration;
import java.util.HashMap;

/**
 * @author Michael McMahon
 */

public class AuthCacheImpl implements AuthCache {
    HashMap hashtable;

    public AuthCacheImpl () {
        hashtable = new HashMap ();
    }

    public void setMap (HashMap map) {
        hashtable = map;
    }

    // put a value in map according to primary key + secondary key which
    // is the path field of AuthenticationInfo

    public synchronized void put (String pkey, AuthCacheValue value) {
        LinkedList list = (LinkedList) hashtable.get (pkey);
        String skey = value.getPath();
        if (list == null) {
            list = new LinkedList ();
            hashtable.put (pkey, list);
        }
        // Check if the path already exists or a super-set of it exists
        ListIterator iter = list.listIterator();
        while (iter.hasNext()) {
            AuthenticationInfo inf = (AuthenticationInfo)iter.next();
            if (inf.path == null || inf.path.startsWith (skey)) {
                iter.remove ();
            }
        }
        iter.add (value);
    }

    // get a value from map checking both primary
    // and secondary (urlpath) key

    public synchronized AuthCacheValue get (String pkey, String skey) {
        AuthenticationInfo result = null;
        LinkedList list = (LinkedList) hashtable.get (pkey);
        if (list == null || list.size() == 0) {
            return null;
        }
        if (skey == null) {
            // list should contain only one element
            return (AuthenticationInfo)list.get (0);
        }
        ListIterator iter = list.listIterator();
        while (iter.hasNext()) {
            AuthenticationInfo inf = (AuthenticationInfo)iter.next();
            if (skey.startsWith (inf.path)) {
                return inf;
            }
        }
        return null;
    }

    public synchronized void remove (String pkey, AuthCacheValue entry) {
        LinkedList list = (LinkedList) hashtable.get (pkey);
        if (list == null) {
            return;
        }
        if (entry == null) {
            list.clear();
            return;
        }
        ListIterator iter = list.listIterator ();
        while (iter.hasNext()) {
            AuthenticationInfo inf = (AuthenticationInfo)iter.next();
            if (entry.equals(inf)) {
                iter.remove ();
            }
        }
    }
}
