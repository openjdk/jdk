/*
 * Copyright (c) 2005, 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6260888 8087157
 * @summary check SunPKCS11-Solaris is available on S10+ systems
 * @author Andreas Sterbenz
 * @run main/othervm DefaultPKCS11
 */

import java.util.*;

import java.security.*;

public class DefaultPKCS11 {

    public static void main(String[] args) throws Exception {
        System.setSecurityManager(new SecurityManager());

        String osName = System.getProperty("os.name", "(null)");
        String osVersion = System.getProperty("os.version", "(null)");
        System.out.println("Running on " + osName + " " + osVersion);
        Provider[] ps = Security.getProviders();
        System.out.println("Providers: " + Arrays.asList(ps));
        System.out.println();

        if (osName.equals("SunOS") == false) {
            System.out.println("Test only applies to Solaris, skipping");
            return;
        }
        String[] v = osVersion.split("\\.");
        if (v.length < 2) {
            throw new Exception("Failed to parse Solaris version: " + Arrays.asList(v));
        }
        if (Integer.parseInt(v[0]) != 5) {
            throw new Exception("Unknown Solaris major version: " + v[0]);
        }
        if (Integer.parseInt(v[1]) < 10) {
            System.out.println("Test only applies to Solaris 10 and later, skipping");
            return;
        }
        // SunPKCS11-Solaris provider should be either the first one or
        // the second one
        if (ps[0].getName().equals("SunPKCS11-Solaris") == false &&
            ps[1].getName().equals("SunPKCS11-Solaris") == false) {
            throw new Exception("SunPKCS11-Solaris provider not installed");
        }
        System.out.println("OK");
    }

}
