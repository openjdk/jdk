/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @library /testlibrary  /test/lib /compiler/whitebox ..
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JVMDefineModule
 */

import static jdk.test.lib.Asserts.*;

public class JVMDefineModule {

    public static void main(String args[]) throws Throwable {
        MyClassLoader cl = new MyClassLoader();
        Object m;

        // NULL classloader argument, expect success
        m = ModuleHelper.ModuleObject("mymodule", null, new String[] { "mypackage" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "mymodule/here", new String[] { "mypackage" });

/* Invalid test, won't compile.
        // Invalid classloader argument, expect an IAE
        try {
            m = ModuleHelper.ModuleObject("mymodule1", new Object(), new String[] { "mypackage1" });
            ModuleHelper.DefineModule(m,  "9.0", "mymodule/here", new String[] { "mypackage1" });
            throw new RuntimeException("Failed to get expected IAE for bad loader");
        } catch(IllegalArgumentException e) {
            // Expected
        }
*/

        // NULL package argument, should not throw an exception
        m = ModuleHelper.ModuleObject("mymodule2", cl, new String[] { "nullpkg" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "mymodule2/here", null);

        // Null module argument, expect an NPE
        try {
            ModuleHelper.DefineModule(null,  "9.0", "mymodule/here", new String[] { "mypackage1" });
            throw new RuntimeException("Failed to get expected NPE for null module");
        } catch(NullPointerException e) {
            if (!e.getMessage().contains("Null module object")) {
              throw new RuntimeException("Failed to get expected IAE message for null module: " + e.getMessage());
            }
            // Expected
        }

        // Invalid module argument, expect an IAE
        try {
            ModuleHelper.DefineModule(new Object(),  "9.0", "mymodule/here", new String[] { "mypackage1" });
            throw new RuntimeException("Failed to get expected IAE or NPE for bad module");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("module is not a subclass")) {
              throw new RuntimeException("Failed to get expected IAE message for bad module: " + e.getMessage());
            }
        }

        // NULL module name, expect an IAE or NPE
        try {
            m = ModuleHelper.ModuleObject(null, cl, new String[] { "mypackage2" });
            ModuleHelper.DefineModule(m, "9.0", "mymodule/here", new String[] { "mypackage2" });
            throw new RuntimeException("Failed to get expected NPE for NULL module");
        } catch(IllegalArgumentException e) {
            // Expected
        } catch(NullPointerException e) {
            // Expected
        }

        // module name is java.base, expect an IAE
        m = ModuleHelper.ModuleObject("java.base", cl, new String[] { "mypackage3" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "mymodule/here", new String[] { "mypackage3" });
            throw new RuntimeException("Failed to get expected IAE for java.base, not defined with boot class loader");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Class loader must be the boot class loader")) {
              throw new RuntimeException("Failed to get expected IAE message for java.base: " + e.getMessage());
            }
        }

        // Duplicates in package list, expect an IAE
        m = ModuleHelper.ModuleObject("module.x", cl, new String[] { "mypackage4", "mypackage5" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "mymodule/here", new String[] { "mypackage4", "mypackage5", "mypackage4" });
            throw new RuntimeException("Failed to get IAE for duplicate packages");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Duplicate package name")) {
              throw new RuntimeException("Failed to get expected IAE message for duplicate package: " + e.getMessage());
            }
        }

