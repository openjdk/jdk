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
 * @compile p2/c2.java
 * @compile p1/c1.java
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI ExportTwice
 */

import java.lang.reflect.Module;
import static jdk.test.lib.Asserts.*;

public class ExportTwice {

    // Check that a package can not be exported unqualifiedly, and then exported
    // to a specific package.
    // Also, check that a package can be exported to a specific package and then
    // exported unqualifiedly.
    public static void main(String args[]) throws Throwable {
        Object m1, m2, m3;

        // Get the java.lang.reflect.Module object for module java.base.
        Class jlObject = Class.forName("java.lang.Object");
        Object jlObject_jlrM = jlObject.getModule();
        assertNotNull(jlObject_jlrM, "jlrModule object of java.lang.Object should not be null");

        // Get the class loader for ExportTwice and assume it's also used to
        // load classes p1.c1 and p2.c2.
        ClassLoader this_cldr = ExportTwice.class.getClassLoader();

        // Define a module for p1.
        m1 = ModuleHelper.ModuleObject("module1", this_cldr, new String[] { "p1" });
        assertNotNull(m1, "Module should not be null");
        ModuleHelper.DefineModule(m1, "9.0", "m1/here", new String[] { "p1" });
        ModuleHelper.AddReadsModule(m1, jlObject_jlrM);

        // Define a module for p2.
        m2 = ModuleHelper.ModuleObject("module2", this_cldr, new String[] { "p2" });
        assertNotNull(m2, "Module should not be null");
        ModuleHelper.DefineModule(m2, "9.0", "m2/there", new String[] { "p2" });
        ModuleHelper.AddReadsModule(m2, jlObject_jlrM);

        // Define a module for p3.
        m3 = ModuleHelper.ModuleObject("module3", this_cldr, new String[] { "p3" });
        assertNotNull(m3, "Module should not be null");
        ModuleHelper.DefineModule(m3, "9.0", "m3/there", new String[] { "p3" });
        ModuleHelper.AddReadsModule(m3, jlObject_jlrM);

        // Make package p1 in m1 visible to everyone.
        ModuleHelper.AddModuleExportsToAll(m1, "p1");

        // Try to export p1 only to m2 after it was exported unqualifiedly.  It
        // should silently succeed.
        ModuleHelper.AddModuleExports(m1, "p1", m2);

        // Export p2 to m3 then export it again unqualifiedly.
        ModuleHelper.AddModuleExports(m2, "p2", m3);
        ModuleHelper.AddModuleExportsToAll(m2, "p2");

        // p1.c1's ctor tries to call a method in p2.c2.  This should work because
        // p1's module can read p2's module and p2 is now exported unqualifiedly.
        ModuleHelper.AddReadsModule(m1, m2);
        Class p1_c1_class = Class.forName("p1.c1");
        p1_c1_class.newInstance();
    }
}
