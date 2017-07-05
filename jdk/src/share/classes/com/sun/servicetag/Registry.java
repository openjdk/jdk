/*
 * Copyright (c) 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.servicetag;

import java.io.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static com.sun.servicetag.Util.*;
import static com.sun.servicetag.RegistrationDocument.*;

/**
 * A service tag registry is a XML-based registry containing
 * the list of {@link ServiceTag service tags} installed in the system.
 * The {@code Registry} class provides interfaces
 * to add, remove, update, and get a service tag from a service tag
 * registry.
 * This {@code Registry} class may not be supported
 * on all systems. The {@link #isSupported} method
 * can be called to determine if it is supported.
 * <p>
 * A registry may implement restrictions to only allow certain users
 * to {@link #updateServiceTag update} and
 * to {@link #removeServiceTag remove} a service tag record. Typically,
 * only the owner of the service tag, the owner of the registry
 * and superuser are authorized to update or remove a service tag in
 * the registry.
 *
 * @see <a href="https://sn-tools.central.sun.com/twiki/bin/view/ServiceTags/ServiceTagDevGuideHelper">
 * Service Tag User Guide</a>
 */
public class Registry {

    private static final String STCLIENT_SOLARIS = "/usr/bin/stclient";
    private static final String STCLIENT_LINUX = "/opt/sun/servicetag/bin/stclient";
    // stclient exit value (see sthelper.h)
    private static final int ST_ERR_NOT_AUTH = 245;
    private static final int ST_ERR_REC_NOT_FOUND = 225;

    // The stclient output has to be an exported interface
    private static final String INSTANCE_URN_DESC = "Product instance URN=";
    private static boolean initialized = false;
    private static File stclient = null;
    private static String stclientPath = null;
    private static Registry registry = new Registry();

    // System properties for testing
    private static String SVCTAG_STCLIENT_CMD = "servicetag.stclient.cmd";
    private static String SVCTAG_STHELPER_SUPPORTED = "servicetag.sthelper.supported";

    private Registry() {
    }

    private synchronized static String getSTclient() {
        if (!initialized) {
            // Initialization to determine the platform's stclient pathname
            String os = System.getProperty("os.name");
            if (os.equals("SunOS")) {
                stclient = new File(STCLIENT_SOLARIS);
            } else if (os.equals("Linux")) {
                stclient = new File(STCLIENT_LINUX);
            } else if (os.startsWith("Windows")) {
                stclient = getWindowsStClientFile();
            } else {
                if (isVerbose()) {
                    System.out.println("Running on non-Sun JDK");
                }
            }
            initialized = true;
        }

        boolean supportsHelperClass = true; // default
        if (System.getProperty(SVCTAG_STHELPER_SUPPORTED) != null) {
            // the system property always overrides the default setting
            supportsHelperClass = Boolean.getBoolean(SVCTAG_STHELPER_SUPPORTED);
        }

        if (!supportsHelperClass) {
            // disable system registry
            return null;
        }

        // This is only used for testing
        String path = System.getProperty(SVCTAG_STCLIENT_CMD);
        if (path != null) {
            return path;
        }

        // com.sun.servicetag package has to be compiled with JDK 5 as well
        // JDK 5 doesn't support the File.canExecute() method.
        // Risk not checking isExecute() for the stclient command is very low.
        if (stclientPath == null && stclient != null && stclient.exists()) {
            stclientPath = stclient.getAbsolutePath();
        }
        return stclientPath;
    }

    /**
     * Returns the system service tag registry. The {@code Registry} class
     * may not be supported on some platforms; use the {@link #isSupported}
     * method to determine if it is supported.
     *
     * @return the {@code Registry} object for the system service tag registry.
     *
     * @throws UnsupportedOperationException if the {@code Registry} class is
     * not supported.
     */
    public static Registry getSystemRegistry() {
        if (isSupported()) {
            return registry;
        } else {
            throw new UnsupportedOperationException("Registry class is not supported");
        }
    }

