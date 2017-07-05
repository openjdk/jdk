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
 * @test
 * @bug     6622366
 * @summary Basic Test for ServiceTag.getJavaServiceTag(String)
 *          to verify that the registration.xml and servicetag files
 *          are both created correctly.
 * @author  Mandy Chung
 *
 * @run build JavaServiceTagTest1 SvcTagClient Util
 * @run main JavaServiceTagTest1
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class JavaServiceTagTest1 {
    private static String registrationDir = System.getProperty("test.classes");
    private static String servicetagDir = System.getProperty("test.src");
    private static File regFile;
    private static File svcTagFile;
    private static Registry registry;
    public static void main(String[] argv) throws Exception {
        try {
            registry = Util.getSvcTagClientRegistry();
            runTest();
        } finally {
            // restore empty registry file
            Util.emptyRegistryFile();
        }
    }

    private static void runTest() throws Exception {
        // cleanup the registration.xml and servicetag file in the test directory
        System.setProperty("servicetag.dir.path", registrationDir);
        regFile = new File(registrationDir, "registration.xml");
        regFile.delete();

        svcTagFile = new File(registrationDir, "servicetag");
        svcTagFile.delete();

        // verify that only one service tag is created
        ServiceTag st1 = testJavaServiceTag("Test1");

        // getJavaServiceTag method should create a new service tag
        // and delete the old one
        ServiceTag st2 = testJavaServiceTag("Test2");
        if (registry.getServiceTag(st1.getInstanceURN()) != null) {
            throw new RuntimeException("instance_urn: " + st1.getInstanceURN() +
                " exists but expected to be removed");
        }

        // expected to have different instance_urn
        if (st1.getInstanceURN().equals(st2.getInstanceURN())) {
            throw new RuntimeException("instance_urn: " + st1.getInstanceURN() +
                " == " + st2.getInstanceURN());
        }

        // Delete the service tag from the Registry and the servicetag file
        if (registry.removeServiceTag(st2.getInstanceURN()) == null) {
            throw new RuntimeException("Failed to remove " +
                st1.getInstanceURN() + " from the registry");
        }
        svcTagFile.delete();

        // call the getJavaServiceTag(String) method again
        // should create the servicetag file.
        ServiceTag st3 = testJavaServiceTag("Test2");
        if (!Util.matches(st2, st3)) {
            System.out.println(st2);
            System.out.println(st3);
            throw new RuntimeException("Test Failed: Expected to be the same");
        }

    }

    private static ServiceTag testJavaServiceTag(String source) throws Exception {
        ServiceTag svctag = ServiceTag.getJavaServiceTag(source);
        checkServiceTag(svctag, source);

        // verify if registration.xml is created
        if (!regFile.exists()) {
            throw new RuntimeException(regFile + " not created.");
        }

        // verify the registration.xml content is the expected service tag
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(regFile));
        RegistrationData registration = RegistrationData.loadFromXML(in);
        Set<ServiceTag> c = registration.getServiceTags();
        if (c.size() != 1) {
            throw new RuntimeException(regFile + " has " + c.size() +
                " service tags. Expected 1.");
        }
        ServiceTag st = registration.getServiceTag(svctag.getInstanceURN());
        if (!Util.matches(st, svctag)) {
            throw new RuntimeException("RegistrationData ServiceTag " +
                " doesn't match.");
        }

        // verify the service tag added in the registry
        st = registry.getServiceTag(svctag.getInstanceURN());
        if (!Util.matches(st, svctag)) {
            throw new RuntimeException("Registry ServiceTag " +
                " doesn't match.");
        }

        // verify if servicetag file is created
        if (!svcTagFile.exists()) {
            throw new RuntimeException(svcTagFile + " not created.");
        }

        // verify that the servicetag file only contains one instance_urn
        BufferedReader reader = new BufferedReader(new FileReader(svcTagFile));
        int count = 0;
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.equals(svctag.getInstanceURN())) {
                    count++;
                } else {
                    throw new RuntimeException("servicetag contains " +
                        " unexpected instance_urn " + line);
                }
            }
        } finally {
            reader.close();
        }
        if (count != 1) {
            throw new RuntimeException("servicetag contains unexpected " +
                "number of instance_urn = " + count);
        }
        return svctag;
    }

    private static void checkServiceTag(ServiceTag st, String source)
            throws IOException {
        Properties props = loadSwordfishEntries();
        if (st.getProductURN().
                equals(props.getProperty("servicetag.jdk.urn"))) {
            if (!st.getProductName().
                    equals(props.getProperty("servicetag.jdk.name"))) {
                throw new RuntimeException("Product URN and name don't match.");
            }
        } else if (st.getProductURN().
                equals(props.getProperty("servicetag.jre.urn"))) {
            if (!st.getProductName().
                    equals(props.getProperty("servicetag.jre.name"))) {
                throw new RuntimeException("Product URN and name don't match.");
            }
        } else {
            throw new RuntimeException("Unexpected product_urn: " +
                st.getProductURN());
        }
        if (!st.getProductVersion().
                equals(System.getProperty("java.version"))) {
            throw new RuntimeException("Unexpected product_version: " +
                st.getProductVersion());
        }
        if (!st.getProductParent().
                equals(props.getProperty("servicetag.parent.name"))) {
            throw new RuntimeException("Unexpected product_parent: " +
                st.getProductParent());
        }
        if (!st.getProductParentURN().
                equals(props.getProperty("servicetag.parent.urn"))) {
            throw new RuntimeException("Unexpected product_parent_urn: " +
                st.getProductParentURN());
        }
        if (!st.getPlatformArch().
                equals(System.getProperty("os.arch"))) {
            throw new RuntimeException("Unexpected platform_arch: " +
                st.getPlatformArch());
        }

        String vendor = System.getProperty("java.vendor");
        if (!st.getProductVendor().
                equals(vendor)) {
            throw new RuntimeException("Unexpected product_vendor: " +
                st.getProductVendor());
        }
        if (!st.getSource().
                equals(source)) {
            throw new RuntimeException("Unexpected source: " +
                st.getSource() + " expected: " + source);
        }
        String[] ss = st.getProductDefinedInstanceID().split(",");
        boolean id = false;
        boolean dir = false;
        for (String s : ss) {
            String[] values = s.split("=");
            if (values[0].equals("id")) {
                id = true;
                String[] sss = values[1].split(" ");
                if (!sss[0].equals(System.getProperty("java.runtime.version"))) {
                    throw new RuntimeException("Unexpected version in id: " +
                        sss[0]);
                }
                if (sss.length < 2) {
                    throw new RuntimeException("Unexpected id=" + values[1]);
                }
            } else if (values[0].equals("dir")) {
                dir = true;
            }
        }
        if (!id || !dir) {
            throw new RuntimeException("Unexpected product_defined_instance_id: " +
                st.getProductDefinedInstanceID());
        }
    }

    private static Properties loadSwordfishEntries()
           throws IOException {
        int version = sun.misc.Version.jdkMinorVersion();
        String filename = "/com/sun/servicetag/resources/javase_" +
                version + "_swordfish.properties";
        InputStream in = Installer.class.getClass().getResourceAsStream(filename);
        Properties props = new Properties();
        try {
            props.load(in);
        } finally {
            in.close();
        }
        return props;
    }
}
