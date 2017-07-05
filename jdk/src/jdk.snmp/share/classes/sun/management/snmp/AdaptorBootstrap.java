/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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

package sun.management.snmp;

import com.sun.jmx.snmp.daemon.SnmpAdaptorServer;
import com.sun.jmx.snmp.InetAddressAcl;
import com.sun.jmx.snmp.IPAcl.SnmpAcl;
import sun.management.snmp.jvmmib.JVM_MANAGEMENT_MIB;
import sun.management.snmp.jvminstr.JVM_MANAGEMENT_MIB_IMPL;
import sun.management.snmp.jvminstr.NotificationTarget;
import sun.management.snmp.jvminstr.NotificationTargetImpl;
import sun.management.snmp.util.MibLogger;
import sun.management.snmp.util.JvmContextFactory;

import sun.management.Agent;
import sun.management.AgentConfigurationError;
import static sun.management.AgentConfigurationError.*;
import sun.management.FileSystem;

import java.util.List;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;

import java.io.IOException;
import java.io.File;
import java.io.FileInputStream;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * This class initializes and starts the SNMP Adaptor for JSR 163 SNMP
 * Monitoring.
 **/
public final class AdaptorBootstrap {

    private static final MibLogger log = new MibLogger(AdaptorBootstrap.class);

    /**
     * Default values for SNMP configuration properties.
     **/
    public static interface DefaultValues {
        public static final String PORT="161";
        public static final String CONFIG_FILE_NAME="management.properties";
        public static final String TRAP_PORT="162";
        public static final String USE_ACL="true";
        public static final String ACL_FILE_NAME="snmp.acl";
        public static final String BIND_ADDRESS="localhost";
    }

    /**
     * Names of SNMP configuration properties.
     **/
    public static interface PropertyNames {
        public static final String PORT="com.sun.management.snmp.port";
        public static final String CONFIG_FILE_NAME=
            "com.sun.management.config.file";
        public static final String TRAP_PORT=
            "com.sun.management.snmp.trap";
        public static final String USE_ACL=
            "com.sun.management.snmp.acl";
        public static final String ACL_FILE_NAME=
            "com.sun.management.snmp.acl.file";
        public static final String BIND_ADDRESS=
            "com.sun.management.snmp.interface";
    }

    /**
     * We keep a reference - so that we can possibly call
     * terminate(). As of now, terminate() is only called by unit tests
     * (makes it possible to run several testcases sequentially in the
     * same JVM).
     **/
    private SnmpAdaptorServer       adaptor;
    private JVM_MANAGEMENT_MIB_IMPL jvmmib;

    private AdaptorBootstrap(SnmpAdaptorServer snmpas,
                             JVM_MANAGEMENT_MIB_IMPL mib) {
        jvmmib  = mib;
        adaptor = snmpas;
    }

    /**
     * Compute the full path name for a default file.
     * @param basename basename (with extension) of the default file.
     * @return ${JRE}/lib/management/${basename}
     **/
    private static String getDefaultFileName(String basename) {
        final String fileSeparator = File.separator;
        return System.getProperty("java.home") + fileSeparator + "lib" +
            fileSeparator + "management" + fileSeparator + basename;
    }

    /**
     * Retrieve the Trap Target List from the ACL file.
     **/
    @SuppressWarnings("unchecked")
    private static List<NotificationTarget> getTargetList(InetAddressAcl acl,
                                                          int defaultTrapPort) {
        final ArrayList<NotificationTarget> result =
                new ArrayList<>();
        if (acl != null) {
            if (log.isDebugOn())
                log.debug("getTargetList",Agent.getText("jmxremote.AdaptorBootstrap.getTargetList.processing"));

            final Enumeration<InetAddress> td = acl.getTrapDestinations();
            for (; td.hasMoreElements() ;) {
                final InetAddress targetAddr = td.nextElement();
                final Enumeration<String> tc =
                    acl.getTrapCommunities(targetAddr);
                for (;tc.hasMoreElements() ;) {
                    final String community = tc.nextElement();
                    final NotificationTarget target =
                        new NotificationTargetImpl(targetAddr,
                                                   defaultTrapPort,
                                                   community);
                    if (log.isDebugOn())
                        log.debug("getTargetList",
                                  Agent.getText("jmxremote.AdaptorBootstrap.getTargetList.adding",
                                                target.toString()));
                    result.add(target);
                }
            }
        }
        return result;
    }

