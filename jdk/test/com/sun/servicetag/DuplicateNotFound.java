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
 * @summary Basic Test for RegistrationData.removeServiceTag and
 *          updateServiceTag.
 * @author  Mandy Chung
 *
 * @run build DuplicateNotFound Util
 * @run main DuplicateNotFound
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

public class DuplicateNotFound {
    private static String servicetagDir = System.getProperty("test.src");
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties"
                                    };

    private static RegistrationData registration = new RegistrationData();

    public static void main(String[] argv) throws Exception {
        ServiceTag svcTag;
        registration.addServiceTag(loadServiceTag(files[0]));
        registration.addServiceTag(loadServiceTag(files[1]));
        testDuplicate(files[0]);
        testDuplicate(files[1]);
        testNotFound(files[2]);
    }

    private static void testDuplicate(String filename) throws Exception {
        boolean dup = false;
        try {
           registration.addServiceTag(loadServiceTag(filename));
        } catch (IllegalArgumentException e) {
           dup = true;
        }
        if (!dup) {
           throw new RuntimeException(filename +
               " added successfully but expected to be a duplicated.");
        }
    }
    private static void testNotFound(String filename) throws Exception {
        ServiceTag st = loadServiceTag(filename);
        ServiceTag svctag = registration.getServiceTag(st.getInstanceURN());
        if (svctag != null) {
           throw new RuntimeException(st.getInstanceURN() +
               " exists but expected not found");
        }

        svctag = registration.removeServiceTag(st.getInstanceURN());
        if (svctag != null) {
           throw new RuntimeException(st.getInstanceURN() +
               " exists but expected not found");
        }

        svctag = registration.updateServiceTag(st.getInstanceURN(), "testing");
        if (svctag != null) {
           throw new RuntimeException(st.getInstanceURN() +
               " updated successfully but expected not found.");
        }
    }

    private static ServiceTag loadServiceTag(String filename) throws Exception {
        File f = new File(servicetagDir, filename);
        return Util.newServiceTag(f);
    }
}
