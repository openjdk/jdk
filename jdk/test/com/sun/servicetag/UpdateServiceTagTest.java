/*
 * Copyright 2008 Sun Microsystems, Inc.  All Rights Reserved.
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

/*
 * @test
 * @bug     6622366
 * @summary Basic Test for RegistrationData.updateServiceTag
 * @author  Mandy Chung
 *
 * @run build UpdateServiceTagTest Util
 * @run main UpdateServiceTagTest
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class UpdateServiceTagTest {
    private static String servicetagDir = System.getProperty("test.src");
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties"
                                    };
    private static RegistrationData registration = new RegistrationData();
    private static Set<ServiceTag> set = new HashSet<ServiceTag>();

    public static void main(String[] argv) throws Exception {
        for (String f : files) {
            ServiceTag st = addServiceTag(f);
            set.add(st);
        }
        Thread.sleep(1000);
        for (ServiceTag st : set) {
            updateServiceTag(st);
        }
    }

    private static ServiceTag addServiceTag(String filename) throws Exception {
        File f = new File(servicetagDir, filename);
        ServiceTag svcTag = Util.newServiceTag(f, true /* no instance_urn */);
        ServiceTag st = registration.addServiceTag(svcTag);
        if (!Util.matchesNoInstanceUrn(svcTag, st)) {
            throw new RuntimeException("ServiceTag " +
                " doesn't match.");
        }
        String urn = st.getInstanceURN();
        if (!urn.startsWith("urn:st:")) {
            throw new RuntimeException("Invalid generated instance_urn " +
                urn);
        }
        if (st.getInstallerUID() != -1) {
            throw new RuntimeException("Invalid installer_uid " +
                st.getInstallerUID());
        }
        if (st.getTimestamp() == null) {
            throw new RuntimeException("null timestamp ");
        }
        return st;
    }

    private static String newID = "New product defined instance ID";
    private static void updateServiceTag(ServiceTag svcTag) throws Exception {
        // update the service tag
        String urn = svcTag.getInstanceURN();
        registration.updateServiceTag(urn, newID);

        // get the updated service tag
        ServiceTag st = registration.getServiceTag(urn);
        if (Util.matches(svcTag, st)) {
            throw new RuntimeException("ServiceTag " +
                " should not match.");
        }
        if (!st.getProductDefinedInstanceID().equals(newID)) {
            throw new RuntimeException("Invalid product_defined_instance_id " +
                st.getProductDefinedInstanceID());
        }
        if (st.getInstallerUID() != -1) {
            throw new RuntimeException("Invalid installer_uid " +
                st.getInstallerUID());
        }
        if (st.getTimestamp().equals(svcTag.getTimestamp())) {
            throw new RuntimeException("Timestamp " +
                st.getTimestamp() + " == " + svcTag.getTimestamp());
        }
    }
}
