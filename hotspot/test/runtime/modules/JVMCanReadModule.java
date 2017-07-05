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
 * @library /testlibrary /test/lib /compiler/whitebox ..
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JVMCanReadModule
 */

import static jdk.test.lib.Asserts.*;

public class JVMCanReadModule {

    public static void main(String args[]) throws Throwable {
        MyClassLoader asking_cl = new MyClassLoader();
        MyClassLoader target_cl = new MyClassLoader();
        Object asking_module, target_module;
        boolean result;

        asking_module = ModuleHelper.ModuleObject("asking_module", asking_cl, new String[] { "mypackage" });
        assertNotNull(asking_module, "Module should not be null");
        ModuleHelper.DefineModule(asking_module, "9.0", "asking_module/here", new String[] { "mypackage" });
        target_module = ModuleHelper.ModuleObject("target_module", target_cl, new String[] { "yourpackage" });
        assertNotNull(target_module, "Module should not be null");
        ModuleHelper.DefineModule(target_module, "9.0", "target_module/here", new String[] { "yourpackage" });

        // Set up relationship
        ModuleHelper.AddReadsModule(asking_module, target_module);

        // Null asking_module argument, expect an NPE
        try {
            result = ModuleHelper.CanReadModule(null, target_module);
            throw new RuntimeException("Failed to get the expected NPE");
        } catch(NullPointerException e) {
            // Expected
        }

        // Bad asking_module argument, expect an IAE
        try {
            result = ModuleHelper.CanReadModule(asking_cl, target_module);
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Bad target_module argument, expect an IAE
        try {
            result = ModuleHelper.CanReadModule(asking_module, asking_cl);
            throw new RuntimeException("Failed to get the expected IAE");
        } catch(IllegalArgumentException e) {
            // Expected
        }

        // Verify strict modules can not read the unnamed module
        result = ModuleHelper.CanReadModule(target_module, null);
        assertFalse(result, "target_module can not read unnamed module");

        // Verify asking_module can read itself
        result = ModuleHelper.CanReadModule(asking_module, asking_module);
        assertTrue(result, "asking_module can read itself");

        // Verify asking_module can read target_module
        result = ModuleHelper.CanReadModule(asking_module, target_module);
        assertTrue(result, "asking_module can read target_module");

        // Verify target_module cannot read asking_module
        result = ModuleHelper.CanReadModule(target_module, asking_module);
        assertTrue(!result, "target_module cannot read asking_module");
    }

    static class MyClassLoader extends ClassLoader { }
}
