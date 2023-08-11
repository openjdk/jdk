/*
 * Copyright (c) 2004, 2022, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 4993819
 * @summary standard extensions path is hard-coded in default
 *      system policy file
 * @run main/othervm/policy=ExtDirsChange.policy ExtDirsChange
 */

import java.security.*;

public class ExtDirsChange {
    public static void main(String args[]) throws Exception {
        System.out.println("java.policy.dirs: " +
                System.getProperty("java.policy.dirs"));

        // Uses default security policy and java.policy.dirs
        try {
            ExtDirsA a = new ExtDirsA();
            a.go();
            throw new Exception("Test Failed (Setup problem)");
        } catch (SecurityException se) {
            System.out.println("Setup OK");
        }

        // Change java.policy.dirs and refresh policy
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                // Change java.policy.dirs
                System.setProperty("java.policy.dirs",
                        System.getProperty("test.classes"));
                System.out.println("java.policy.dirs: " +
                        System.getProperty("java.policy.dirs"));
                return null;
            }
        });

        // Continue to use default security policy
        try {
            ExtDirsA a = new ExtDirsA();
            a.go();
            throw new Exception("Test Failed (Setup before refresh problem)");
        } catch (SecurityException se) {
            System.out.println("Setup before refresh OK");
        }

        // Refresh policy using updated java.policy.dirs
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                Policy.getPolicy().refresh();
                return null;
            }
        });

        // Test should now succeed
        try {
            ExtDirsA a = new ExtDirsA();
            a.go();
            System.out.println("Test Succeeded");
        } catch (SecurityException se) {
            se.printStackTrace();
            System.out.println("Test Failed");
            throw se;
        }

        // Test with blank java.ext.dir
        // Change java.policy.dirs and refresh policy
        AccessController.doPrivileged(new PrivilegedAction() {
            public Object run() {
                // Change java.policy.dirs
                System.setProperty("java.policy.dirs", " ");
                System.out.println("java.policy.dirs: " +
                        System.getProperty("java.policy.dirs"));
                Policy.getPolicy().refresh();
                return null;
            }
        });

        // Test with blank java.ext.dir
        try {
            ExtDirsA a = new ExtDirsA();
            a.go();
            throw new Exception("Blank Test Failed");
        } catch (SecurityException se) {
            System.out.println("Blank Test OK");
        }
    }
}
