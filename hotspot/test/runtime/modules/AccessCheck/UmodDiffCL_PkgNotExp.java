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
 * @summary class p1.c1 defined in unnamed module tries to access p2.c2 defined in m2.
 *          Access is denied since even though unnamed module can read all modules, p2
 *          in module m2 is not exported at all.
 * @modules java.base/jdk.internal.misc
 * @library /testlibrary /test/lib
 * @compile myloaders/MyDiffClassLoader.java
 * @compile p1/c1.java
 * @build UmodDiffCL_PkgNotExp
 * @run main/othervm -Xbootclasspath/a:. UmodDiffCL_PkgNotExp
 */

import static jdk.test.lib.Asserts.*;

import java.lang.reflect.Layer;
import java.lang.module.Configuration;
import java.lang.module.ModuleDescriptor;
import java.lang.module.ModuleFinder;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import myloaders.MyDiffClassLoader;

//
// ClassLoader1 --> defines m1 --> no packages
// ClassLoader2 --> defines m2 --> packages p2
//
// m1 can read m2
// package p2 in m2 is not exported
//
// class p1.c1 defined in unnamed module tries to access p2.c2 defined in m2
// Access denied since even though unnamed module can read all modules, p2
// in module m2 is not exported at all.
//
public class UmodDiffCL_PkgNotExp {

    // Create a Layer over the boot layer.
    // Define modules within this layer to test access between
    // publically defined classes within packages of those modules.
    public void createLayerOnBoot() throws Throwable {

        // Define module:     m1
        // Can read:          java.base, m2
        // Packages:          none
        // Packages exported: none
        ModuleDescriptor descriptor_m1 =
                new ModuleDescriptor.Builder("m1")
                        .requires("java.base")
                        .requires("m2")
                        .build();

        // Define module:     m2
        // Can read:          java.base
        // Packages:          p2
        // Packages exported: none
        ModuleDescriptor descriptor_m2 =
                new ModuleDescriptor.Builder("m2")
                        .requires("java.base")
                        .conceals("p2")
                        .build();

        // Set up a ModuleFinder containing all modules for this layer.
        ModuleFinder finder = ModuleLibrary.of(descriptor_m1, descriptor_m2);

        // Resolves "m1"
        Configuration cf = Layer.boot()
                .configuration()
                .resolveRequires(finder, ModuleFinder.empty(), Set.of("m1"));

        // map each module to differing class loaders for this test
        Map<String, ClassLoader> map = new HashMap<>();
        map.put("m1", MyDiffClassLoader.loader1);
        map.put("m2", MyDiffClassLoader.loader2);

        // Create Layer that contains m1 & m2
        Layer layer = Layer.boot().defineModules(cf, map::get);

        assertTrue(layer.findLoader("m1") == MyDiffClassLoader.loader1);
        assertTrue(layer.findLoader("m2") == MyDiffClassLoader.loader2);
        assertTrue(layer.findLoader("java.base") == null);

        // now use the same loader to load class p1.c1
        // NOTE: module m1 does not define a package named p1.
        //       p1 will be loaded in an unnamed module.
        Class p1_c1_class = MyDiffClassLoader.loader1.loadClass("p1.c1");
        try {
            p1_c1_class.newInstance();
            throw new RuntimeException("Failed to get IAE (p2 in m2 is not exported to an unnamed module)");
        } catch (IllegalAccessError e) {
          System.out.println(e.getMessage());
          if (!e.getMessage().contains("does not export")) {
              throw new RuntimeException("Wrong message: " + e.getMessage());
          }
        }
    }

    public static void main(String args[]) throws Throwable {
      UmodDiffCL_PkgNotExp test = new UmodDiffCL_PkgNotExp();
      test.createLayerOnBoot();
    }
}
