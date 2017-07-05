/*
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
 *
 *  (C) Copyright IBM Corp. 1999 All Rights Reserved.
 *  Copyright 1997 The Open Group Research Institute.  All rights reserved.
 */

package sun.security.krb5.internal.rcache;

import java.util.Hashtable;
import sun.security.krb5.internal.KerberosTime;


/**
 * This class implements Hashtable to store the replay caches.
 *
 * @author Yanni Zhang
 */
public class CacheTable extends Hashtable<String,ReplayCache> {

    private static final long serialVersionUID = -4695501354546664910L;

    private boolean DEBUG = sun.security.krb5.internal.Krb5.DEBUG;
    public CacheTable () {
    }

    /**
     * Puts the client timestamp in replay cache.
     * @params principal the client's principal name.
     * @params time authenticator timestamp.
     */
    public synchronized void put(String principal, AuthTime time, long currTime) {
        ReplayCache rc = super.get(principal);
        if (rc == null) {
            if (DEBUG) {
                System.out.println("replay cache for " + principal + " is null.");
            }
            rc = new ReplayCache(principal, this);
            rc.put(time, currTime);
            super.put(principal, rc);
        }
        else {
            rc.put(time, currTime);
            // re-insert the entry, since rc.put could have removed the entry
            super.put(principal, rc);
            if (DEBUG) {
                System.out.println("replay cache found.");
            }
        }

    }

    /**
     * This method tests if replay cache keeps a record of the authenticator's time stamp.
     * If there is a record (replay attack detected), the server should reject the client request.
     * @params principal the client's principal name.
     * @params time authenticator timestamp.
     * @return null if no record found, else return an <code>AuthTime</code> object.
     */
    public Object get(AuthTime time, String principal) {
        ReplayCache rc = super.get(principal);
        if ((rc != null) && (rc.contains(time))) {
            return time;
        }
        return null;
    }
}
