/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @key randomness
 * @library /test/lib
 * @requires vm.flagless
 * @modules java.base/jdk.internal.vm.annotation
 * @enablePreview
 * @compile ValueClassGenerator.java ValueComparisonTest.java
 * @run main/othervm runtime.valhalla.inlinetypes.field_layout.ValueComparisonTest
 */

package runtime.valhalla.inlinetypes.field_layout;

import java.io.File;
import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import jdk.test.lib.Utils;

public class ValueComparisonTest {

    static void runSubstitutabilityTest(String classname, Path tempWorkDir) {
        Class<?> c = null;

        URL[] cp = null;
        try {
            cp = new URL[]{tempWorkDir.toFile().toURI().toURL()};
        } catch (MalformedURLException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        URLClassLoader urlcl = new URLClassLoader(cp);
        try {
            c = urlcl.loadClass(classname);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
        try {
            Method m = c.getDeclaredMethod("substitutabilityTest");
            m.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        long seed = 0;
        String seedString = System.getProperty("CLASS_GENERATION_SEED");
        if (seedString != null) {
            try {
                seed = Long.parseLong(seedString);
            } catch(NumberFormatException e) { }
        }
        if (seed == 0) {
            seed = System.nanoTime();
        }
        System.out.println("Random seed for class generation: " + seed);
        Path tempWorkDir;
        try {
            tempWorkDir = Utils.createTempDirectory("generatedClasses_" + seed);
        } catch (Exception e) {
            System.err.println("Failed to create temporary directory: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        var gen = new ValueClassGenerator(seed, 256);
        gen.generateAll(128,  tempWorkDir);
        for (String classname : gen.getValueClassesNames()) {
            runSubstitutabilityTest(classname, tempWorkDir);
        }
    }
}