    /**
     * Initializes and starts the SNMP Adaptor Server.
     * If the com.sun.management.snmp.port property is not defined,
     * simply return. Otherwise, attempts to load the config file, and
     * then calls {@link #initialize(java.lang.String, java.util.Properties)}.
     *
     **/
    public static synchronized AdaptorBootstrap initialize() {

        // Load a new properties
        final Properties props = Agent.loadManagementProperties();
        if (props == null) return null;

        final String portStr = props.getProperty(PropertyNames.PORT);

        return initialize(portStr,props);
    }

    /**
     * Initializes and starts the SNMP Adaptor Server.
     **/
    public static synchronized
        AdaptorBootstrap initialize(String portStr, Properties props) {

        // Get port number
        if (portStr.length()==0) portStr=DefaultValues.PORT;
        final int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException x) {
            throw new AgentConfigurationError(INVALID_SNMP_PORT, x, portStr);
        }

        if (port < 0) {
            throw new AgentConfigurationError(INVALID_SNMP_PORT, portStr);
        }

        // Get trap port number
        final String trapPortStr =
            props.getProperty(PropertyNames.TRAP_PORT,
                              DefaultValues.TRAP_PORT);

        final int trapPort;
        try {
            trapPort = Integer.parseInt(trapPortStr);
        } catch (NumberFormatException x) {
            throw new AgentConfigurationError(INVALID_SNMP_TRAP_PORT, x, trapPortStr);
        }

        if (trapPort < 0) {
            throw new AgentConfigurationError(INVALID_SNMP_TRAP_PORT, trapPortStr);
        }

        // Get bind address
        final String addrStr =
            props.getProperty(PropertyNames.BIND_ADDRESS,
                              DefaultValues.BIND_ADDRESS);

        // Get ACL File
        final String defaultAclFileName   =
            getDefaultFileName(DefaultValues.ACL_FILE_NAME);
        final String aclFileName =
            props.getProperty(PropertyNames.ACL_FILE_NAME,
                               defaultAclFileName);
        final String  useAclStr =
            props.getProperty(PropertyNames.USE_ACL,DefaultValues.USE_ACL);
        final boolean useAcl =
            Boolean.valueOf(useAclStr).booleanValue();

        if (useAcl) checkAclFile(aclFileName);

