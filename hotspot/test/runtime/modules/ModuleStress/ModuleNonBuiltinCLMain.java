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

import static jdk.test.lib.Asserts.*;

import java.lang.reflect.Layer;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

//
// ClassLoader1 --> defines m1 --> packages p1
// ClassLoader2 --> defines m2 --> packages p2
// Java System Class Loader --> defines m3 --> packages p3
//
// m1 can read m2
// package p2 in m2 is exported to m1 and m3
//
// class p1.c1 defined in m1 tries to access p2.c2 defined in m2
// Access allowed since m1 can read m2 and package p2 is exported to m1.
//
public class ModuleNonBuiltinCLMain {

    // Create a Layer over the boot layer.
    // Define modules within this layer to test access between
    // publically defined classes within packages of those modules.
    public void createLayerOnBoot() throws Throwable {

        // Define module:     m1
        // Can read:          java.base, m2
        // Packages:          p1
        // Packages exported: p1 is exported to unqualifiedly
        ModuleDescriptor descriptor_m1 =
                new ModuleDescriptor.Builder("m1")
                        .requires("java.base")
                        .requires("m2")
                        .exports("p1")
                        .build();

        // Define module:     m2
        // Can read:          java.base, m3
        // Packages:          p2
        // Packages exported: package p2 is exported to m1 and m3
        Set<String> targets = new HashSet<>();
        targets.add("m1");
        targets.add("m3");
        ModuleDescriptor descriptor_m2 =
                new ModuleDescriptor.Builder("m2")
                        .requires("java.base")
                        .requires("m3")
                        .exports("p2", targets)
                        .build();

        // Define module:     m3
        // Can read:          java.base
        // Packages:          p3
        // Packages exported: none
        ModuleDescriptor descriptor_m3 =
                new ModuleDescriptor.Builder("m3")
                        .requires("java.base")
                        .build();

        // Set up a ModuleFinder containing all modules for this layer.
        ModuleFinder finder = ModuleLibrary.of(descriptor_m1, descriptor_m2, descriptor_m3);

        // Resolves "m1"
        Configuration cf = Layer.boot()
                .configuration()
                .resolveRequires(finder, ModuleFinder.of(), Set.of("m1"));

        // map each module to differing user defined class loaders for this test
        Map<String, ClassLoader> map = new HashMap<>();
        Loader1 cl1 = new Loader1();
        Loader2 cl2 = new Loader2();
        ClassLoader cl3 = ClassLoader.getSystemClassLoader();
        map.put("m1", cl1);
        map.put("m2", cl2);
        map.put("m3", cl3);

        // Create Layer that contains m1 & m2
        Layer layer = Layer.boot().defineModules(cf, map::get);
        assertTrue(layer.findLoader("m1") == cl1);
        assertTrue(layer.findLoader("m2") == cl2);
        assertTrue(layer.findLoader("m3") == cl3);
        assertTrue(layer.findLoader("java.base") == null);

        // now use the same loader to load class p1.c1
        Class p1_c1_class = cl1.loadClass("p1.c1");
        try {
            p1_c1_class.newInstance();
        } catch (IllegalAccessError e) {
            throw new RuntimeException("Test Failed, an IAE should not be thrown since p2 is exported qualifiedly to m1");
        }
    }

    public static void main(String args[]) throws Throwable {
      ModuleNonBuiltinCLMain test = new ModuleNonBuiltinCLMain();
      test.createLayerOnBoot();
    }

    static class Loader1 extends ClassLoader { }
    static class Loader2 extends ClassLoader { }
}
