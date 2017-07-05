/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
 * @summary Test that if module m1 can read module m2, AND package p2 in module2 is
 *          exported unqualifiedly, then class p1.c1 in m1 can read p2.c2 in m2.
 * @library /testlibrary /test/lib
 * @compile myloaders/MySameClassLoader.java
 * @compile p2/c2.java
 * @compile p1/c1.java
 * @build ExpUnqual
 * @run main/othervm -Xbootclasspath/a:. ExpUnqual
 */

import static jdk.test.lib.Asserts.*;

import java.lang.reflect.Layer;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import myloaders.MySameClassLoader;

public class ExpUnqual {

    // Create a Layer over the boot layer.
    // Define modules within this layer to test access between
    // publically defined classes within packages of those modules.
    public void createLayerOnBoot() throws Throwable {

        // Define module:     m1
        // Can read:          java.base, m2
        // Packages:          p1
        // Packages exported: p1 is exported unqualifiedly
        ModuleDescriptor descriptor_m1 =
                new ModuleDescriptor.Builder("m1")
                        .requires("java.base")
                        .requires("m2")
                        .exports("p1")
                        .build();

        // Define module:     m2
        // Can read:          java.base
        // Packages:          p2
        // Packages exported: p2 is exported unqualifiedly
        ModuleDescriptor descriptor_m2 =
                new ModuleDescriptor.Builder("m2")
                        .requires("java.base")
                        .exports("p2")
                        .build();

        // Set up a ModuleFinder containing all modules for this layer.
        ModuleFinder finder = ModuleLibrary.of(descriptor_m1, descriptor_m2);

        // Resolves "m1"
        Configuration cf = Layer.boot()
                .configuration()
                .resolveRequires(finder, ModuleFinder.empty(), Set.of("m1"));

        // map each module to the same class loader for this test
        Map<String, ClassLoader> map = new HashMap<>();
        map.put("m1", MySameClassLoader.loader1);
        map.put("m2", MySameClassLoader.loader1);

        // Create Layer that contains m1 & m2
        Layer layer = Layer.boot().defineModules(cf, map::get);

        assertTrue(layer.findLoader("m1") == MySameClassLoader.loader1);
        assertTrue(layer.findLoader("m2") == MySameClassLoader.loader1);

        // now use the same loader to load class p1.c1
        Class p1_c1_class = MySameClassLoader.loader1.loadClass("p1.c1");
        try {
            p1_c1_class.newInstance();
        } catch (IllegalAccessError e) {
            throw new RuntimeException("Test Failed, an IAE should not be thrown since p2 is exported unqualifiedly.");
        }
    }

    public static void main(String args[]) throws Throwable {
      ExpUnqual test = new ExpUnqual();
      test.createLayerOnBoot();
    }
}
