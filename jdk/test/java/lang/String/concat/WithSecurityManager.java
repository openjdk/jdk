/*
 * Copyright (c) 2016, 2016, Oracle and/or its affiliates. All rights reserved.
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

import java.security.Permission;

/**
 * @test
 * @summary String concatenation fails with a custom SecurityManager that uses concatenation
 * @bug 8155090
 *
 * @compile WithSecurityManager.java
 *
 * @run main/othervm -Xverify:all WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB                  WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED            WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED            WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=BC_SB_SIZED_EXACT      WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_SB_SIZED_EXACT      WithSecurityManager
 * @run main/othervm -Xverify:all -Djava.lang.invoke.stringConcat=MH_INLINE_SIZED_EXACT  WithSecurityManager
*/
public class WithSecurityManager {
    public static void main(String[] args) throws Throwable {
        SecurityManager sm = new SecurityManager() {
            @Override
            public void checkPermission(Permission perm) {
                String abc = "abc";
                String full = abc + "def";
            }
        };
        System.setSecurityManager(sm);
        ClassLoader cl = new ClassLoader() {};
    }
}
