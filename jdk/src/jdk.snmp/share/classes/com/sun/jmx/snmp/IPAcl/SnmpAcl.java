/*
 * Copyright (c) 1997, 2012, Oracle and/or its affiliates. All rights reserved.
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.Vector;
import java.util.Enumeration;
import java.util.HashSet;
import java.security.acl.AclEntry;
import java.security.acl.NotOwnerException;

// SNMP Runtime import
//
import static com.sun.jmx.defaults.JmxProperties.SNMP_LOGGER;
import com.sun.jmx.snmp.InetAddressAcl;

/**
 * Defines an implementation of the {@link com.sun.jmx.snmp.InetAddressAcl InetAddressAcl} interface.
 * <p>
 * In this implementation the ACL information is stored on a flat file and
 * its default location is "$JRE/lib/snmp.acl" - See
 * {@link #getDefaultAclFileName()}
 * <p>
 * <OL>
  *
 * <p><b>This API is a Sun Microsystems internal API  and is subject
 * to change without notice.</b></p>
 */

public class SnmpAcl implements InetAddressAcl, Serializable {
    private static final long serialVersionUID = -6702287103824397063L;

    static final PermissionImpl READ  = new PermissionImpl("READ");
    static final PermissionImpl WRITE = new PermissionImpl("WRITE");

    /**
     * Constructs the Java Dynamic Management(TM) Access Control List
     * based on IP addresses. The ACL will take the given owner name.
     * The current IP address will be the owner of the ACL.
     *
     * @param Owner The name of the ACL Owner.
     *
     * @exception UnknownHostException If the local host is unknown.
     * @exception IllegalArgumentException If the ACL file doesn't exist.
     */
    public SnmpAcl(String Owner)
        throws UnknownHostException, IllegalArgumentException {
        this(Owner,null);
    }

