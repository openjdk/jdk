/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import jdk.internal.org.objectweb.asm.AnnotationVisitor;
import jdk.internal.org.objectweb.asm.Attribute;
import jdk.internal.org.objectweb.asm.ClassReader;
import jdk.internal.org.objectweb.asm.ClassVisitor;
import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.Opcodes;
import jdk.internal.org.objectweb.asm.commons.ModuleTargetAttribute;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

/**
 * @test
 * @modules java.base/jdk.internal.org.objectweb.asm
 *          java.base/jdk.internal.org.objectweb.asm.commons
 *          java.base/jdk.internal.module
 *          java.xml
 * @run testng AnnotationsTest
 * @summary Basic test of annotations on modules
 */

public class AnnotationsTest {

    /**
     * Test that there are no annotations on an unnamed module.
     */
    @Test
    public void testUnnamedModule() {
        Module module = this.getClass().getModule();
        assertTrue(module.getAnnotations().length == 0);
        assertTrue(module.getDeclaredAnnotations().length == 0);
    }

    /**
     * Test loading a module with a RuntimeVisibleAnnotation attribute.
     * The test copies the module-info.class for java.xml, adds the attribute,
     * and then loads the updated module.
     */
    @Test
    public void testNamedModule() throws IOException {

        // "deprecate" java.xml
        Path dir = Files.createTempDirectory(Paths.get(""), "mods");
        deprecateModule("java.xml", true, "9", dir);

        // "load" the cloned java.xml
        Module module = loadModule(dir, "java.xml");

        // check the annotation is present
        assertTrue(module.isAnnotationPresent(Deprecated.class));
        Deprecated d = module.getAnnotation(Deprecated.class);
        assertNotNull(d, "@Deprecated not found");
        assertTrue(d.forRemoval());
        assertEquals(d.since(), "9");
        Annotation[] a = module.getAnnotations();
        assertTrue(a.length == 1);
        assertTrue(a[0] instanceof Deprecated);
        assertEquals(module.getDeclaredAnnotations(), a);
    }


    /**
     * Copy the module-info.class for the given module, add the
     * Deprecated annotation, and write the updated module-info.class
     * to a directory.
     */
    static void deprecateModule(String name,
                                boolean forRemoval,
                                String since,
                                Path output) throws IOException {
        Module module = ModuleLayer.boot().findModule(name).orElse(null);
        assertNotNull(module, name + " not found");

        InputStream in = module.getResourceAsStream("module-info.class");
        assertNotNull(in, "No module-info.class for " + name);

        try (in) {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                                             + ClassWriter.COMPUTE_FRAMES);

            ClassVisitor cv = new ClassVisitor(Opcodes.ASM6, cw) { };

            ClassReader cr = new ClassReader(in);
            List<Attribute> attrs = new ArrayList<>();
            attrs.add(new ModuleTargetAttribute());
            cr.accept(cv, attrs.toArray(new Attribute[0]), 0);

            AnnotationVisitor annotationVisitor
                = cv.visitAnnotation("Ljava/lang/Deprecated;", true);
            annotationVisitor.visit("forRemoval", forRemoval);
            annotationVisitor.visit("since", since);
            annotationVisitor.visitEnd();

            byte[] bytes = cw.toByteArray();
            Path mi = output.resolve("module-info.class");
            Files.write(mi, bytes);
        }
    }

    /**
     * Load the module of the given name in the given directory into a
     * child layer.
     */
    static Module loadModule(Path dir, String name) throws IOException {
        ModuleFinder finder = ModuleFinder.of(dir);

        ModuleLayer bootLayer = ModuleLayer.boot();

        Configuration cf = bootLayer.configuration()
                .resolve(finder, ModuleFinder.of(), Set.of(name));

        ClassLoader scl = ClassLoader.getSystemClassLoader();
        ModuleLayer layer = bootLayer.defineModulesWithOneLoader(cf, scl);

        Module module = layer.findModule(name).orElse(null);
        assertNotNull(module, name + " not loaded");
        return module;
    }
}
