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
 * @summary Basic Test for deleting a service tag in a product registration
 * @author  Mandy Chung
 *
 * @run build DeleteServiceTag Util
 * @run main DeleteServiceTag
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class DeleteServiceTag {
    private static RegistrationData registration;
    private static File regFile;
    private static Map<String, ServiceTag> stMap =
        new LinkedHashMap<String, ServiceTag>();
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties"
                                    };

    public static void main(String[] argv) throws Exception {
        String registrationDir = System.getProperty("test.classes");
        String servicetagDir = System.getProperty("test.src");

        File original = new File(servicetagDir, "registration.xml");
        regFile = new File(registrationDir, "registration.xml");
        copyRegistrationXML(original, regFile);

        // loads all the service tags
        for (String f : files) {
            File stfile = new File(servicetagDir, f);
            ServiceTag svcTag = Util.newServiceTag(stfile);
            stMap.put(svcTag.getInstanceURN(), svcTag);
        }

        // load the registration data with all service tags
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(regFile));
        registration = RegistrationData.loadFromXML(in);

        if (stMap.size() != files.length) {
            throw new RuntimeException("Invalid service tag count= " +
                stMap.size() + " expected=" + files.length);
        }
        // check the service tags
        Util.checkRegistrationData(regFile.getCanonicalPath(), stMap);

        // delete a service tag
        deleteServiceTag(servicetagDir, files[0]);

        System.out.println("Test passed: service tags deleted.");
    }

    private static void copyRegistrationXML(File from, File to) throws IOException {

        to.delete();
        BufferedReader reader = new BufferedReader(new FileReader(from));
        PrintWriter writer = new PrintWriter(to);
        try {
            String line = null;
            while ((line = reader.readLine()) != null) {
                writer.println(line);
            }
            writer.flush();
        } finally {
            writer.close();
        }
    }

    private static void deleteServiceTag(String parent, String filename) throws Exception {
        File f = new File(parent, filename);
        ServiceTag svcTag = Util.newServiceTag(f);

        ServiceTag st = registration.removeServiceTag(svcTag.getInstanceURN());
        if (st == null) {
            throw new RuntimeException("RegistrationData.remove method" +
                " returns null");
        }
        if (!Util.matches(st, svcTag)) {
            throw new RuntimeException("ServiceTag added in the registration " +
                " doesn't match.");
        }
        // check the service tags before storing the updated data
        Util.checkRegistrationData(regFile.getCanonicalPath(), stMap);

        ServiceTag st1 = registration.getServiceTag(svcTag.getInstanceURN());
        if (st1 != null) {
            throw new RuntimeException("RegistrationData.get method returns " +
                "non-null.");
        }
        // Now remove the service tag from the map and store to the XML file
        stMap.remove(svcTag.getInstanceURN());
        BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(regFile));
        try {
            registration.storeToXML(out);
        } finally {
            out.close();
        }
    }
}
