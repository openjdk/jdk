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

/*
 * @bug     6622366
 * @summary Utility class used by other jtreg tests
 */

import com.sun.servicetag.RegistrationData;
import com.sun.servicetag.ServiceTag;
import com.sun.servicetag.Registry;

import java.util.Set;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.io.*;

public class Util {
    public static ServiceTag newServiceTag(File f)
            throws FileNotFoundException, IOException, NumberFormatException {
        return newServiceTag(f, false /* with instance_urn */);
    }

    public static ServiceTag newServiceTag(File f, boolean noInstanceURN)
            throws FileNotFoundException, IOException, NumberFormatException {
        Properties props = new Properties();
        FileReader reader = new FileReader(f);
        try {
            props.load(reader);
        } finally {
            reader.close();
        }
        if (noInstanceURN) {
            return ServiceTag.newInstance(
                            props.getProperty("product_name"),
                            props.getProperty("product_version"),
                            props.getProperty("product_urn"),
                            props.getProperty("product_parent"),
                            props.getProperty("product_parent_urn"),
                            props.getProperty("product_defined_inst_id"),
                            props.getProperty("product_vendor"),
                            props.getProperty("platform_arch"),
                            props.getProperty("container"),
                            props.getProperty("source"));
        } else {
            return ServiceTag.newInstance(
                            props.getProperty("instance_urn"),
                            props.getProperty("product_name"),
                            props.getProperty("product_version"),
                            props.getProperty("product_urn"),
                            props.getProperty("product_parent"),
                            props.getProperty("product_parent_urn"),
                            props.getProperty("product_defined_inst_id"),
                            props.getProperty("product_vendor"),
                            props.getProperty("platform_arch"),
                            props.getProperty("container"),
                            props.getProperty("source"));
        }
    }

    public static boolean matches(ServiceTag st1, ServiceTag st2) {
        if (!st1.getInstanceURN().equals(st2.getInstanceURN())) {
            System.out.println("instance_urn: " + st1.getInstanceURN() +
                " != " + st2.getInstanceURN());
            return false;
        }
        return matchesNoInstanceUrn(st1, st2);
    }

    public static boolean matchesNoInstanceUrn(ServiceTag st1, ServiceTag st2) {
        if (!st1.getProductName().equals(st2.getProductName())) {
            System.out.println("product_name: " + st1.getProductName() +
                " != " + st2.getProductName());
            return false;
        }

        if (!st1.getProductVersion().equals(st2.getProductVersion())) {
            System.out.println("product_version: " + st1.getProductVersion() +
                " != " + st2.getProductVersion());
            return false;
        }
        if (!st1.getProductURN().equals(st2.getProductURN())) {
            System.out.println("product_urn: " + st1.getProductURN() +
                " != " + st2.getProductURN());
            return false;
        }
        if (!st1.getProductParentURN().equals(st2.getProductParentURN())) {
            System.out.println("product_parent_urn: " + st1.getProductParentURN() +
                " != " + st2.getProductParentURN());
            return false;
        }
        if (!st1.getProductParent().equals(st2.getProductParent())) {
            System.out.println("product_parent: " + st1.getProductParent() +
                " != " + st2.getProductParent());
            return false;
        }
        if (!st1.getProductDefinedInstanceID().equals(st2.getProductDefinedInstanceID())) {
            System.out.println("product_defined_inst_id: " +
                st1.getProductDefinedInstanceID() +
                " != " + st2.getProductDefinedInstanceID());
            return false;
        }
        if (!st1.getProductVendor().equals(st2.getProductVendor())) {
            System.out.println("product_vendor: " + st1.getProductVendor() +
                " != " + st2.getProductVendor());
            return false;
        }
        if (!st1.getPlatformArch().equals(st2.getPlatformArch())) {
            System.out.println("platform_arch: " + st1.getPlatformArch() +
                " != " + st2.getPlatformArch());
            return false;
        }
        if (!st1.getContainer().equals(st2.getContainer())) {
            System.out.println("container: " + st1.getContainer() +
                " != " + st2.getContainer());
            return false;
        }
        if (!st1.getSource().equals(st2.getSource())) {
            System.out.println("source: " + st1.getSource() +
                " != " + st2.getSource());
            return false;
        }
        return true;
    }