        // Empty entry in package list, expect an IAE
        m = ModuleHelper.ModuleObject("module.y", cl, new String[] { "mypackageX", "mypackageY" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "mymodule/here", new String[] { "mypackageX", "", "mypackageY" });
            throw new RuntimeException("Failed to get IAE for empty package");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Invalid package name")) {
              throw new RuntimeException("Failed to get expected IAE message empty package entry: " + e.getMessage());
            }
        }

        // Duplicate module name, expect an IAE
        m = ModuleHelper.ModuleObject("module.name", cl, new String[] { "mypackage6" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage6" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage6a" });
            throw new RuntimeException("Failed to get IAE for duplicate module");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Module module.name is already defined")) {
              throw new RuntimeException("Failed to get expected IAE message for duplicate module: " + e.getMessage());
            }
        }

        // Package is already defined for class loader, expect an IAE
        m = ModuleHelper.ModuleObject("dupl.pkg.module", cl, new String[] { "mypackage6b" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage6" });
            throw new RuntimeException("Failed to get IAE for existing package");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Package mypackage6 for module dupl.pkg.module already exists for class loader")) {
              throw new RuntimeException("Failed to get expected IAE message for duplicate package: " + e.getMessage());
            }
        }

        // Empty module name, expect an IAE
        try {
            m = ModuleHelper.ModuleObject("", cl, new String[] { "mypackage8" });
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage8" });
            throw new RuntimeException("Failed to get expected IAE for empty module name");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad module name, expect an IAE
        try {
            m = ModuleHelper.ModuleObject("bad;name", cl, new String[] { "mypackage9" });
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage9" });
            throw new RuntimeException("Failed to get expected IAE for bad;name");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad module name, expect an IAE
        try {
            m = ModuleHelper.ModuleObject(".leadingdot", cl, new String[] { "mypackage9a" });
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage9a" });
            throw new RuntimeException("Failed to get expected IAE for .leadingdot");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad module name, expect an IAE
        try {
            m = ModuleHelper.ModuleObject("trailingdot.", cl, new String[] { "mypackage9b" });
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage9b" });
            throw new RuntimeException("Failed to get expected IAE for trailingdot.");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad module name, expect an IAE
        m = ModuleHelper.ModuleObject("consecutive..dots", cl, new String[] { "mypackage9c" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage9c" });
            throw new RuntimeException("Failed to get expected IAE for consecutive..dots");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // module name with multiple dots, should be okay
        m = ModuleHelper.ModuleObject("more.than.one.dat", cl, new String[] { "mypackage9d" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "mypackage9d" });

        // Zero length package list, should be okay
        m = ModuleHelper.ModuleObject("zero.packages", cl, new String[] { });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { });

        // Invalid package name, expect an IAE
        m = ModuleHelper.ModuleObject("module5", cl, new String[] { "your.package" });
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "your.package" });
            throw new RuntimeException("Failed to get expected IAE for your.package");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Invalid package name")) {
              throw new RuntimeException("Failed to get expected IAE message for bad package name: " + e.getMessage());
            }
        }

        // Invalid package name, expect an IAE
        m = ModuleHelper.ModuleObject("module6", cl, new String[] { "foo" }); // Name irrelevant
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { ";your/package" });
            throw new RuntimeException("Failed to get expected IAE for ;your.package");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Invalid package name")) {
              throw new RuntimeException("Failed to get expected IAE message for bad package name: " + e.getMessage());
            }
        }

        // Invalid package name, expect an IAE
        m = ModuleHelper.ModuleObject("module7", cl, new String[] { "foo" }); // Name irrelevant
        try {
            ModuleHelper.DefineModule(m, "9.0", "module.name/here", new String[] { "7[743" });
            throw new RuntimeException("Failed to get expected IAE for package 7[743");
        } catch(IllegalArgumentException e) {
            if (!e.getMessage().contains("Invalid package name")) {
              throw new RuntimeException("Failed to get expected IAE message for bad package name: " + e.getMessage());
            }
        }

        // module version that is null, should be okay
        m = ModuleHelper.ModuleObject("module8", cl, new String[] { "a_package_8" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, null, "module8/here", new String[] { "a_package_8" });

        // module version that is "", should be okay
        m = ModuleHelper.ModuleObject("module9", cl, new String[] { "a_package_9" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "", "module9/here", new String[] { "a_package_9" });

        // module location that is null, should be okay
        m = ModuleHelper.ModuleObject("module10", cl, new String[] { "a_package_10" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", null, new String[] { "a_package_10" });

        // module location that is "", should be okay
        m = ModuleHelper.ModuleObject("module11", cl, new String[] { "a_package_11" });
        assertNotNull(m, "Module should not be null");
        ModuleHelper.DefineModule(m, "9.0", "", new String[] { "a_package_11" });
    }

    static class MyClassLoader extends ClassLoader { }
}
