/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8246778
 * @summary Test that security checks occur for getPermittedSubclasses
 *
 * @library /test/lib
 * @compile --enable-preview -source ${jdk.version} TestSecurityManagerChecks.java testPkg/TestSealing.java
 * @run driver ClassFileInstaller testPkg.TestSealing testPkg.TestSealing$Sealed testPkg.TestSealing$Impl
 * @run main/othervm --enable-preview -Xbootclasspath/a:.  TestSecurityManagerChecks
 */

// ClassFileInstaller copies the testPkg files into the "current" directory
// so we can add it to the bootclasspath. Then when we run the test the
// loader for the testPkg files is the bootloader but the loader for the
// test class is the system loader, hence a package access check will fail
// because the system loader is not the same as, nor a parent of, the bootloader.
import java.security.Security;
import java.util.Arrays;

public class TestSecurityManagerChecks {

    public static void main(String[] args) throws Throwable {

        // First get hold of the target classes before we enable security
        Class<?> sealed = testPkg.TestSealing.Sealed.class;

        //try without a SM:
        Class<?>[] subclasses = sealed.getPermittedSubclasses();

        if (subclasses.length != 1) {
            throw new AssertionError("Incorrect permitted subclasses: " + Arrays.asList(subclasses));
        }

        System.out.println("OK - getPermittedSubclasses for " + sealed.getName() +
                           " got result: " + Arrays.asList(subclasses));

        // Next add testPkg to the set of packages for which package-access
        // permission is required
        Security.setProperty("package.access",
                             Security.getProperty("package.access") + ",testPkg.");

        // Finally install a default security manager
        SecurityManager sm = new SecurityManager();
        System.setSecurityManager(sm);

        try {
            sealed.getPermittedSubclasses();
            throw new Error("getPermittedSubclasses succeeded for " + sealed.getName());
        } catch (SecurityException e) {
            System.out.println("OK - getPermittedSubclasses for " + sealed.getName() +
                               " got expected exception: " + e);
        }
    }

}
