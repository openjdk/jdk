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
 * @modules java.base/jdk.internal.misc
 * @library /test/lib ..
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JVMAddModulePackage
 */

import static jdk.test.lib.Asserts.*;
import java.sql.Time;

public class JVMAddModulePackage {

    public static void main(String args[]) throws Throwable {
        MyClassLoader cl1 = new MyClassLoader();
        MyClassLoader cl3 = new MyClassLoader();
        Object module_one, module_two, module_three;
        boolean result;

        module_one = ModuleHelper.ModuleObject("module_one", cl1, new String[] { "mypackage" });
        assertNotNull(module_one, "Module should not be null");
        ModuleHelper.DefineModule(module_one, "9.0", "module_one/here", new String[] { "mypackage" });
        module_two = ModuleHelper.ModuleObject("module_two", cl1, new String[] { "yourpackage" });
        assertNotNull(module_two, "Module should not be null");
        ModuleHelper.DefineModule(module_two, "9.0", "module_two/here", new String[] { "yourpackage" });
        module_three = ModuleHelper.ModuleObject("module_three", cl3, new String[] { "package/num3" });
        assertNotNull(module_three, "Module should not be null");
        ModuleHelper.DefineModule(module_three, "9.0", "module_three/here", new String[] { "package/num3" });

        // Simple call
        ModuleHelper.AddModulePackage(module_one, "new_package");

        // Add a package and export it
        ModuleHelper.AddModulePackage(module_one, "package/num3");
        ModuleHelper.AddModuleExportsToAll(module_one, "package/num3");

        // Null module argument, expect an NPE
        try {
            ModuleHelper.AddModulePackage(null, "new_package");
            throw new RuntimeException("Failed to get the expected NPE");
        } catch(NullPointerException e) {
            // Expected
        }

        // Bad module argument, expect an IAE
        try {
            ModuleHelper.AddModulePackage(cl1, "new_package");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Null package argument, expect an NPE
        try {
            ModuleHelper.AddModulePackage(module_one, null);
            throw new RuntimeException("Failed to get the expected NPE");
        } catch(NullPointerException e) {
            // Expected
        }

        // Existing package, expect an ISE
        try {
            ModuleHelper.AddModulePackage(module_one, "yourpackage");
            throw new RuntimeException("Failed to get the expected ISE");
        } catch(IllegalStateException e) {
            // Expected
        }

        // Invalid package name, expect an IAE
        try {
            ModuleHelper.AddModulePackage(module_one, "your.package");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Invalid package name, expect an IAE
        try {
            ModuleHelper.AddModulePackage(module_one, ";your/package");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Invalid package name, expect an IAE
        try {
            ModuleHelper.AddModulePackage(module_one, "7[743");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Empty package name, expect an IAE
        try {
            ModuleHelper.AddModulePackage(module_one, "");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Add package named "java" to an module defined to a class loader other than the boot or platform loader.
        try {
            // module_one is defined to a MyClassLoader class loader.
            ModuleHelper.AddModulePackage(module_one, "java/foo");
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("prohibited package name")) {
              throw new RuntimeException("Failed to get expected IAE message for prohibited package name: " + e.getMessage());
            }
        }

        // Package "javabar" should be ok
        ModuleHelper.AddModulePackage(module_one, "javabar");

        // Package named "java" defined to the boot class loader, should be ok
        Object module_javabase = module_one.getClass().getModule();
        ModuleHelper.AddModulePackage(module_javabase, "java/foo");

        // Package named "java" defined to the platform class loader, should be ok
        // The module java.sql is defined to the platform class loader.
        java.sql.Time jst = new java.sql.Time(45000); // milliseconds
        Object module_javasql = jst.getClass().getModule();
        ModuleHelper.AddModulePackage(module_javasql, "java/foo");
    }

    static class MyClassLoader extends ClassLoader { }
}

