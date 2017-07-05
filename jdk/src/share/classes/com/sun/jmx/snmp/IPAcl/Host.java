/*
 * Copyright (c) 1997, 2007, Oracle and/or its affiliates. All rights reserved.
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



// java import
//
import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.Vector;
import java.security.acl.NotOwnerException;

import static com.sun.jmx.defaults.JmxProperties.SNMP_LOGGER;

/**
 * The class defines an abstract representation of a host.
 *
 */
abstract class Host extends SimpleNode implements Serializable {

    public Host(int id) {
        super(id);
    }

    public Host(Parser p, int id) {
        super(p, id);
    }

    protected abstract PrincipalImpl createAssociatedPrincipal()
        throws UnknownHostException;

    protected abstract String getHname();

    public void buildAclEntries(PrincipalImpl owner, AclImpl acl) {
        // Create a principal
        //
        PrincipalImpl p=null;
        try {
            p = createAssociatedPrincipal();
        } catch(UnknownHostException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, Host.class.getName(),
                        "buildAclEntries",
                        "Cannot create ACL entry; got exception", e);
            }
            throw new IllegalArgumentException("Cannot create ACL entry for " + e.getMessage());
        }

        // Create an AclEntry
        //
        AclEntryImpl entry= null;
        try {
            entry = new AclEntryImpl(p);
            // Add permission
            //
            registerPermission(entry);
            acl.addEntry(owner, entry);
        } catch(UnknownHostException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, Host.class.getName(),
                        "buildAclEntries",
                        "Cannot create ACL entry; got exception", e);
            }
            return;
        } catch(NotOwnerException a) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, Host.class.getName(),
                        "buildAclEntries",
                        "Cannot create ACL entry; got exception", a);
            }
            return;
        }
    }

    private void registerPermission(AclEntryImpl entry) {
        JDMHost host= (JDMHost) jjtGetParent();
        JDMManagers manager= (JDMManagers) host.jjtGetParent();
        JDMAclItem acl= (JDMAclItem) manager.jjtGetParent();
        JDMAccess access= acl.getAccess();
        access.putPermission(entry);
        JDMCommunities comm= acl.getCommunities();
        comm.buildCommunities(entry);
    }

    public void buildTrapEntries(Hashtable<InetAddress, Vector<String>> dest) {

        JDMHostTrap host= (JDMHostTrap) jjtGetParent();
        JDMTrapInterestedHost hosts= (JDMTrapInterestedHost) host.jjtGetParent();
        JDMTrapItem trap = (JDMTrapItem) hosts.jjtGetParent();
        JDMTrapCommunity community = trap.getCommunity();
        String comm = community.getCommunity();

        InetAddress add = null;
        try {
            add = java.net.InetAddress.getByName(getHname());
        } catch(UnknownHostException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, Host.class.getName(),
                        "buildTrapEntries",
                        "Cannot create TRAP entry; got exception", e);
            }
            return;
        }

        Vector<String> list = null;
        if (dest.containsKey(add)){
            list = dest.get(add);
            if (!list.contains(comm)){
                list.addElement(comm);
            }
        } else {
            list = new Vector<String>();
            list.addElement(comm);
            dest.put(add,list);
        }
    }

    public void buildInformEntries(Hashtable<InetAddress, Vector<String>> dest) {

        JDMHostInform host= (JDMHostInform) jjtGetParent();
        JDMInformInterestedHost hosts= (JDMInformInterestedHost) host.jjtGetParent();
        JDMInformItem inform = (JDMInformItem) hosts.jjtGetParent();
        JDMInformCommunity community = inform.getCommunity();
        String comm = community.getCommunity();

        InetAddress add = null;
        try {
            add = java.net.InetAddress.getByName(getHname());
        } catch(UnknownHostException e) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, Host.class.getName(),
                        "buildTrapEntries",
                        "Cannot create INFORM entry; got exception", e);
            }
            return;
        }

        Vector<String> list = null;
        if (dest.containsKey(add)){
            list = dest.get(add);
            if (!list.contains(comm)){
                list.addElement(comm);
            }
        } else {
            list = new Vector<String>();
            list.addElement(comm);
            dest.put(add,list);
        }
    }



}