    /**
     * Returns {@code true} if the {@code Registry} class is supported on this system.
     *
     * @return {@code true} if the {@code Registry} class is supported;
     * otherwise, return {@code false}.
     */
    public static synchronized boolean isSupported() {
        return getSTclient() != null;
    }

    private static List<String> getCommandList() {
        // Set up the arguments to call stclient
        List<String> command = new ArrayList<String>();
        if (System.getProperty(SVCTAG_STCLIENT_CMD) != null) {
            // This is for jtreg testing use. This will be set to something
            // like:
            // $JAVA_HOME/bin/java -cp $TEST_DIR \
            //    -Dstclient.registry.path=$TEST_DIR/registry.xml \
            //    SvcTagClient
            //
            // On Windows, the JAVA_HOME and TEST_DIR path could contain
            // space e.g. c:\Program Files\Java\jdk1.6.0_05\bin\java.
            // The SVCTAG_STCLIENT_CMD must be set with a list of
            // space-separated parameters.  If a parameter contains spaces,
            // it must be quoted with '"'.

            String cmd = getSTclient();
            int len = cmd.length();
            int i = 0;
            while (i < len) {
                char separator = ' ';
                if (cmd.charAt(i) == '"') {
                    separator = '"';
                    i++;
                }
                // look for the separator or matched the closing '"'
                int j;
                for (j = i+1; j < len; j++) {
                    if (cmd.charAt(j) == separator) {
                        break;
                    }
                }

                if (i == j-1) {
                    // add an empty parameter
                    command.add("\"\"");
                } else {
                    // double quotes and space are not included
                    command.add(cmd.substring(i,j));
                }

                // skip spaces
                for (i = j+1; i < len; i++) {
                    if (!Character.isSpaceChar(cmd.charAt(i))) {
                        break;
                    }
                }
            }
            if (isVerbose()) {
                System.out.println("Command list:");
                for (String s : command) {
                    System.out.println(s);
                }
            }
        } else {
            command.add(getSTclient());
        }
        return command;
    }

    // Returns null if the service tag record not found;
    // or throw UnauthorizedAccessException or IOException
    // based on the exitValue.
    private static ServiceTag checkReturnError(int exitValue,
                                               String output,
                                               ServiceTag st) throws IOException {
        switch (exitValue) {
            case ST_ERR_REC_NOT_FOUND:
                return null;
            case ST_ERR_NOT_AUTH:
                if (st != null) {
                    throw new UnauthorizedAccessException(
                        "Not authorized to access " + st.getInstanceURN() +
                        " installer_uid=" + st.getInstallerUID());
                } else  {
                    throw new UnauthorizedAccessException(
                        "Not authorized:" + output);
                }
            default:
                throw new IOException("stclient exits with error" +
                     " (" + exitValue + ")\n" + output);
        }
    }

