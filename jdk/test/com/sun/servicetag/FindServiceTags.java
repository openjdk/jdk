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
 * @summary Basic Test for Registry.findServiceTags()
 * @author  Mandy Chung
 *
 * @run build FindServiceTags SvcTagClient Util
 * @run main FindServiceTags
 */

import com.sun.servicetag.*;
import java.io.*;
import java.util.*;

// This test creates a few service tags in the Registry.
// Check if the findServiceTags method returns the expected ones.
public class FindServiceTags {
    private static String registryDir = System.getProperty("test.classes");
    private static String servicetagDir = System.getProperty("test.src");
    private static String[] files = new String[] {
                                        "servicetag1.properties",
                                        "servicetag2.properties",
                                        "servicetag3.properties",
                                        "servicetag4.properties",
                                        "servicetag5.properties"
                                    };

    private static Registry registry;
    private static Set<ServiceTag> set = new HashSet<ServiceTag>();
    private static Set<String> productUrns = new HashSet<String>();
    private static int expectedUrnCount = 3;

    public static void main(String[] argv) throws Exception {
        registry = Util.getSvcTagClientRegistry();

        for (String filename : files) {
            File f = new File(servicetagDir, filename);
            ServiceTag svcTag = Util.newServiceTag(f);
            ServiceTag st = registry.addServiceTag(svcTag);

            set.add(st);
            productUrns.add(st.getProductURN());
        }
        if (productUrns.size() != expectedUrnCount) {
            throw new RuntimeException("Unexpected number of product URNs = " +
                productUrns.size() + " expected " + expectedUrnCount);
        }
        if (set.size() != files.length) {
            throw new RuntimeException("Unexpected number of service tags = " +
                set.size() + " expected " + files.length);
        }
        String purn = null;
        for (String urn : productUrns) {
            if (purn == null) {
                // save the first product_urn for later use
                purn = urn;
            }
            findServiceTags(urn);
        }

        // remove all service tags of purn
        Set<ServiceTag> tags = registry.findServiceTags(purn);
        for (ServiceTag st : tags) {
            System.out.println("Removing service tag " + st.getInstanceURN());
            registry.removeServiceTag(st.getInstanceURN());
        }
        tags = registry.findServiceTags(purn);
        if (tags.size() != 0) {
            throw new RuntimeException("Unexpected service tag count = " +
                tags.size());
        }

        System.out.println("Test passed.");
    }

    private static void findServiceTags(String productUrn) throws Exception {
        Set<ServiceTag> found = registry.findServiceTags(productUrn);
        Set<ServiceTag> matched = new HashSet<ServiceTag>();
        System.out.println("Finding service tags of product_urn=" +
            productUrn);
        for (ServiceTag st : set) {
            if (st.getProductURN().equals(productUrn)) {
                System.out.println(st.getInstanceURN());
                matched.add(st);
            }
        }
        if (found.size() != matched.size()) {
            throw new RuntimeException("Unmatched service tag count = " +
                found.size() + " expected " + matched.size());
        }

        for (ServiceTag st0 : found) {
            ServiceTag st = null;
            for (ServiceTag st1 : matched) {
                if (Util.matches(st0, st1)) {
                    st = st1;
                    break;
                }
            }
            if (st == null) {
                System.out.println("product_urn=" + st0.getProductURN());
                System.out.println("instance_urn=" + st0.getInstanceURN() );
                throw new RuntimeException(st0.getInstanceURN() +
                    " not expected in the returned list");
            }
        }
    }
}
