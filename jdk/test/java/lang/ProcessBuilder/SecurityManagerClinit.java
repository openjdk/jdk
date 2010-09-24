/*
 * Copyright 2010 Google Inc.  All Rights Reserved.
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
 * @bug 6980747
 * @summary Check that Process-related classes have the proper
 *     doPrivileged blocks, and can be initialized with an adversarial
 *     security manager.
 * @run main/othervm SecurityManagerClinit
 * @author Martin Buchholz
 */

import java.io.*;
import java.security.*;

public class SecurityManagerClinit {
    private static class Policy extends java.security.Policy {
        private Permissions perms;

        public Policy(Permission... permissions) {
            perms = new Permissions();
            for (Permission permission : permissions)
                perms.add(permission);
        }

        public boolean implies(ProtectionDomain pd, Permission p) {
            return perms.implies(p);
        }
    }

    public static void main(String[] args) throws Throwable {
        String javaExe =
            System.getProperty("java.home") +
            File.separator + "bin" + File.separator + "java";

        // A funky contrived security setup, just for bug repro purposes.
        java.security.Security.setProperty("package.access", "java.util");

        final Policy policy =
            new Policy
            (new FilePermission("<<ALL FILES>>", "execute"),
             new RuntimePermission("setSecurityManager"));
        Policy.setPolicy(policy);

        System.setSecurityManager(new SecurityManager());

        try {
            String[] cmd = { javaExe, "-version" };
            Process p = Runtime.getRuntime().exec(cmd);
            p.getOutputStream().close();
            p.getInputStream().close();
            p.getErrorStream().close();
            p.waitFor();
        } finally {
            System.setSecurityManager(null);
        }
    }
}
