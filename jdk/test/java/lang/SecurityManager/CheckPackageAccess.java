/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
 *  @test
 *  @bug 6741606 7146431 8000450 8019830 8022945 8027144 8041633
 *  @summary Make sure all restricted packages listed in the package.access
 *           property in the java.security file are blocked
 *  @run main/othervm CheckPackageAccess
 */

import java.util.Collections;
import java.util.ArrayList;
import java.util.List;

/*
 * The main benefit of this test is to catch merge errors or other types
 * of issues where one or more of the packages are accidentally
 * removed. This is why the packages that are known to be restricted have to
 * be explicitly listed below.
 */
public class CheckPackageAccess {

    public static void main(String[] args) throws Exception {
        // get expected list of restricted packages
        List<String> pkgs = RestrictedPackages.expected();

        // get actual list of restricted packages
        List<String> jspkgs = RestrictedPackages.actual();

        if (!isOpenJDKOnly()) {
            String lastPkg = pkgs.get(pkgs.size() - 1);

            // Remove any closed packages from list before comparing
            int index = jspkgs.indexOf(lastPkg);
            if (index != -1 && index != jspkgs.size() - 1) {
                jspkgs.subList(index + 1, jspkgs.size()).clear();
            }
        }

        // Sort to ensure lists are comparable
        Collections.sort(pkgs);
        Collections.sort(jspkgs);

        if (!pkgs.equals(jspkgs)) {
            for (String p : pkgs)
                if (!jspkgs.contains(p))
                    System.out.println("In golden set, but not in j.s file: " + p);
            for (String p : jspkgs)
                if (!pkgs.contains(p))
                    System.out.println("In j.s file, but not in golden set: " + p);


            throw new RuntimeException("restricted packages are not " +
                                       "consistent with java.security file");
        }
        System.setSecurityManager(new SecurityManager());
        SecurityManager sm = System.getSecurityManager();
        for (String pkg : pkgs) {
            String subpkg = pkg + "foo";
            try {
                sm.checkPackageAccess(pkg);
                throw new RuntimeException("Able to access " + pkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageAccess(subpkg);
                throw new RuntimeException("Able to access " + subpkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageDefinition(pkg);
                throw new RuntimeException("Able to define class in " + pkg +
                                           " package");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageDefinition(subpkg);
                throw new RuntimeException("Able to define class in " + subpkg +
                                           " package");
            } catch (SecurityException se) { }
        }
        System.out.println("Test passed");
    }

    private static boolean isOpenJDKOnly() {
        String prop = System.getProperty("java.runtime.name");
        return prop != null && prop.startsWith("OpenJDK");
    }
}
