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
 * @compile p2/c2.java
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/reflect/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI JVMGetModuleByPkgName
 */

import static jdk.test.lib.Asserts.*;
import java.lang.ClassLoader;
import java.lang.reflect.Module;

public class JVMGetModuleByPkgName {

    public static void main(String args[]) throws Throwable {

        Module javaBase = ModuleHelper.GetModuleByPackageName(null, "java/lang");
        if (!javaBase.getName().equals("java.base")) {
            throw new RuntimeException(
                "Failed to get module java.base for package java/lang");
        }

        if (ModuleHelper.GetModuleByPackageName(null, "bad.package.name") != null) {
            throw new RuntimeException("Failed to get null for bad.package.name");
        }

        ClassLoader systemLoader = ClassLoader.getSystemClassLoader();
        if (ModuleHelper.GetModuleByPackageName(systemLoader, "java/lang") != null) {
            throw new RuntimeException(
                "Failed to get null for systemClassLoader and java/lang");
        }

        try {
            ModuleHelper.GetModuleByPackageName(systemLoader, null);
            throw new RuntimeException(
                "Failed to throw NullPointerException for null package name");
        } catch(NullPointerException e) {
             // Expected
        }

        Module unnamedModule = ModuleHelper.GetModuleByPackageName(systemLoader, "");
        if (unnamedModule.isNamed()) {
            throw new RuntimeException(
                "Unexpected named module returned for unnamed package");
        }

        p2.c2 obj = new p2.c2();
        unnamedModule = ModuleHelper.GetModuleByPackageName(systemLoader, "p2");
        if (unnamedModule.isNamed()) {
            throw new RuntimeException(
                "Unexpected named module returned for package p2 in unnamed module");
        }

        MyClassLoader cl1 = new MyClassLoader();
        Module module1 = (Module)ModuleHelper.ModuleObject("module1", cl1, new String[] { "mypackage" });
        assertNotNull(module1, "Module should not be null");
        ModuleHelper.DefineModule(module1, "9.0", "module1/here", new String[] { "mypackage" });
        if (ModuleHelper.GetModuleByPackageName(cl1, "mypackage") != module1) {
            throw new RuntimeException("Wrong module returned for cl1 mypackage");
        }
    }

    static class MyClassLoader extends ClassLoader { }
}