    public static void checkRegistrationData(String regFile,
                                             Map<String, ServiceTag> stMap)
            throws IOException {
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(regFile));
        RegistrationData registration = RegistrationData.loadFromXML(in);
        Set<ServiceTag> svcTags = registration.getServiceTags();
        if (svcTags.size() != stMap.size()) {
            throw new RuntimeException("Invalid service tag count= " +
                svcTags.size() + " expected=" + stMap.size());
        }
        for (ServiceTag st : svcTags) {
            ServiceTag st1 = stMap.get(st.getInstanceURN());
            if (!matches(st, st1)) {
                System.err.println(st);
                System.err.println(st1);
                throw new RuntimeException("ServiceTag in the registry " +
                    "does not match the one in the map");
            }
        }
    }


    /**
     * Formats the Date into a timestamp string in YYYY-MM-dd HH:mm:ss GMT.
     * @param timestamp Date
     * @return a string representation of the timestamp in the YYYY-MM-dd HH:mm:ss GMT format.
     */
    static String formatTimestamp(Date timestamp) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        return df.format(timestamp);
    }

    /**
     * Parses a timestamp string in YYYY-MM-dd HH:mm:ss GMT format.
     * @param timestamp Timestamp in the YYYY-MM-dd HH:mm:ss GMT format.
     * @return Date
     */
    static Date parseTimestamp(String timestamp) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        df.setTimeZone(TimeZone.getTimeZone("GMT"));
        try {
            return df.parse(timestamp);
        } catch (ParseException e) {
            // should not reach here
            e.printStackTrace();
            return new Date();
        }
    }

    /**
     * Returns the command simulating stclient behavior.
     */
    static String getSvcClientCommand(String stclientRegistry) {
        String regDir = System.getProperty("test.classes");

        StringBuilder sb = new StringBuilder();
        // wrap each argument to the command with double quotes
        sb.append("\"");
        sb.append(System.getProperty("java.home"));
        sb.append(File.separator).append("bin");
        sb.append(File.separator).append("java");
        sb.append("\"");
        sb.append(" -cp ");
        sb.append("\"").append(regDir).append("\"");
        sb.append(" \"-Dstclient.registry.path=");
        sb.append(stclientRegistry).append("\"");
        sb.append(" SvcTagClient");
        return sb.toString();
    }

    private static Registry registry = null;
    private static File registryFile = null;
    /**
     * Returns the Registry processed by SvcTagClient that simulates
     * stclient.
     */
    static synchronized Registry getSvcTagClientRegistry() throws IOException {
        String regDir = System.getProperty("test.classes");
        File f = new File(regDir, "registry.xml");
        if (registry != null) {
            if (!f.equals(registryFile) && f.length() != 0) {
                throw new AssertionError("Has to be empty registry.xml to run in samevm");
            }
            return registry;
        }

        // System.setProperty("servicetag.verbose", "true");
        // enable the helper class
        System.setProperty("servicetag.sthelper.supported", "true");
        registryFile = f;

        String stclientCmd = Util.getSvcClientCommand(registryFile.getCanonicalPath());
        System.out.println("stclient cmd: " + stclientCmd);
        System.setProperty("servicetag.stclient.cmd", stclientCmd);

        // get the Registry object after the system properties are set
        registry = Registry.getSystemRegistry();
        return registry;
    }

    static void emptyRegistryFile() throws IOException {
        if (registryFile.exists()) {
            BufferedOutputStream out = new BufferedOutputStream(
                new FileOutputStream(registryFile));
            try {
                RegistrationData data = new RegistrationData();
                data.storeToXML(out);
            } finally {
                out.close();
            }
        }
    }
}
