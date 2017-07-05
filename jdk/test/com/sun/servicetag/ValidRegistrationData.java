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
 * @summary Basic Test for reading a valid registration
 * @author  Mandy Chung
 *
 * @run build ValidRegistrationData
 * @run main ValidRegistrationData
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class ValidRegistrationData {
    private static String registrationDir = System.getProperty("test.classes");
    private static String servicetagDir = System.getProperty("test.src");
    private static RegistrationData registration;
    private static Map<String, ServiceTag> stMap =
        new LinkedHashMap<String, ServiceTag>();
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties"
                                    };
    private static String URN = "urn:st:9543ffaa-a4f1-4f77-b2d1-f561922d4e4a";

    public static void main(String[] argv) throws Exception {
        File f = new File(servicetagDir, "registration.xml");

        // load the registration data with all service tags
        BufferedInputStream in = new BufferedInputStream(new FileInputStream(f));
        registration = RegistrationData.loadFromXML(in);
        if (!registration.getRegistrationURN().equals(URN)){
            throw new RuntimeException("Invalid URN=" +
                registration.getRegistrationURN());
        }
        Map<String,String> environMap = registration.getEnvironmentMap();
        checkEnvironmentMap(environMap);

        // set environment
        setInvalidEnvironment("hostname", "");
        setInvalidEnvironment("osName", "");
        setInvalidEnvironment("invalid", "");
    }

    private static void checkEnvironmentMap(Map<String,String> envMap)
            throws Exception {
        Properties props = new Properties();
        File f = new File(servicetagDir, "environ.properties");
        FileReader reader = new FileReader(f);
        try {
            props.load(reader);
        } finally {
            reader.close();
        }
        for (Map.Entry<String,String> entry : envMap.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            String expected = props.getProperty(name);
            if (expected == null || !value.equals(expected)) {
                throw new RuntimeException("Invalid environment " +
                    name + "=" + value);
            }
            props.remove(name);
        }
        if (!props.isEmpty()) {
            System.out.println("Environment missing: ");
            for (String s : props.stringPropertyNames()) {
                System.out.println("   " + s + "=" + props.getProperty(s));
            }
            throw new RuntimeException("Invalid environment read");
        }
    }
    private static void setInvalidEnvironment(String name, String value) {
        boolean invalid = false;
        try {
            registration.setEnvironment(name, value);
        } catch (IllegalArgumentException e) {
            invalid = true;
        }
        if (!invalid) {
           throw new RuntimeException(name + "=" + value +
               " set but expected to fail.");
        }
    }
}
