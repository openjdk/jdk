/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Tests that it is possible to load JVMCI classes from a custom class loader and
 *          that the loaded class is different than the classes loaded by the platform loader.
 *          This test also ensures that only JVMCI classes loaded by the platform loader
 *          will have their native methods linked to implementations in the JVM.
 * @modules java.base/jdk.internal.loader:+open
 * @compile alt/ResolvedJavaType.java
 * @compile alt/HotSpotJVMCIRuntime.java
 * @compile alt/CompilerToVM.java
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+EnableJVMCI LoadAlternativeJVMCI
 */
import java.io.File;
import java.net.URL;
import java.net.URI;
import java.net.URLClassLoader;
import java.lang.reflect.*;

import jdk.internal.loader.ClassLoaders;

public class LoadAlternativeJVMCI {
    public static void main(String[] args) throws Exception {
        String[] testClasses = System.getProperty("test.classes").split(File.pathSeparator);
        URL[] cp = new URL[testClasses.length];
        for (int i = 0; i < testClasses.length; i++) {
            String e = testClasses[i];
            if (new File(e).isDirectory()) {
                e = e + File.separator;
            }
            cp[i] = new URI("file:" + e).toURL();
        }

        Field blField = ClassLoaders.class.getDeclaredField("BOOT_LOADER");
        blField.setAccessible(true);
        ClassLoader boot = (ClassLoader) blField.get(null);
        URLClassLoader ucl = new URLClassLoader(cp, boot);
        ClassLoader pcl = ClassLoader.getPlatformClassLoader();

        String[] names = {
            "jdk.vm.ci.meta.ResolvedJavaType",
            "jdk.vm.ci.hotspot.CompilerToVM",
            "jdk.vm.ci.hotspot.HotSpotJVMCIRuntime"
        };
        for (String name : names) {
            Class<?> customClass = ucl.loadClass(name);
            Class<?> platformClass = pcl.loadClass(name);
            if (customClass.equals(platformClass)) {
                throw new AssertionError(String.format("%s loaded by %s should be distinct from version loaded by %s",
                                name, ucl, pcl));
            }
            Class<?> customClassAgain = ucl.loadClass(name);
            if (!customClassAgain.equals(customClass)) {
                throw new AssertionError(String.format("%s loaded twice by %s should produce the same class",
                                name, ucl));
            }

            if (name.equals("jdk.vm.ci.hotspot.CompilerToVM")) {
                // Detect refactoring of CompilerToVM.registerNatives so that alt/CompilerToVM.java
                // can be adjusted accordingly.
                try {
                    platformClass.getDeclaredMethod("registerNatives");
                } catch (NoSuchMethodException e) {
                    throw new AssertionError("missing method in platform JVMCI class: " + e);
                }

                // Only JVMCI classes loaded by the platform class loader can link to native
                // method implementations in HotSpot.
                try {
                    Class.forName(name, true, ucl);
                    throw new AssertionError("expected UnsatisfiedLinkError");
                } catch (UnsatisfiedLinkError e) {
                    if (!e.getMessage().contains(name + ".registerNatives")) {
                        throw new AssertionError("unexpected message: " + e.getMessage());
                    }
                }
            }
        }
    }
}
