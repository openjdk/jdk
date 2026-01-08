/*
 * Copyright (c) 2014, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8039853
 * @summary Provider.Service.newInstance() does not work with current
            JDK JGSS Mechanisms
 * @library /test/lib
 */


import jtreg.SkippedException;

import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NewInstance {

    public static void main(String[] args) throws Exception {

        System.out.println("Removing SunPCSC provider from the list (A smartcard might not be installed).");
        final List<Provider> providers = Arrays.stream(Security.getProviders())
                .filter(provider -> !provider.getName().equals("SunPCSC"))
                .collect(Collectors.toList());

        for (Provider p : providers) {
            System.out.println("---------");
            System.out.println(p.getName() + ":" + p.getInfo());
            Set<Provider.Service> set = p.getServices();
            Iterator<Provider.Service> i = set.iterator();

            while (i.hasNext()) {
                Provider.Service s = i.next();
                System.out.println(s.getType() + "." + s.getAlgorithm());
                try {
                    s.newInstance(null);
                } catch (NoSuchAlgorithmException e) {
                    System.out.println("  check");
                    Throwable t = e.getCause();
                    if (!(t instanceof InvalidAlgorithmParameterException)) {
                        // Some engines require certain parameters to be
                        // present on creation. Calling newInstance(null) will
                        // trigger this exception and it's OK.
                        throw e;
                    }
                }
            }
        }
    }
}
