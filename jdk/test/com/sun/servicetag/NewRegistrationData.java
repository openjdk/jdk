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
 * @summary Basic Test for Registration Data
 * @author  Mandy Chung
 *
 * @run build NewRegistrationData Util
 * @run main NewRegistrationData
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class NewRegistrationData {
    private static RegistrationData regData;
    private static Map<String, ServiceTag> stMap = new LinkedHashMap<String, ServiceTag>();
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties"
                                    };

    public static void main(String[] argv) throws Exception {
        String regDataDir = System.getProperty("test.classes");
        String servicetagDir = System.getProperty("test.src");

        File reg = new File(regDataDir, "registration.xml");
        // Make sure a brand new file is created
        reg.delete();

        regData = new RegistrationData();
        if (regData.getRegistrationURN().isEmpty()) {
            throw new RuntimeException("Empty registration urn");
        }

        int count = 0;
        for (String f : files) {
            addServiceTag(servicetagDir, f, ++count);
        }

        // check if the registration data contains all service tags
        Set<ServiceTag> c = regData.getServiceTags();
        for (ServiceTag st : c) {
            if (!Util.matches(st, regData.getServiceTag(st.getInstanceURN()))) {
                throw new RuntimeException("ServiceTag added in the regData " +
                    " doesn't match.");
            }
        }

        // store the service tag to a file
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(reg));
        try {
            regData.storeToXML(out);
        } finally {
            out.close();
        }

        Util.checkRegistrationData(reg.getCanonicalPath(), stMap);
        System.out.println("Test passed: " + count + " service tags added");
    }

    private static void addServiceTag(String parent, String filename, int count) throws Exception {
        File f = new File(parent, filename);
        ServiceTag svcTag = Util.newServiceTag(f);
        regData.addServiceTag(svcTag);
        stMap.put(svcTag.getInstanceURN(), svcTag);

        Set<ServiceTag> c = regData.getServiceTags();
        if (c.size() != count) {
            throw new RuntimeException("Invalid service tag count= " +
                c.size() + " expected=" + count);
        }
        ServiceTag st = regData.getServiceTag(svcTag.getInstanceURN());
        if (!Util.matches(st, svcTag)) {
            throw new RuntimeException("ServiceTag added in the regData " +
                " doesn't match.");
        }
    }
}
