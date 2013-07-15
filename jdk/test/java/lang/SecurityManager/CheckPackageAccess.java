/*
 * Copyright (c) 2012, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7146431 8000450
 * @summary Test that internal packages cannot be accessed
 */

public class CheckPackageAccess {

    public static void main(String[] args) throws Exception {

        String[] pkgs = new String[] {
            "com.sun.corba.se.impl.",
            "com.sun.org.apache.xerces.internal.utils.",
            "com.sun.org.apache.xalan.internal.utils." };
        SecurityManager sm = new SecurityManager();
        System.setSecurityManager(sm);
        for (String pkg : pkgs) {
            System.out.println("Checking package access for " + pkg);
            try {
                sm.checkPackageAccess(pkg);
                throw new Exception("Expected PackageAccess SecurityException not thrown");
            } catch (SecurityException se) { }
            try {
                sm.checkPackageDefinition(pkg);
                throw new Exception("Expected PackageDefinition SecurityException not thrown");
            } catch (SecurityException se) { }
        }
    }
}