    /**
     * Adds a service tag to this registry.
     * If the given service tag has an empty <tt>instance_urn</tt>,
     * this helper class will generate a URN and place it in the
     * copy of the service tag in this registry.
     * This method will return the {@code ServiceTag} representing
     * the service tag entry to this registry.
     *
     * @param st {@code ServiceTag} object
     * @return a {@code ServiceTag} object representing the service tag
     *         entry to this registry.
     *
     * @throws IllegalArgumentException if a service tag of the same
     * <tt>instance_urn</tt> already exists in this registry.
     *
     * @throws java.io.IOException if an I/O error occurs in this operation.
     */
    public ServiceTag addServiceTag(ServiceTag st) throws IOException {
        List<String> command = getCommandList();
        command.add("-a");
        if (st.getInstanceURN().length() > 0) {
            ServiceTag sysSvcTag = getServiceTag(st.getInstanceURN());
            if (sysSvcTag != null) {
                throw new IllegalArgumentException("Instance_urn = " +
                    st.getInstanceURN() + " already exists");
            }
            command.add("-i");
            command.add(st.getInstanceURN());
        }
        command.add("-p");
        command.add(st.getProductName());
        command.add("-e");
        command.add(st.getProductVersion());
        command.add("-t");
        command.add(st.getProductURN());
        if (st.getProductParentURN().length() > 0) {
            command.add("-F");
            command.add(st.getProductParentURN());
        }
        command.add("-P");
        command.add(st.getProductParent());
        if (st.getProductDefinedInstanceID().length() > 0) {
            command.add("-I");
            command.add(st.getProductDefinedInstanceID());
        }
        command.add("-m");
        command.add(st.getProductVendor());
        command.add("-A");
        command.add(st.getPlatformArch());
        command.add("-z");
        command.add(st.getContainer());
        command.add("-S");
        command.add(st.getSource());

        BufferedReader in = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            String output = commandOutput(p);
            if (isVerbose()) {
                System.out.println("Output from stclient -a command:");
                System.out.println(output);
            }
            String urn = "";
            if (p.exitValue() == 0) {
                // Obtain the instance urn from the stclient output
                in = new BufferedReader(new StringReader(output));
                String line = null;
                while ((line = in.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith(INSTANCE_URN_DESC)) {
                        urn = line.substring(INSTANCE_URN_DESC.length());
                        break;
                    }
                }
                if (urn.length() == 0) {
                    throw new IOException("Error in creating service tag:\n" +
                        output);
                }
                return getServiceTag(urn);
            } else {
                return checkReturnError(p.exitValue(), output, st);
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    /**
     * Removes a service tag of the given <tt>instance_urn</tt> from this
     * registry.
     *
     * @param instanceURN the <tt>instance_urn</tt> of the service tag
     *        to be removed.
     *
     * @return the {@code ServiceTag} object removed from this registry;
     * or {@code null} if the service tag does not exist in this registry.
     *
     * @throws UnauthorizedAccessException if the user is not authorized to
     * remove the service tag of the given <tt>instance_urn</tt>
     * from this registry.
     *
     * @throws java.io.IOException if an I/O error occurs in this operation.
     */
    public ServiceTag removeServiceTag(String instanceURN) throws IOException {
        ServiceTag st = getServiceTag(instanceURN);
        if (st == null) {
            return null;
        }

        List<String> command = getCommandList();
        command.add("-d");
        command.add("-i");
        command.add(instanceURN);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        String output = commandOutput(p);
        if (isVerbose()) {
            System.out.println("Output from stclient -d command:");
            System.out.println(output);
        }
        if (p.exitValue() == 0) {
            return st;
        } else {
            return checkReturnError(p.exitValue(), output, st);
        }
    }

    /**
     * Updates the <tt>product_defined_instance_id</tt> in the service tag
     * of the specified <tt>instance_urn</tt> in this registry.
     *
     * @param instanceURN the <tt>instance_urn</tt> of the service tag to be updated.
     * @param productDefinedInstanceID the value of the
     * <tt>product_defined_instance_id</tt> to be set.
     *
     * @return the updated {@code ServiceTag} object;
     * or {@code null} if the service tag does not exist in this
     * registry.
     *
     * @throws UnauthorizedAccessException if the user is not authorized to
     * update the service tag from this registry.
     *
     * @throws IOException if an I/O error occurs in this operation.
     */
    public ServiceTag updateServiceTag(String instanceURN,
                                       String productDefinedInstanceID)
            throws IOException {
        ServiceTag svcTag = getServiceTag(instanceURN);
        if (svcTag == null) {
            return null;
        }

        List<String> command = getCommandList();
        command.add("-u");
        command.add("-i");
        command.add(instanceURN);
        command.add("-I");
        if (productDefinedInstanceID.length() > 0) {
            command.add(productDefinedInstanceID);
        } else {
            command.add("\"\"");
        }

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        String output = commandOutput(p);
        if (isVerbose()) {
            System.out.println("Output from stclient -u command:");
            System.out.println(output);
        }

        if (p.exitValue() == 0) {
            return getServiceTag(instanceURN);
        } else {
            return checkReturnError(p.exitValue(), output, svcTag);
        }
    }

    /**
     * Returns a {@code ServiceTag} object of the given  <tt>instance_urn</tt>
     * in this registry.
     *
     * @param instanceURN the  <tt>instance_urn</tt> of the service tag
     * @return a {@code ServiceTag} object of the given <tt>instance_urn</tt>
     * in this registry; or {@code null} if not found.
     *
     * @throws java.io.IOException if an I/O error occurs in this operation.
     */
    public ServiceTag getServiceTag(String instanceURN) throws IOException {
        if (instanceURN == null) {
            throw new NullPointerException("instanceURN is null");
        }

        List<String> command = getCommandList();
        command.add("-g");
        command.add("-i");
        command.add(instanceURN);

        ProcessBuilder pb = new ProcessBuilder(command);
        Process p = pb.start();
        String output = commandOutput(p);
        if (isVerbose()) {
            System.out.println("Output from stclient -g command:");
            System.out.println(output);
        }
        if (p.exitValue() == 0) {
            return parseServiceTag(output);
        } else {
            return checkReturnError(p.exitValue(), output, null);
        }
    }

    private ServiceTag parseServiceTag(String output) throws IOException {
        BufferedReader in = null;
        try {
            Properties props = new Properties();
            // parse the service tag output from stclient
            in = new BufferedReader(new StringReader(output));
            String line = null;
            while ((line = in.readLine()) != null) {
                if ((line = line.trim()).length() > 0) {
                    String[] ss = line.trim().split("=", 2);
                    if (ss.length == 2) {
                        props.setProperty(ss[0].trim(), ss[1].trim());
                    } else {
                        props.setProperty(ss[0].trim(), "");
                    }
                }
            }

            String urn = props.getProperty(ST_NODE_INSTANCE_URN);
            String productName = props.getProperty(ST_NODE_PRODUCT_NAME);
            String productVersion = props.getProperty(ST_NODE_PRODUCT_VERSION);
            String productURN = props.getProperty(ST_NODE_PRODUCT_URN);
            String productParent = props.getProperty(ST_NODE_PRODUCT_PARENT);
            String productParentURN = props.getProperty(ST_NODE_PRODUCT_PARENT_URN);
            String productDefinedInstanceID =
                props.getProperty(ST_NODE_PRODUCT_DEFINED_INST_ID);
            String productVendor = props.getProperty(ST_NODE_PRODUCT_VENDOR);
            String platformArch = props.getProperty(ST_NODE_PLATFORM_ARCH);
            String container = props.getProperty(ST_NODE_CONTAINER);
            String source = props.getProperty(ST_NODE_SOURCE);
            int installerUID =
                Util.getIntValue(props.getProperty(ST_NODE_INSTALLER_UID));
            Date timestamp =
                Util.parseTimestamp(props.getProperty(ST_NODE_TIMESTAMP));

            return new ServiceTag(urn,
                                  productName,
                                  productVersion,
                                  productURN,
                                  productParent,
                                  productParentURN,
                                  productDefinedInstanceID,
                                  productVendor,
                                  platformArch,
                                  container,
                                  source,
                                  installerUID,
                                  timestamp);
        } finally {
            if (in != null) {
                in.close();
            }
        }

    }

    /**
     * Returns the service tags of the specified
     * <tt>product_urn</tt> in this registry.
     *
     * @param productURN the  <tt>product_urn</tt> to look up
     * @return a {@code Set} of {@code ServiceTag} objects
     * of the specified <tt>product_urn</tt> in this registry.
     *
     * @throws java.io.IOException if an I/O error occurs in this operation.
     */
    public Set<ServiceTag> findServiceTags(String productURN) throws IOException {
        if (productURN == null) {
            throw new NullPointerException("productURN is null");
        }

        List<String> command = getCommandList();
        command.add("-f");
        command.add("-t");
        command.add(productURN);

        BufferedReader in = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            Process p = pb.start();
            String output = commandOutput(p);

            Set<ServiceTag> instances = new HashSet<ServiceTag>();
            if (p.exitValue() == 0) {
                // parse the service tag output from stclient
                in = new BufferedReader(new StringReader(output));
                String line = null;
                while ((line = in.readLine()) != null) {
                    String s = line.trim();
                    if (s.startsWith("urn:st:")) {
                        instances.add(getServiceTag(s));
                    }
                }
            } else {
                checkReturnError(p.exitValue(), output, null);
            }
            return instances;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }
}
