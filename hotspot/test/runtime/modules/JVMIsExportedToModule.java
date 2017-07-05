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
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary /test/lib /compiler/whitebox ..
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JVMIsExportedToModule
 */

import static jdk.test.lib.Asserts.*;

public class JVMIsExportedToModule {

    public static void main(String args[]) throws Throwable {
        MyClassLoader from_cl = new MyClassLoader();
        MyClassLoader to_cl = new MyClassLoader();
        Object from_module, to_module;
        boolean result;

        from_module = ModuleHelper.ModuleObject("from_module", from_cl, new String[] { "mypackage", "this/package" });
        assertNotNull(from_module, "Module from_module should not be null");
        ModuleHelper.DefineModule(from_module, "9.0", "from_module/here", new String[] { "mypackage", "this/package" });
        to_module = ModuleHelper.ModuleObject("to_module", to_cl, new String[] { "yourpackage", "that/package" });
        assertNotNull(to_module, "Module to_module should not be null");
        ModuleHelper.DefineModule(to_module, "9.0", "to_module/here", new String[] { "yourpackage", "that/package" });

        Object unnamed_module = JVMIsExportedToModule.class.getModule();
        assertNotNull(unnamed_module, "Module unnamed_module should not be null");

        // Null from_module argument, expect an NPE
        try {
            result = ModuleHelper.IsExportedToModule(null, "mypackage", to_module);
            throw new RuntimeException("Failed to get the expected NPE for null from_module");
        } catch(NullPointerException e) {
            // Expected
        }

        // Null to_module argument, expect an NPE
        try {
          result = ModuleHelper.IsExportedToModule(from_module, "mypackage", null);
          throw new RuntimeException("Failed to get expected NPE for null to_module");
        } catch(NullPointerException e) {
            // Expected
        }

        // Null package argument, expect an NPE
        try {
            result = ModuleHelper.IsExportedToModule(from_module, null, to_module);
            throw new RuntimeException("Failed to get the expected NPE for null package");
        } catch(NullPointerException e) {
            // Expected
        }

        // Bad from_module argument, expect an IAE
        try {
            result = ModuleHelper.IsExportedToModule(to_cl, "mypackage", to_module);
            throw new RuntimeException("Failed to get the expected IAE for bad from_module");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad to_module argument, expect an IAE
        try {
            result = ModuleHelper.IsExportedToModule(from_module, "mypackage", from_cl);
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Check that package is exported to its own module
        result = ModuleHelper.IsExportedToModule(from_module, "mypackage", from_module);
        assertTrue(result, "Package is always exported to itself");

        // Package is not in to_module, expect an IAE
        try {
            result = ModuleHelper.IsExportedToModule(from_module, "yourpackage", from_cl);
            throw new RuntimeException("Failed to get the expected IAE for package not in to_module");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Package is accessible when exported to unnamed module
        ModuleHelper.AddModuleExportsToAll(from_module, "mypackage");
        result = ModuleHelper.IsExportedToModule(from_module, "mypackage", to_module);
        assertTrue(result, "Package exported to unnamed module is visible to named module");

        result = ModuleHelper.IsExportedToModule(from_module, "mypackage", unnamed_module);
        assertTrue(result, "Package exported to unnamed module is visible to unnamed module");

        // Package is accessible only to named module when exported to named module
        ModuleHelper.AddModuleExports(from_module, "this/package", to_module);
        result = ModuleHelper.IsExportedToModule(from_module, "this/package", to_module);
        assertTrue(result, "Package exported to named module is visible to named module");
        result = ModuleHelper.IsExportedToModule(from_module, "this/package", unnamed_module);
        assertTrue(!result, "Package exported to named module is not visible to unnamed module");
    }

    static class MyClassLoader extends ClassLoader { }
}