    /**
     * Constructs the Java Dynamic Management(TM) Access Control List
     * based on IP addresses. The ACL will take the given owner name.
     * The current IP address will be the owner of the ACL.
     *
     * @param Owner The name of the ACL Owner.
     * @param aclFileName The name of the ACL File.
     *
     * @exception UnknownHostException If the local host is unknown.
     * @exception IllegalArgumentException If the ACL file doesn't exist.
     */
    public SnmpAcl(String Owner, String aclFileName)
        throws UnknownHostException, IllegalArgumentException {
        trapDestList= new Hashtable<InetAddress, Vector<String>>();
        informDestList= new Hashtable<InetAddress, Vector<String>>();

        // PrincipalImpl() take the current host as entry
        owner = new PrincipalImpl();
        try {
            acl = new AclImpl(owner,Owner);
            AclEntry ownEntry = new AclEntryImpl(owner);
            ownEntry.addPermission(READ);
            ownEntry.addPermission(WRITE);
            acl.addEntry(owner,ownEntry);
        } catch (NotOwnerException ex) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, SnmpAcl.class.getName(),
                    "SnmpAcl(String,String)",
                    "Should never get NotOwnerException as the owner " +
                    "is built in this constructor");
            }
        }
        if (aclFileName == null) setDefaultFileName();
        else setAuthorizedListFile(aclFileName);
        readAuthorizedListFile();
    }

    /**
     * Returns an enumeration of the entries in this ACL. Each element in the
     * enumeration is of type <CODE>java.security.acl.AclEntry</CODE>.
     *
     * @return An enumeration of the entries in this ACL.
     */
    public Enumeration<AclEntry> entries() {
        return acl.entries();
    }

    /**
     * Returns ann enumeration of community strings. Community strings are returned as String.
     * @return The enumeration of community strings.
     */
    public Enumeration<String> communities() {
        HashSet<String> set = new HashSet<String>();
        Vector<String> res = new Vector<String>();
        for (Enumeration<AclEntry> e = acl.entries() ; e.hasMoreElements() ;) {
            AclEntryImpl entry = (AclEntryImpl) e.nextElement();
            for (Enumeration<String> cs = entry.communities();
                 cs.hasMoreElements() ;) {
                set.add(cs.nextElement());
            }
        }
        String[] objs = set.toArray(new String[0]);
        for(int i = 0; i < objs.length; i++)
            res.addElement(objs[i]);

        return res.elements();
    }

    /**
     * Returns the name of the ACL.
     *
     * @return The name of the ACL.
     */
    public String getName() {
        return acl.getName();
    }

    /**
     * Returns the read permission instance used.
     *
     * @return The read permission instance.
     */
    static public PermissionImpl getREAD() {
        return READ;
    }

    /**
     * Returns the write permission instance used.
     *
     * @return  The write permission instance.
     */
    static public PermissionImpl getWRITE() {
        return WRITE;
    }

    /**
     * Get the default name for the ACL file.
     * In this implementation this is "$JRE/lib/snmp.acl"
     * @return The default name for the ACL file.
     **/
    public static String getDefaultAclFileName() {
        final String fileSeparator =
            System.getProperty("file.separator");
        final StringBuilder defaultAclName =
            new StringBuilder(System.getProperty("java.home")).
            append(fileSeparator).append("lib").append(fileSeparator).
            append("snmp.acl");
        return defaultAclName.toString();
    }

    /**
     * Sets the full path of the file containing the ACL information.
     *
     * @param filename The full path of the file containing the ACL information.
     * @throws IllegalArgumentException If the passed ACL file doesn't exist.
     */
    public void setAuthorizedListFile(String filename)
        throws IllegalArgumentException {
        File file = new File(filename);
        if (!file.isFile() ) {
            if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                SNMP_LOGGER.logp(Level.FINEST, SnmpAcl.class.getName(),
                    "setAuthorizedListFile", "ACL file not found: " + filename);
            }
            throw new
                IllegalArgumentException("The specified file ["+file+"] "+
                                         "doesn't exist or is not a file, "+
                                         "no configuration loaded");
        }
        if (SNMP_LOGGER.isLoggable(Level.FINER)) {
            SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                "setAuthorizedListFile", "Default file set to " + filename);
        }
        authorizedListFile = filename;
    }

    /**
     * Resets this ACL to the values contained in the configuration file.
     *
     * @exception NotOwnerException If the principal attempting the reset is not an owner of this ACL.
     * @exception UnknownHostException If IP addresses for hosts contained in the ACL file couldn't be found.
     */
    public void rereadTheFile() throws NotOwnerException, UnknownHostException {
        alwaysAuthorized = false;
        acl.removeAll(owner);
        trapDestList.clear();
        informDestList.clear();
        AclEntry ownEntry = new AclEntryImpl(owner);
        ownEntry.addPermission(READ);
        ownEntry.addPermission(WRITE);
        acl.addEntry(owner,ownEntry);
        readAuthorizedListFile();
    }

    /**
     * Returns the full path of the file used to get ACL information.
     *
     * @return The full path of the file used to get ACL information.
     */
    public String getAuthorizedListFile() {
        return authorizedListFile;
    }

    /**
     * Checks whether or not the specified host has <CODE>READ</CODE> access.
     *
     * @param address The host address to check.
     *
     * @return <CODE>true</CODE> if the host has read permission, <CODE>false</CODE> otherwise.
     */
    public boolean checkReadPermission(InetAddress address) {
        if (alwaysAuthorized) return ( true );
        PrincipalImpl p = new PrincipalImpl(address);
        return acl.checkPermission(p, READ);
    }

    /**
     * Checks whether or not the specified host and community have <CODE>READ</CODE> access.
     *
     * @param address The host address to check.
     * @param community The community associated with the host.
     *
     * @return <CODE>true</CODE> if the pair (host, community) has read permission, <CODE>false</CODE> otherwise.
     */
    public boolean checkReadPermission(InetAddress address, String community) {
        if (alwaysAuthorized) return ( true );
        PrincipalImpl p = new PrincipalImpl(address);
        return acl.checkPermission(p, community, READ);
    }

    /**
     * Checks whether or not a community string is defined.
     *
     * @param community The community to check.
     *
     * @return <CODE>true</CODE> if the community is known, <CODE>false</CODE> otherwise.
     */
    public boolean checkCommunity(String community) {
        return acl.checkCommunity(community);
    }

    /**
     * Checks whether or not the specified host has <CODE>WRITE</CODE> access.
     *
     * @param address The host address to check.
     *
     * @return <CODE>true</CODE> if the host has write permission, <CODE>false</CODE> otherwise.
     */
    public boolean checkWritePermission(InetAddress address) {
        if (alwaysAuthorized) return ( true );
        PrincipalImpl p = new PrincipalImpl(address);
        return acl.checkPermission(p, WRITE);
    }

    /**
     * Checks whether or not the specified host and community have <CODE>WRITE</CODE> access.
     *
     * @param address The host address to check.
     * @param community The community associated with the host.
     *
     * @return <CODE>true</CODE> if the pair (host, community) has write permission, <CODE>false</CODE> otherwise.
     */
    public boolean checkWritePermission(InetAddress address, String community) {
        if (alwaysAuthorized) return ( true );
        PrincipalImpl p = new PrincipalImpl(address);
        return acl.checkPermission(p, community, WRITE);
    }

    /**
     * Returns an enumeration of trap destinations.
     *
     * @return An enumeration of the trap destinations (enumeration of <CODE>InetAddress</CODE>).
     */
    public Enumeration<InetAddress> getTrapDestinations() {
        return trapDestList.keys();
    }

    /**
     * Returns an enumeration of trap communities for a given host.
     *
     * @param i The address of the host.
     *
     * @return An enumeration of trap communities for a given host (enumeration of <CODE>String</CODE>).
     */
    public Enumeration<String> getTrapCommunities(InetAddress i) {
        Vector<String> list = null;
        if ((list = trapDestList.get(i)) != null ) {
            if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                    "getTrapCommunities", "["+i.toString()+"] is in list");
            }
            return list.elements();
        } else {
            list = new Vector<>();
            if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                    "getTrapCommunities", "["+i.toString()+"] is not in list");
            }
            return list.elements();
        }
    }

    /**
     * Returns an enumeration of inform destinations.
     *
     * @return An enumeration of the inform destinations (enumeration of <CODE>InetAddress</CODE>).
     */
    public Enumeration<InetAddress> getInformDestinations() {
        return informDestList.keys();
    }

    /**
     * Returns an enumeration of inform communities for a given host.
     *
     * @param i The address of the host.
     *
     * @return An enumeration of inform communities for a given host (enumeration of <CODE>String</CODE>).
     */
    public Enumeration<String> getInformCommunities(InetAddress i) {
        Vector<String> list = null;
        if ((list = informDestList.get(i)) != null ) {
            if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                    "getInformCommunities", "["+i.toString()+"] is in list");
            }
            return list.elements();
        } else {
            list = new Vector<>();
            if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                    "getInformCommunities", "["+i.toString()+"] is not in list");
            }
            return list.elements();
        }
    }

    /**
     * Converts the input configuration file into ACL.
     */
    private void readAuthorizedListFile() {

        alwaysAuthorized = false;

        if (authorizedListFile == null) {
            if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                    "readAuthorizedListFile", "alwaysAuthorized set to true");
            }
            alwaysAuthorized = true ;
        } else {
            // Read the file content
            Parser parser = null;
            try {
                parser= new Parser(new FileInputStream(getAuthorizedListFile()));
            } catch (FileNotFoundException e) {
                if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_LOGGER.logp(Level.FINEST, SnmpAcl.class.getName(),
                            "readAuthorizedListFile",
                            "The specified file was not found, authorize everybody");
                }
                alwaysAuthorized = true ;
                return;
            }

            try {
                JDMSecurityDefs n = parser.SecurityDefs();
                n.buildAclEntries(owner, acl);
                n.buildTrapEntries(trapDestList);
                n.buildInformEntries(informDestList);
            } catch (ParseException e) {
                if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_LOGGER.logp(Level.FINEST, SnmpAcl.class.getName(),
                        "readAuthorizedListFile", "Got parsing exception", e);
                }
                throw new IllegalArgumentException(e.getMessage());
            } catch (Error err) {
                if (SNMP_LOGGER.isLoggable(Level.FINEST)) {
                    SNMP_LOGGER.logp(Level.FINEST, SnmpAcl.class.getName(),
                        "readAuthorizedListFile", "Got unexpected error", err);
                }
                throw new IllegalArgumentException(err.getMessage());
            }

            for(Enumeration<AclEntry> e = acl.entries(); e.hasMoreElements();) {
                AclEntryImpl aa = (AclEntryImpl) e.nextElement();
                if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                    SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                            "readAuthorizedListFile",
                            "===> " + aa.getPrincipal().toString());
                }
                for (Enumeration<java.security.acl.Permission> eee = aa.permissions();eee.hasMoreElements();) {
                    java.security.acl.Permission perm = eee.nextElement();
                    if (SNMP_LOGGER.isLoggable(Level.FINER)) {
                        SNMP_LOGGER.logp(Level.FINER, SnmpAcl.class.getName(),
                                "readAuthorizedListFile", "perm = " + perm);
                    }
                }
            }
        }
    }

    /**
     * Set the default full path for "snmp.acl" input file.
     * Do not complain if the file does not exists.
     */
    private void setDefaultFileName() {
        try {
            setAuthorizedListFile(getDefaultAclFileName());
        } catch (IllegalArgumentException x) {
            // OK...
        }
    }


    // PRIVATE VARIABLES
    //------------------

    /**
     * Represents the Access Control List.
     */
    private AclImpl acl = null;
    /**
     * Flag indicating whether the access is always authorized.
     * <BR>This is the case if there is no flat file defined.
     */
    private boolean alwaysAuthorized = false;
    /**
     * Represents the Access Control List flat file.
     */
    private String authorizedListFile = null;
    /**
     * Contains the hosts list for trap destination.
     */
    private Hashtable<InetAddress, Vector<String>> trapDestList = null;
    /**
     * Contains the hosts list for inform destination.
     */
    private Hashtable<InetAddress, Vector<String>> informDestList = null;

    private PrincipalImpl owner = null;
}
