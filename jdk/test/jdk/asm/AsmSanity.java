/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 7197401
 * @summary Add a subset of the org.objectweb.asm packages to jdk8
 * This test doesn't test asm functionality, it just tests the presence of
 * asm in the jdk.
 * These compile/run commands do the following:
 * - Verify that asm is not in ct.sym so user code that refs it won't compile.
 * - Verify that asm really is in rt.jar and can be accessed when ct.sym is not used.
 * - Verify that if user code is compiled without ct.sym, it can't access asm classes
 *   at runtime when a security manager is in use.
 *
 * @modules java.base/jdk.internal.org.objectweb.asm
 *
 * @compile -XDignore.symbol.file=true AsmSanity.java
 * @run main/othervm AsmSanity
 *
 * @run main/othervm/fail AsmSanity secure
 *
 */


// Verify that the expected asm pkgs are present
import jdk.internal.org.objectweb.asm.*;

// Verify that we can actually run some of the asm code.
public class AsmSanity {

    static public void main(String[] args) {
        if (args.length == 0) {
            System.out.println("-- Running without SecurityManager");
            new Label();
            System.out.println("-- Passed");
            return;
        }

        if (args[0].equals("secure")) {
            System.out.println("-- Running with SecurityManager");
            java.lang.SecurityManager sm = new SecurityManager();
            System.setSecurityManager(sm);
            // This should cause an accessClassInPackage exception
            new Label();
            return;
        }
        throw new Error("-- Failed:  Unknown argument to main: " + args[0]);
    }
}
