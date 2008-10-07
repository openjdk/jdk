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
import java.io.IOException;
import java.util.HashSet;

/**
 * MembershipKey implementation.
 */

class MembershipKeyImpl
    extends MembershipKey
{
    private final MulticastChannel ch;
    private final InetAddress group;
    private final NetworkInterface interf;
    private final InetAddress source;

    // true when key is valid
    private volatile boolean valid = true;

    // lock used when creating or accessing blockedSet
    private Object stateLock = new Object();

    // set of source addresses that are blocked
    private HashSet<InetAddress> blockedSet;

    private MembershipKeyImpl(MulticastChannel ch,
                              InetAddress group,
                              NetworkInterface interf,
                              InetAddress source)
    {
        this.ch = ch;
        this.group = group;
        this.interf = interf;
        this.source = source;
    }

    /**
     * MembershipKey will additional context for IPv4 membership
     */
    static class Type4 extends MembershipKeyImpl {
        private final int groupAddress;
        private final int interfAddress;
        private final int sourceAddress;

        Type4(MulticastChannel ch,
              InetAddress group,
              NetworkInterface interf,
              InetAddress source,
              int groupAddress,
              int interfAddress,
              int sourceAddress)
        {
            super(ch, group, interf, source);
            this.groupAddress = groupAddress;
            this.interfAddress = interfAddress;
            this.sourceAddress = sourceAddress;
        }

        int group() {
            return groupAddress;
        }

        int interfaceAddress() {
            return interfAddress;
        }

        int source() {
            return sourceAddress;
        }
    }

    /**
     * MembershipKey will additional context for IPv6 membership
     */
    static class Type6 extends MembershipKeyImpl {
        private final byte[] groupAddress;
        private final int index;
        private final byte[] sourceAddress;

        Type6(MulticastChannel ch,
              InetAddress group,
              NetworkInterface interf,
              InetAddress source,
              byte[] groupAddress,
              int index,
              byte[] sourceAddress)
        {
            super(ch, group, interf, source);
            this.groupAddress = groupAddress;
            this.index = index;
            this.sourceAddress = sourceAddress;
        }

        byte[] group() {
            return groupAddress;
        }

        int index() {
            return index;
        }

        byte[] source() {
            return sourceAddress;
        }
    }

    public boolean isValid() {
        return valid;
    }

    // package-private
    void invalidate() {
        valid = false;
    }

    public void drop() throws IOException {
        // delegate to channel
        ((DatagramChannelImpl)ch).drop(this);
    }

    @Override
    public MulticastChannel getChannel() {
        return ch;
    }

    @Override
    public InetAddress getGroup() {
        return group;
    }

    @Override
    public NetworkInterface getNetworkInterface() {
        return interf;
    }

    @Override
    public InetAddress getSourceAddress() {
        return source;
    }

    @Override
    public MembershipKey block(InetAddress toBlock)
        throws IOException
    {
        if (source != null)
            throw new IllegalStateException("key is source-specific");

        synchronized (stateLock) {
            if ((blockedSet != null) && blockedSet.contains(toBlock)) {
                // already blocked, nothing to do
                return this;
            }

            ((DatagramChannelImpl)ch).block(this, toBlock);

            // created blocked set if required and add source address
            if (blockedSet == null)
                blockedSet = new HashSet<InetAddress>();
            blockedSet.add(toBlock);
        }
        return this;
    }

    @Override
    public MembershipKey unblock(InetAddress toUnblock)
        throws IOException
    {
        synchronized (stateLock) {
            if ((blockedSet == null) || !blockedSet.contains(toUnblock))
                throw new IllegalStateException("not blocked");

            ((DatagramChannelImpl)ch).unblock(this, toUnblock);

            blockedSet.remove(toUnblock);
        }
        return this;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append('<');
        sb.append(group.getHostAddress());
        sb.append(',');
        sb.append(interf.getName());
        if (source != null) {
            sb.append(',');
            sb.append(source.getHostAddress());
        }
        sb.append('>');
        return sb.toString();
    }
}
