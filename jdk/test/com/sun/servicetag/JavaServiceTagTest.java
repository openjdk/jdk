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
 * @summary Basic Test for ServiceTag.getJavaServiceTag()
 *          Disable creating the service tag in the system registry.
 *          Verify the existence of registration.xml file and the
 *          content of the service tag.
 * @author  Mandy Chung
 *
 * @run build JavaServiceTagTest
 * @run main JavaServiceTagTest
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class JavaServiceTagTest {
    public static void main(String[] argv) throws Exception {
        String registrationDir = System.getProperty("test.classes");

        // disable calling to stclient
        System.setProperty("servicetag.sthelper.supported", "false");

        if (Registry.isSupported()) {
            throw new RuntimeException("Registry.isSupported() should " +
                "return false");
        }
        // For debugging
        // System.setProperty("servicetag.verbose", "");

        // cleanup the registration.xml and servicetag file in the test directory
        System.setProperty("servicetag.dir.path", registrationDir);
        File regFile = new File(registrationDir, "registration.xml");
        regFile.delete();
        File svcTagFile = new File(registrationDir, "servicetag");
        svcTagFile.delete();

        ServiceTag svctag = ServiceTag.getJavaServiceTag("JavaServiceTagTest");
        checkServiceTag(svctag);

        if (svcTagFile.exists()) {
            throw new RuntimeException(svcTagFile + " should not exist.");
        }

        // registration.xml should be created
        if (!regFile.exists()) {
            throw new RuntimeException(regFile + " not created.");
        }
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(regFile));
        RegistrationData registration = RegistrationData.loadFromXML(in);
        Set<ServiceTag> c = registration.getServiceTags();
        if (c.size() != 1) {
            throw new RuntimeException(regFile + " has " + c.size() +
                " service tags. Expected 1.");
        }
        ServiceTag st = registration.getServiceTag(svctag.getInstanceURN());
        if (!Util.matches(st, svctag)) {
            throw new RuntimeException("ServiceTag " +
                " doesn't match.");
        }
    }

    private static void checkServiceTag(ServiceTag st) throws IOException {
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
                equals("JavaServiceTagTest")) {
            throw new RuntimeException("Unexpected source: " +
                st.getSource());
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
