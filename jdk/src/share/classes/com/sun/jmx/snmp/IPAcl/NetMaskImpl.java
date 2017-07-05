/*
 * Copyright (c) 2002, 2007, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jmx.snmp.IPAcl;

import static com.sun.jmx.defaults.JmxProperties.SNMP_LOGGER;

import java.util.logging.Level;
import java.util.Vector;
import java.util.Enumeration;
import java.io.Serializable;
import java.net.UnknownHostException;
import java.net.InetAddress;

import java.security.Principal;
import java.security.acl.Group;


/**
 * This class is used to represent a subnet mask (a group of hosts matching the same
 * IP mask).
 *
 * @see java.security.acl.Group
 */

class NetMaskImpl extends PrincipalImpl implements Group, Serializable {
    private static final long serialVersionUID = -7332541893877932896L;

    protected byte[] subnet = null;
    protected int prefix = -1;
    /**
     * Constructs an empty group.
     * @exception UnknownHostException Not implemented
     */
    public NetMaskImpl () throws UnknownHostException {
    }

    private byte[] extractSubNet(byte[] b) {
        int addrLength = b.length;
        byte[] subnet = null;
        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(),
                "extractSubNet", "BINARY ARRAY :");
            StringBuffer buff = new StringBuffer();
            for(int i =0; i < addrLength; i++) {
                buff.append((b[i] &0xFF) +":");
            }
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(),
                "extractSubNet", buff.toString());
        }

        // 8 is a byte size. Common to any InetAddress (V4 or V6).
        int fullyCoveredByte = prefix / 8;
        if(fullyCoveredByte == addrLength) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
                   "The mask is the complete address, strange..." + addrLength);
            }
            subnet = b;
            return subnet;
        }
        if(fullyCoveredByte > addrLength) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
                   "The number of covered byte is longer than the address. BUG");
            }
            throw new IllegalArgumentException("The number of covered byte is longer than the address.");
        }
        int partialyCoveredIndex = fullyCoveredByte;
        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Partially covered index : " + partialyCoveredIndex);
        }
        byte toDeal = b[partialyCoveredIndex];
        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Partially covered byte : " + toDeal);
        }

        // 8 is a byte size. Common to any InetAddress (V4 or V6).
        int nbbits = prefix % 8;
        int subnetSize = 0;

        if(nbbits == 0)
        subnetSize = partialyCoveredIndex;
        else
        subnetSize = partialyCoveredIndex + 1;

        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Remains : " + nbbits);
        }

        byte mask = 0;
        for(int i = 0; i < nbbits; i++) {
            mask |= (1 << (7 - i));
        }
        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Mask value : " + (mask & 0xFF));
        }

        byte maskedValue = (byte) ((int)toDeal & (int)mask);

        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Masked byte : "  + (maskedValue &0xFF));
        }
        subnet = new byte[subnetSize];
        if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
            SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
               "Resulting subnet : ");
        }
        for(int i = 0; i < partialyCoveredIndex; i++) {
            subnet[i] = b[i];

            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
                   (subnet[i] & 0xFF) +":");
            }
        }

        if(nbbits != 0) {
            subnet[partialyCoveredIndex] = maskedValue;
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "extractSubNet",
                    "Last subnet byte : " + (subnet[partialyCoveredIndex] &0xFF));
            }
        }
        return subnet;
    }

  /**
   * Constructs a group using the specified subnet mask.
   * THIS ALGORITHM IS V4 and V6 compatible.
   *
   * @exception UnknownHostException if the subnet mask cann't be built.
   */
  public NetMaskImpl (String a, int prefix) throws UnknownHostException {
        super(a);
        this.prefix = prefix;
        subnet = extractSubNet(getAddress().getAddress());
  }

  /**
   * Adds the specified member to the group.
   *
   * @param p the principal to add to this group.
   * @return true if the member was successfully added, false if the
   *      principal was already a member.
   */
  public boolean addMember(Principal p) {
        // we don't need to add members because the ip address is a subnet mask
        return true;
  }

  public int hashCode() {
        return super.hashCode();
  }

  /**
   * Compares this group to the specified object. Returns true if the object
   * passed in matches the group represented.
   *
   * @param p the object to compare with.
   * @return true if the object passed in matches the subnet mask,
   *    false otherwise.
   */
    public boolean equals (Object p) {
        if (p instanceof PrincipalImpl || p instanceof NetMaskImpl){
            PrincipalImpl received = (PrincipalImpl) p;
            InetAddress addr = received.getAddress();
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "equals",
                    "Received Address : " + addr);
            }
            byte[] recAddr = addr.getAddress();
            for(int i = 0; i < subnet.length; i++) {
                if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "equals",
                        "(recAddr[i]) : " + (recAddr[i] & 0xFF));
                    SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "equals",
                        "(recAddr[i] & subnet[i]) : " +
                         ((recAddr[i] & (int)subnet[i]) &0xFF) +
                         " subnet[i] : " + (subnet[i] &0xFF));
                }
                if((recAddr[i] & subnet[i]) != subnet[i]) {
                    if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                        SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "equals",
                            "FALSE");
                    }
                    return false;
                }
            }
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, NetMaskImpl.class.getName(), "equals",
                    "TRUE");
            }
            return true;
        } else
            return false;
    }
  /**
   * Returns true if the passed principal is a member of the group.
   *
   * @param p the principal whose membership is to be checked.
   * @return true if the principal is a member of this group, false otherwise.
   */
  public boolean isMember(Principal p) {
        if ((p.hashCode() & super.hashCode()) == p.hashCode()) return true;
        else return false;
  }

  /**
   * Returns an enumeration which contains the subnet mask.
   *
   * @return an enumeration which contains the subnet mask.
   */
  public Enumeration<? extends Principal> members(){
        Vector<Principal> v = new Vector<Principal>(1);
        v.addElement(this);
        return v.elements();
  }

  /**
   * Removes the specified member from the group. (Not implemented)
   *
   * @param p the principal to remove from this group.
   * @return allways return true.
   */
  public boolean removeMember(Principal p) {
        return true;
  }

  /**
   * Prints a string representation of this group.
   *
   * @return  a string representation of this group.
   */
  public String toString() {
        return ("NetMaskImpl :"+ super.getAddress().toString() + "/" + prefix);
  }

}
