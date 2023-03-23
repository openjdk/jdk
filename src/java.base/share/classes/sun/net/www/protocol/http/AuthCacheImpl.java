/*
 * Copyright (c) 2003, 2021, Oracle and/or its affiliates. All rights reserved.
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

package sun.net.www.protocol.http;

import java.lang.ref.Cleaner;
import java.net.Authenticator;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author Michael McMahon
 */
public class AuthCacheImpl implements AuthCache {
    // No blocking IO is performed within the synchronized code blocks
    // in this class, so there is no need to convert this class to using
    // java.util.concurrent.locks
    HashMap<String,LinkedList<AuthCacheValue>> hashtable;
    final Cleaner cleaner;

    public AuthCacheImpl () {
        hashtable = new HashMap<String,LinkedList<AuthCacheValue>>();
        cleaner = Cleaner.create();
    }

    public void setMap (HashMap<String,LinkedList<AuthCacheValue>> map) {
        hashtable = map;
    }

    // call to register Authenticator with Cleaner.

    public void registerAuthenticator(Authenticator auth) {
        cleaner.register(auth, new CleanerAction(AuthenticatorKeys.getKey(auth)));
    }

    // used for testing
    public int mapSize() {
        AtomicInteger count = new AtomicInteger();
        hashtable.forEach((k,v) -> {
            count.addAndGet(v == null ? 0: v.size());
        });
        return count.get();
    }

    // Cleaner action run to remove all entries whose key ends with
    // ;auth=authkey
    // ie. all entries belonging to the given Authenticator

    class CleanerAction implements Runnable {
        private final String authkey;

        CleanerAction(String authkey) {
            this.authkey = ";auth=" + authkey;
        }

        public void run() {
            synchronized(AuthCacheImpl.this) {
                hashtable.forEach((String key,
                                   LinkedList<AuthCacheValue> list) -> {
                    if (key.endsWith(authkey)) {
                        hashtable.remove(key);
                    }
                });
            }
        }
    }

    // put a value in map according to primary key + secondary key which
    // is the path field of AuthenticationInfo
    public synchronized void put (String pkey, AuthCacheValue value) {
        LinkedList<AuthCacheValue> list = hashtable.get (pkey);
        String skey = value.getPath();
        if (list == null) {
            list = new LinkedList<AuthCacheValue>();
            hashtable.put(pkey, list);
        }
        // Check if the path already exists or a super-set of it exists
        ListIterator<AuthCacheValue> iter = list.listIterator();
        while (iter.hasNext()) {
            AuthenticationInfo inf = (AuthenticationInfo)iter.next();
            if (inf.path == null || inf.path.startsWith (skey)) {
                iter.remove ();
            }
        }
        iter.add(value);
    }

    // get a value from map checking both primary
    // and secondary (urlpath) key

    public synchronized AuthCacheValue get (String pkey, String skey) {
        AuthenticationInfo result = null;
        LinkedList<AuthCacheValue> list = hashtable.get (pkey);
        if (list == null || list.size() == 0) {
            return null;
        }
        if (skey == null) {
            // list should contain only one element
            return list.get(0);
        }
        for (AuthCacheValue authCacheValue : list) {
            AuthenticationInfo inf = (AuthenticationInfo) authCacheValue;
            if (skey.startsWith (inf.path)) {
                return inf;
            }
        }
        return null;
    }

    public synchronized void remove (String pkey, AuthCacheValue entry) {
        LinkedList<AuthCacheValue> list = hashtable.get (pkey);
        if (list == null) {
            return;
        }
        if (entry == null) {
            list.clear();
            return;
        }
        ListIterator<AuthCacheValue> iter = list.listIterator ();
        while (iter.hasNext()) {
            AuthenticationInfo inf = (AuthenticationInfo)iter.next();
            if (entry.equals(inf)) {
                iter.remove ();
            }
        }
    }
}
