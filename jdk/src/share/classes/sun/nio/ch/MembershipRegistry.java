/*
 * Copyright 2007-2008 Sun Microsystems, Inc.  All Rights Reserved.
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

package sun.nio.ch;

import java.nio.channels.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.*;

/**
 * Simple registry of membership keys for a MulticastChannel.
 *
 * Instances of this object are not safe by multiple concurrent threads.
 */

class MembershipRegistry {

    // map multicast group to keys
    private Map<InetAddress,List<MembershipKeyImpl>> groups = null;

    MembershipRegistry() {
    }

    /**
     * Checks registry for membership of the group on the given
     * network interface.
     */
    MembershipKey checkMembership(InetAddress group, NetworkInterface interf,
                                  InetAddress source)
    {
        if (groups != null) {
            List<MembershipKeyImpl> keys = groups.get(group);
            if (keys != null) {
                for (MembershipKeyImpl key: keys) {
                    if (key.getNetworkInterface().equals(interf)) {
                        // already a member to receive all packets so return
                        // existing key or detect conflict
                        if (source == null) {
                            if (key.getSourceAddress() == null)
                                return key;
                            throw new IllegalStateException("Already a member to receive all packets");
                        }

                        // already have source-specific membership so return key
                        // or detect conflict
                        if (key.getSourceAddress() == null)
                            throw new IllegalStateException("Already have source-specific membership");
                        if (source.equals(key.getSourceAddress()))
                            return key;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Add membership to the registry, returning a new membership key.
     */
    void add(MembershipKeyImpl key) {
        InetAddress group = key.getGroup();
        List<MembershipKeyImpl> keys;
        if (groups == null) {
            groups = new HashMap<InetAddress,List<MembershipKeyImpl>>();
            keys = null;
        } else {
            keys = groups.get(group);
        }
        if (keys == null) {
            keys = new LinkedList<MembershipKeyImpl>();
            groups.put(group, keys);
        }
        keys.add(key);
    }

    /**
     * Remove a key from the registry
     */
    void remove(MembershipKeyImpl key) {
        InetAddress group = key.getGroup();
        List<MembershipKeyImpl> keys = groups.get(group);
        if (keys != null) {
            Iterator<MembershipKeyImpl> i = keys.iterator();
            while (i.hasNext()) {
                if (i.next() == key) {
                    i.remove();
                    break;
                }
            }
            if (keys.isEmpty()) {
                groups.remove(group);
            }
        }
    }

    /**
     * Invalidate all keys in the registry
     */
    void invalidateAll() {
        for (InetAddress group: groups.keySet()) {
            for (MembershipKeyImpl key: groups.get(group)) {
                key.invalidate();
            }
        }
    }
}
