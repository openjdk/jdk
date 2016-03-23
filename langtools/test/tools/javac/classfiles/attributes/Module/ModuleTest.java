/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Module attribute tests
 * @bug 8080878
 * @modules jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.classfile
 *          jdk.jdeps/com.sun.tools.javap
 * @library /tools/lib ../lib /tools/javac/lib
 * @build ToolBox TestBase TestResult ModuleTestBase
 * @run main ModuleTest
 */

import java.nio.file.Path;

public class ModuleTest extends ModuleTestBase {

    public static void main(String[] args) throws Exception {
        new ModuleTest().run();
    }

    @Test
    public void testEmptyModule(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exports("pack")
                .write(base);
        tb.writeJavaFiles(base, "package pack; public class C extends java.util.ArrayList{}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exports("pack")
                .exports("pack2")
                .exports("pack3")
                .write(base);
        tb.writeJavaFiles(base,
                "package pack; public class A {}",
                "package pack2; public class B {}",
                "package pack3; public class C {}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testQualifiedExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exportsTo("pack", "jdk.compiler")
                .write(base);
        tb.writeJavaFiles(base, "package pack; public class A {}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralQualifiedExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exportsTo("pack", "jdk.compiler, java.xml")
                .exportsTo("pack2", "java.xml")
                .exportsTo("pack3", "jdk.compiler")
                .write(base);
        tb.writeJavaFiles(base,
                "package pack; public class A {}",
                "package pack2; public class B {}",
                "package pack3; public class C {}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testRequires(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requires("jdk.compiler")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testRequiresPublic(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requiresPublic("java.xml")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralRequires(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requiresPublic("java.xml")
                .requires("java.compiler")
                .requires("jdk.compiler")
                .requiresPublic("jdk.scripting.nashorn")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testProvides(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .provides("java.util.Collection", "pack2.D")
                .write(base);
        tb.writeJavaFiles(base, "package pack2; public class D extends java.util.ArrayList{}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralProvides(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .provides("java.util.Collection", "pack2.D")
                .provides("java.util.List", "pack2.D")
                .requires("java.logging")
                .provides("java.util.logging.Logger", "pack2.C")
                .write(base);
        tb.writeJavaFiles(base, "package pack2; public class D extends java.util.ArrayList{}",
                "package pack2; public class C extends java.util.logging.Logger{ " +
                        "public C() { super(\"\",\"\"); } \n" +
                        "C(String a,String b){" +
                        "    super(a,b);" +
                        "}}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testUses(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .uses("java.util.List")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralUses(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .uses("java.util.List")
                .uses("java.util.Collection")
                .requires("java.logging")
                .uses("java.util.logging.Logger")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testComplex(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exports("pack1")
                .exportsTo("packTo1", "java.xml")
                .requires("jdk.compiler")
                .requiresPublic("java.xml")
                .provides("java.util.List", "pack1.C")
                .uses("java.util.List")
                .uses("java.nio.file.Path")
                .provides("java.util.List", "pack2.D")
                .requiresPublic("java.desktop")
                .requires("java.compiler")
                .exportsTo("packTo2", "java.compiler")
                .exports("pack2")
                .write(base);
        tb.writeJavaFiles(base, "package pack1; public class C extends java.util.ArrayList{}",
                "package pack2; public class D extends java.util.ArrayList{}");
        tb.writeJavaFiles(base,
                "package packTo1; public class T1 {}",
                "package packTo2; public class T2 {}");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }
}
