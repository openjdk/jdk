/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8164705
 * @summary check jdk.filepermission.canonicalize
 * @library /java/security/testlibrary/
 * @modules java.base/jdk.internal.misc
 * @run main/othervm -Djdk.io.permissionsUseCanonicalPath=true Flag truetrue
 * @run main/othervm -Djdk.io.permissionsUseCanonicalPath=false Flag falsetrue
 * @run main/othervm Flag falsetrue
 */

import java.io.File;
import java.io.FilePermission;
import java.lang.*;
import java.security.Permission;
import java.security.Policy;
import java.security.ProtectionDomain;

public class Flag {
    public static void main(String[] args) throws Exception {

        boolean test1;
        boolean test2;

        String here = System.getProperty("user.dir");
        File abs = new File(here, "x");
        FilePermission fp1 = new FilePermission("x", "read");
        FilePermission fp2 = new FilePermission(abs.toString(), "read");
        test1 = fp1.equals(fp2);

        Policy pol = new Policy() {
            @java.lang.Override
            public boolean implies(ProtectionDomain domain, Permission permission) {
                return fp1.implies(permission);
            }
        };

        Policy.setPolicy(pol);
        System.setSecurityManager(new SecurityManager());
        try {
            System.getSecurityManager().checkPermission(fp2);
            test2 = true;
        } catch (SecurityException se) {
            test2 = false;
        }

        if (!args[0].equals(test1 + "" + test2)) {
            throw new Exception("Test failed: " + test1 + test2);
        }
    }
}
