/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @run main/othervm/policy=flag.policy
  *     -Djdk.io.permissionsUseCanonicalPath=true Flag true true
 * @run main/othervm/policy=flag.policy
  *     -Djdk.io.permissionsUseCanonicalPath=false Flag false true
 * @run main/othervm/policy=flag.policy Flag false true
 */

import java.io.File;
import java.io.FilePermission;
import java.lang.*;

public class Flag {
    public static void main(String[] args) throws Exception {

        boolean test1;
        boolean test2;

        String here = System.getProperty("user.dir");
        File abs = new File(here, "x");
        FilePermission fp1 = new FilePermission("x", "read");
        FilePermission fp2 = new FilePermission(abs.toString(), "read");
        test1 = fp1.equals(fp2);

        try {
            System.getSecurityManager().checkPermission(fp2);
            test2 = true;
        } catch (SecurityException se) {
            test2 = false;
        }

        if (test1 != Boolean.parseBoolean(args[0]) ||
                test2 != Boolean.parseBoolean(args[1])) {
            throw new Exception("Test failed: " + test1 + " " + test2);
        }
    }
}
