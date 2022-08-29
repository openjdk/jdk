/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @bug 6447816
 * @summary Check that provider service matching/filtering is done correctly.
 * @run main/othervm ProviderFiltering
 */

import java.util.*;
import java.security.*;

public class ProviderFiltering {

    private static void testIPE(String s) {
        // check against invalid filter for InvalidParameterException
        try {
            Security.getProviders(s);
            throw new RuntimeException("Expected IPE not thrown: " + s);
        } catch (InvalidParameterException ipe) {
            System.out.println("Expected IPE thrown for " + s);
        }
    }

    private static void doit(Object filter, String... expectedPNs) {
        System.out.println("Filter: " + filter);
        System.out.println("Expected Provider(s): " +
                (expectedPNs.length > 0 ? Arrays.toString(expectedPNs) :
                "<NONE>"));
        Provider ps[];
        if (filter instanceof String filterStr) {
            ps = Security.getProviders(filterStr);
        } else if (filter instanceof Map filterMap) {
            ps = Security.getProviders(filterMap);
        } else {
            throw new RuntimeException("Error: unknown input type: " + filter);
        }

        if (ps == null) {
            if (expectedPNs.length != 0) {
                throw new RuntimeException("Fail: expected provider(s) " +
                        "not found");
            }
        } else {
            if (ps.length == expectedPNs.length) {
                // check the provider names
                for (int i = 0; i < ps.length; i++) {
                    if (!ps[i].getName().equals(expectedPNs[i])) {
                        throw new RuntimeException("Fail: provider name " +
                                "mismatch at index " + i + ", got " +
                                ps[i].getName());
                    }
                }
            } else {
                throw new RuntimeException("Fail: # of providers mismatch");
            }
        }
        System.out.println("=> Passed");
    }


    public static void main(String[] args)
                throws NoSuchAlgorithmException {
        testIPE("");
        testIPE("Cipher.");
        testIPE(".RC2 ");
        testIPE("Cipher.RC2 :");
        testIPE("Cipher.RC2 a: ");
        testIPE("Cipher.RC2 :b");
        testIPE("Cipher.RC2 SupportedKeyClasses:a|b");

        String p = "SUN";

        // test alias
        doit("Signature.NONEwithDSA", p);

        String sigService = "Signature.SHA256withDSA";
        // javadoc allows extra spaces in between
        String key = sigService + "   SupportedKeyClasses";
        String valComp1 = "java.security.interfaces.DSAPublicKey";
        String valComp2 = "java.security.interfaces.DSAPrivateKey";
        String valComp2CN = valComp2.substring(valComp2.lastIndexOf('.') + 1);

        // test using String filter
        doit(key + ":" + valComp1, p);
        doit(key + ":" + valComp2, p);
        doit(key + ":" + valComp2CN);

        // repeat above tests using filter Map
        Map<String,String> filters = new HashMap<>();
        filters.put(key, valComp1);
        doit(filters, p);
        filters.put(key, valComp2);
        doit(filters, p);
        filters.put(key, valComp2CN);
        doit(filters);

        // try non-attribute filters
        filters.clear();
        filters.put(sigService, "");
        doit(filters, p);
        filters.put("Cipher.RC2", "");
        doit(filters);

        // test against a custom provider and attribute
        filters.clear();
        String customKey = "customAttr";
        String customValue = "customValue";
        String pName = "TestProv";
        Provider testProv = new TestProvider(pName, sigService, customKey,
                customValue);
        Security.insertProviderAt(testProv, 1);
        // should find both TestProv and SUN and in this order
        doit(sigService, pName, "SUN");
        filters.put(sigService, "");
        doit(filters, pName, "SUN");

        String specAttr = sigService + "  " + customKey + ":" + customValue;
        // should find only TestProv
        doit(specAttr, pName);
        filters.put(sigService + "  " + customKey, customValue);
        doit(filters, pName);

        // should find no proviser now that TestProv is removed
        Security.removeProvider(pName);
        doit(specAttr);
    }

    private static class TestProvider extends Provider {
        TestProvider(String name, String service, String attrKey,
                String attrValue) {
            super(name, "0.0", "Not for use in production systems!");
            put(service, "a.b.c");
            put(service + " " + attrKey, attrValue);
        }
    }
}