        AdaptorBootstrap adaptor = null;
        try {
            adaptor = getAdaptorBootstrap(port, trapPort, addrStr,
                                          useAcl, aclFileName);
        } catch (Exception e) {
            throw new AgentConfigurationError(AGENT_EXCEPTION, e, e.getMessage());
        }
        return adaptor;
    }

    private static AdaptorBootstrap getAdaptorBootstrap
        (int port, int trapPort, String bindAddress, boolean useAcl,
         String aclFileName) {

        final InetAddress address;
        try {
            address = InetAddress.getByName(bindAddress);
        } catch (UnknownHostException e) {
            throw new AgentConfigurationError(UNKNOWN_SNMP_INTERFACE, e, bindAddress);
        }
        if (log.isDebugOn()) {
            log.debug("initialize",
                      Agent.getText("jmxremote.AdaptorBootstrap.getTargetList.starting" +
                      "\n\t" + PropertyNames.PORT + "=" + port +
                      "\n\t" + PropertyNames.TRAP_PORT + "=" + trapPort +
                      "\n\t" + PropertyNames.BIND_ADDRESS + "=" + address +
                      (useAcl?("\n\t" + PropertyNames.ACL_FILE_NAME + "="
                               + aclFileName):"\n\tNo ACL")+
                      ""));
        }

        final InetAddressAcl acl;
        try {
            acl = useAcl ? new SnmpAcl(System.getProperty("user.name"),aclFileName)
                         : null;
        } catch (UnknownHostException e) {
            throw new AgentConfigurationError(UNKNOWN_SNMP_INTERFACE, e, e.getMessage());
        }

        // Create adaptor
        final SnmpAdaptorServer adaptor =
            new SnmpAdaptorServer(acl, port, address);
        adaptor.setUserDataFactory(new JvmContextFactory());
        adaptor.setTrapPort(trapPort);

        // Create MIB
        //
        final JVM_MANAGEMENT_MIB_IMPL mib = new JVM_MANAGEMENT_MIB_IMPL();
        try {
            mib.init();
        } catch (IllegalAccessException x) {
            throw new AgentConfigurationError(SNMP_MIB_INIT_FAILED, x, x.getMessage());
        }

        // Configure the trap destinations.
        //
        mib.addTargets(getTargetList(acl,trapPort));


        // Start Adaptor
        //
        try {
            // Will wait until the adaptor starts or fails to start.
            // If the adaptor fails to start, a CommunicationException or
            // an InterruptedException is thrown.
            //
            adaptor.start(Long.MAX_VALUE);
        } catch (Exception x) {
            Throwable t=x;
            if (x instanceof com.sun.jmx.snmp.daemon.CommunicationException) {
                final Throwable next = t.getCause();
                if (next != null) t = next;
            }
            throw new AgentConfigurationError(SNMP_ADAPTOR_START_FAILED, t,
                                              address + ":" + port,
                                              "(" + t.getMessage() + ")");
        }

        // double check that adaptor is actually started (should always
        // be active, so that exception should never be thrown from here)
        //
        if (!adaptor.isActive()) {
            throw new AgentConfigurationError(SNMP_ADAPTOR_START_FAILED,
                                              address + ":" + port);
        }

        try {
            // Add MIB to adaptor
            //
            adaptor.addMib(mib);

            // Add Adaptor to the MIB
            //
            mib.setSnmpAdaptor(adaptor);
        } catch (RuntimeException x) {
            new AdaptorBootstrap(adaptor,mib).terminate();
            throw x;
        }

        log.debug("initialize",
                  Agent.getText("jmxremote.AdaptorBootstrap.getTargetList.initialize1"));
        log.config("initialize",
                   Agent.getText("jmxremote.AdaptorBootstrap.getTargetList.initialize2",
                                 address.toString(), java.lang.Integer.toString(adaptor.getPort())));
        return new AdaptorBootstrap(adaptor,mib);
    }

    private static void checkAclFile(String aclFileName) {
        if (aclFileName == null || aclFileName.length()==0) {
            throw new AgentConfigurationError(SNMP_ACL_FILE_NOT_SET);
        }
        final File file = new File(aclFileName);
        if (!file.exists()) {
            throw new AgentConfigurationError(SNMP_ACL_FILE_NOT_FOUND, aclFileName);
        }
        if (!file.canRead()) {
            throw new AgentConfigurationError(SNMP_ACL_FILE_NOT_READABLE, aclFileName);
        }

        FileSystem fs = FileSystem.open();
        try {
            if (fs.supportsFileSecurity(file)) {
                if (!fs.isAccessUserOnly(file)) {
                    throw new AgentConfigurationError(SNMP_ACL_FILE_ACCESS_NOT_RESTRICTED,
                        aclFileName);
                }
            }
        } catch (IOException e) {
            throw new AgentConfigurationError(SNMP_ACL_FILE_READ_FAILED, aclFileName);

        }
    }


    /**
     * Get the port on which the adaptor is bound.
     * Returns 0 if the adaptor is already terminated.
     *
     **/
    public synchronized int getPort() {
        if (adaptor != null) return adaptor.getPort();
        return 0;
    }

    /**
     * Stops the adaptor server.
     **/
    public synchronized void terminate() {
        if (adaptor == null) return;

        // Terminate the MIB (deregister NotificationListener from
        // MemoryMBean)
        //
        try {
            jvmmib.terminate();
        } catch (Exception x) {
            // Must not prevent to stop...
            //
            log.debug("jmxremote.AdaptorBootstrap.getTargetList.terminate",
                      x.toString());
        } finally {
            jvmmib=null;
        }

        // Stop the adaptor
        //
        try {
            adaptor.stop();
        } finally {
            adaptor = null;
        }
    }

}
