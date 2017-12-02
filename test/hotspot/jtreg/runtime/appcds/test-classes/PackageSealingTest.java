/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
 *
 */

import java.lang.Package;

public class PackageSealingTest {
    public static void main(String args[]) {
        try {
            Class c1 = PackageSealingTest.class.forName("sealed.pkg.C1");
            Class c2 = PackageSealingTest.class.forName("pkg.C2");
            Package p1 = c1.getPackage();
            System.out.println("Package 1: " + p1.toString());
            Package p2 = c2.getPackage();
            System.out.println("Package 2: " + p2.toString());

            if (!p1.isSealed()) {
                System.out.println("Failed: sealed.pkg is not sealed.");
                System.exit(0);
            }

            if (p2.isSealed()) {
                System.out.println("Failed: pkg is sealed.");
                System.exit(0);
            }

            System.out.println("OK");
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
