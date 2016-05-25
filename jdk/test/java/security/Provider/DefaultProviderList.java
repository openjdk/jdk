/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 7191662 8157469
 * @summary Ensure non-java.base providers can be found by ServiceLoader
 * @author Valerie Peng
 */

import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ServiceLoader;

public class DefaultProviderList {

    public static void main(String[] args) throws Exception {
        Provider[] defaultProvs = Security.getProviders();
        System.out.println("Providers: " + Arrays.asList(defaultProvs));
        System.out.println();

        ServiceLoader<Provider> sl = ServiceLoader.load(Provider.class);
        boolean failed = false;
        for (Provider p : defaultProvs) {
            String pName = p.getName();
            // only providers outside java.base are loaded by ServiceLoader
            if (pName.equals("SUN") || pName.equals("SunRsaSign") ||
                pName.equals("SunJCE") || pName.equals("SunJSSE") ||
                pName.equals("Apple")) {
                System.out.println("Skip test for provider " + pName);
                continue;
            }
            String pClassName = p.getClass().getName();
            // Should be able to find each one through ServiceLoader
            Iterator<Provider> provIter = sl.iterator();
            boolean found = false;
            while (provIter.hasNext()) {
                Provider pFromSL = provIter.next();
                if (pFromSL.getClass().getName().equals(pClassName)) {
                    found = true;
                    break;
                }
            }
            System.out.println("Found " + p.getName() + " = " + found);
            if (!found) {
                failed = true;
                System.out.println("Error: no provider class " + pClassName +
                    " found");
            }
        }
        if (!failed) {
            System.out.println("Test Passed");
        } else {
            throw new Exception("One or more provider not loaded by SL");
        }
    }
}
