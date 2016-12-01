/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8080878 8161906 8162713
 * @modules java.compiler
 *          jdk.compiler/com.sun.tools.javac.api
 *          jdk.compiler/com.sun.tools.javac.main
 *          jdk.compiler/com.sun.tools.javac.util
 *          jdk.jdeps/com.sun.tools.classfile
 * @library /tools/lib ../lib /tools/javac/lib
 * @build toolbox.ToolBox toolbox.JavacTask toolbox.ToolBox
 *      TestBase TestResult ModuleTestBase
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
        tb.writeJavaFiles(base, "package pack; public class C extends java.util.ArrayList{ }");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exports("pack")
                .exports("pack2")
                .exports("pack3")
                .exports("pack4")
                .exports("pack5")
                .write(base);
        tb.writeJavaFiles(base,
                "package pack; public class A { }",
                "package pack2; public class B { }",
                "package pack3; public class C { }",
                "package pack4; public class C { }",
                "package pack5; public class C { }");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testQualifiedExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exportsTo("pack", "jdk.compiler")
                .write(base);
        tb.writeJavaFiles(base, "package pack; public class A { }");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testQualifiedDynamicExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exportsTo("pack", "jdk.compiler")
                .write(base);
        tb.writeJavaFiles(base, "package pack; public class A { }");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralQualifiedExports(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exportsTo("pack", "jdk.compiler, jdk.jdeps")
                .exportsTo("pack2", "jdk.jdeps")
                .exportsTo("pack3", "jdk.compiler")
                .exportsTo("pack4", "jdk.compiler, jdk.jdeps")
                .exportsTo("pack5", "jdk.compiler")
                .write(base);
        tb.writeJavaFiles(base,
                "package pack; public class A {}",
                "package pack2; public class B {}",
                "package pack3; public class C {}",
                "package pack4; public class C {}",
                "package pack5; public class C {}");
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
    public void testRequiresTransitive(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requires("jdk.jdeps", RequiresFlag.TRANSITIVE)
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testRequiresStatic(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requires("jdk.jdeps", RequiresFlag.STATIC)
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralRequires(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .requires("jdk.jdeps", RequiresFlag.TRANSITIVE)
                .requires("jdk.compiler")
                .requires("m2", RequiresFlag.STATIC)
                .requires("m3")
                .requires("m4", RequiresFlag.TRANSITIVE)
                .requires("m5", RequiresFlag.STATIC, RequiresFlag.TRANSITIVE)
                .write(base.resolve("m1"));
        tb.writeJavaFiles(base.resolve("m2"), "module m2 { }");
        tb.writeJavaFiles(base.resolve("m3"), "module m3 { }");
        tb.writeJavaFiles(base.resolve("m4"), "module m4 { }");
        tb.writeJavaFiles(base.resolve("m5"), "module m5 { }");
        compile(base, "--module-source-path", base.toString(),
                "-d", base.toString());
        testModuleAttribute(base.resolve("m1"), moduleDescriptor);
    }

    @Test
    public void testProvides(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .provides("java.util.Collection", "pack2.D")
                .write(base);
        tb.writeJavaFiles(base, "package pack2; public class D extends java.util.ArrayList{ }");
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testSeveralProvides(Path base) throws Exception {
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .provides("java.util.Collection", "pack2.D")
                .provides("java.util.List", "pack2.D")
                .requires("jdk.compiler")
                .provides("com.sun.tools.javac.Main", "pack2.C")
                .write(base);
        tb.writeJavaFiles(base, "package pack2; public class D extends java.util.ArrayList{ }",
                "package pack2; public class C extends com.sun.tools.javac.Main{ }");
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
                .requires("jdk.compiler")
                .uses("javax.tools.JavaCompiler")
                .write(base);
        compile(base);
        testModuleAttribute(base, moduleDescriptor);
    }

    @Test
    public void testComplex(Path base) throws Exception {
        Path m1 = base.resolve("m1");
        ModuleDescriptor moduleDescriptor = new ModuleDescriptor("m1")
                .exports("pack1")
                .exports("pack3")
                .exportsTo("packTo1", "m2")
                .exportsTo("packTo3", "m3")
                .requires("jdk.compiler")
                .requires("m2", RequiresFlag.TRANSITIVE)
                .requires("m3", RequiresFlag.STATIC)
                .requires("m4", RequiresFlag.TRANSITIVE, RequiresFlag.STATIC)
                .provides("java.util.List", "pack1.C", "pack2.D")
                .uses("java.util.List")
                .uses("java.nio.file.Path")
                .requires("jdk.jdeps", RequiresFlag.STATIC, RequiresFlag.TRANSITIVE)
                .requires("m5", RequiresFlag.STATIC)
                .requires("m6", RequiresFlag.TRANSITIVE)
                .requires("java.compiler")
                .exportsTo("packTo4", "java.compiler")
                .exportsTo("packTo2", "java.compiler")
                .exports("pack4")
                .exports("pack2")
                .write(m1);
        tb.writeJavaFiles(m1, "package pack1; public class C extends java.util.ArrayList{ }",
                "package pack2; public class D extends java.util.ArrayList{ }",
                "package pack3; public class D extends java.util.ArrayList{ }",
                "package pack4; public class D extends java.util.ArrayList{ }");
        tb.writeJavaFiles(m1,
                "package packTo1; public class T1 {}",
                "package packTo2; public class T2 {}",
                "package packTo3; public class T3 {}",
                "package packTo4; public class T4 {}");
        tb.writeJavaFiles(base.resolve("m2"), "module m2 { }");
        tb.writeJavaFiles(base.resolve("m3"), "module m3 { }");
        tb.writeJavaFiles(base.resolve("m4"), "module m4 { }");
        tb.writeJavaFiles(base.resolve("m5"), "module m5 { }");
        tb.writeJavaFiles(base.resolve("m6"), "module m6 { }");
        compile(base, "--module-source-path", base.toString(),
                "-d", base.toString());
        testModuleAttribute(m1, moduleDescriptor);
    }
}
