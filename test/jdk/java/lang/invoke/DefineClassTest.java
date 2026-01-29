/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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

/* @test
 * @modules java.base/java.lang:open
 * @run junit/othervm test.DefineClassTest
 * @summary Basic test for java.lang.invoke.MethodHandles.Lookup.defineClass
 */

package test;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.AccessFlag;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.lang.classfile.ClassFile.ACC_PUBLIC;
import static java.lang.classfile.ClassFile.ACC_STATIC;
import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CLASS_INIT_NAME;
import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodHandles.Lookup.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class DefineClassTest {
    private static final String THIS_PACKAGE = DefineClassTest.class.getPackageName();
    private static final ClassDesc CD_Runnable = Runnable.class.describeConstable().orElseThrow();
    private static final ClassDesc CD_MissingSuperClass = ClassDesc.of("MissingSuperClass");

    /**
     * Test that a class has the same class loader, and is in the same package and
     * protection domain, as a lookup class.
     */
    void testSameAbode(Class<?> clazz, Class<?> lc) {
        assertSame(lc.getClassLoader(), clazz.getClassLoader());
        assertEquals(lc.getPackageName(), clazz.getPackageName());
        assertSame(lc.getProtectionDomain(), clazz.getProtectionDomain());
    }

    /**
     * Tests that a class is discoverable by name using Class.forName and
     * lookup.findClass
     */
    void testDiscoverable(Class<?> clazz, Lookup lookup) throws Exception {
        String cn = clazz.getName();
        ClassLoader loader = clazz.getClassLoader();
        assertSame(clazz, Class.forName(cn, false, loader));
        assertSame(clazz, lookup.findClass(cn));
    }

    /**
     * Basic test of defineClass to define a class in the same package as test.
     */
    @Test
    public void testDefineClass() throws Exception {
        final String CLASS_NAME = THIS_PACKAGE + ".Foo";
        Lookup lookup = lookup();
        Class<?> clazz = lookup.defineClass(generateClass(CLASS_NAME));

        // test name
        assertEquals(CLASS_NAME, clazz.getName());

        // test loader/package/protection-domain
        testSameAbode(clazz, lookup.lookupClass());

        // test discoverable
        testDiscoverable(clazz, lookup);

        // attempt defineClass again
        var bytes = generateClass(CLASS_NAME);
        assertThrows(LinkageError.class, () -> lookup.defineClass(bytes));
    }

    /**
     * Test public/package/protected/private access from class defined with defineClass.
     */
    @Test
    public void testAccess() throws Exception {
        final String THIS_CLASS = this.getClass().getName();
        final String CLASS_NAME = THIS_PACKAGE + ".Runner";
        Lookup lookup = lookup();

        // public
        byte[] classBytes = generateRunner(CLASS_NAME + nextNumber(), THIS_CLASS, "method1");
        testInvoke(lookup.defineClass(classBytes));

        // package
        classBytes = generateRunner(CLASS_NAME + nextNumber(), THIS_CLASS, "method2");
        testInvoke(lookup.defineClass(classBytes));

        // protected (same package)
        classBytes = generateRunner(CLASS_NAME + nextNumber(), THIS_CLASS, "method3");
        testInvoke(lookup.defineClass(classBytes));

        // private
        classBytes = generateRunner(CLASS_NAME + nextNumber(), THIS_CLASS, "method4");
        Class<?> clazz = lookup.defineClass(classBytes);
        Runnable r = (Runnable) clazz.newInstance();
        assertThrows(IllegalAccessError.class, r::run);
    }

    public static void method1() { }
    static void method2() { }
    protected static void method3() { }
    private static void method4() { }

    void testInvoke(Class<?> clazz) throws Exception {
        Object obj = clazz.newInstance();
        ((Runnable) obj).run();
    }

    /**
     * Test that defineClass does not run the class initializer
     */
    @Test
    public void testInitializerNotRun() throws Exception {
        final String THIS_CLASS = this.getClass().getName();
        final String CLASS_NAME = THIS_PACKAGE + ".ClassWithClinit";

        byte[] classBytes = generateClassWithInitializer(CLASS_NAME, THIS_CLASS, "fail");
        Class<?> clazz = lookup().defineClass(classBytes);

        // trigger initializer to run
        var e = assertThrows(ExceptionInInitializerError.class, clazz::newInstance);
        assertInstanceOf(IllegalCallerException.class, e.getCause());
    }

    static void fail() { throw new IllegalCallerException(); }


    /**
     * Test defineClass to define classes in a package containing classes with
     * different protection domains.
     */
    @Test
    public void testTwoProtectionDomains() throws Exception {
        Path here = Paths.get("");

        // p.C1 in one exploded directory
        Path dir1 = Files.createTempDirectory(here, "classes");
        Path p = Files.createDirectory(dir1.resolve("p"));
        Files.write(p.resolve("C1.class"), generateClass("p.C1"));
        URL url1 = dir1.toUri().toURL();

        // p.C2 in another exploded directory
        Path dir2 = Files.createTempDirectory(here, "classes");
        p = Files.createDirectory(dir2.resolve("p"));
        Files.write(p.resolve("C2.class"), generateClass("p.C2"));
        URL url2 = dir2.toUri().toURL();

        // load p.C1 and p.C2
        ClassLoader loader = new URLClassLoader(new URL[] { url1, url2 });
        Class<?> target1 = Class.forName("p.C1", false, loader);
        Class<?> target2 = Class.forName("p.C2", false, loader);
        assertSame(loader, target1.getClassLoader());
        assertSame(loader, target1.getClassLoader());
        assertNotEquals(target2.getProtectionDomain(), target1.getProtectionDomain());

        // protection domain 1
        Lookup lookup1 = privateLookupIn(target1, lookup());

        Class<?> clazz = lookup1.defineClass(generateClass("p.Foo"));
        testSameAbode(clazz, lookup1.lookupClass());
        testDiscoverable(clazz, lookup1);

        // protection domain 2
        Lookup lookup2 = privateLookupIn(target2, lookup());

        clazz = lookup2.defineClass(generateClass("p.Bar"));
        testSameAbode(clazz, lookup2.lookupClass());
        testDiscoverable(clazz, lookup2);
    }

    /**
     * Test defineClass defining a class to the boot loader
     */
    @Test
    public void testBootLoader() throws Exception {
        Lookup lookup = privateLookupIn(Thread.class, lookup());
        assertNull(lookup.getClass().getClassLoader());

        Class<?> clazz = lookup.defineClass(generateClass("java.lang.Foo"));
        assertEquals("java.lang.Foo", clazz.getName());
        testSameAbode(clazz, Thread.class);
        testDiscoverable(clazz, lookup);
    }

    @Test
    public void testWrongPackage() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> lookup().defineClass(generateClass("other.C")));
    }

    @Test
    public void testNoPackageAccess() throws Exception {
        Lookup lookup = lookup().dropLookupMode(PACKAGE);
        assertThrows(IllegalAccessException.class, () -> lookup.defineClass(generateClass(THIS_PACKAGE + ".C")));
    }

    @Test
    public void testTruncatedClassFile() throws Exception {
        assertThrows(ClassFormatError.class, () -> lookup().defineClass(new byte[0]));
    }

    @Test
    public void testNull() throws Exception {
        assertThrows(NullPointerException.class, () -> lookup().defineClass(null));
    }

    @Test
    public void testLinking() throws Exception {
        assertThrows(NoClassDefFoundError.class, () -> lookup().defineClass(generateNonLinkableClass(THIS_PACKAGE + ".NonLinkableClass")));
    }

    @Test
    public void testModuleInfo() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> lookup().defineClass(generateModuleInfo()));
    }

    /**
     * Generates a class file with the given class name
     */
    byte[] generateClass(String className) {
        return ClassFile.of().build(ClassDesc.of(className), clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withSuperclass(CD_Object);
            clb.withMethodBody(INIT_NAME, MTD_void, PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
        });
    }

    /**
     * Generate a class file with the given class name. The class implements Runnable
     * with a run method to invokestatic the given targetClass/targetMethod.
     */
    byte[] generateRunner(String className,
                          String targetClass,
                          String targetMethod) throws Exception {

        return ClassFile.of().build(ClassDesc.of(className), clb -> {
            clb.withSuperclass(CD_Object);
            clb.withInterfaceSymbols(CD_Runnable);
            clb.withMethodBody(INIT_NAME, MTD_void, PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody("run", MTD_void, PUBLIC, cob -> {
                cob.invokestatic(ClassDesc.of(targetClass), targetMethod, MTD_void);
                cob.return_();
            });
        });
    }

    /**
     * Generate a class file with the given class name. The class will initializer
     * to invokestatic the given targetClass/targetMethod.
     */
    byte[] generateClassWithInitializer(String className,
                                        String targetClass,
                                        String targetMethod) throws Exception {

        return ClassFile.of().build(ClassDesc.of(className), clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withSuperclass(CD_Object);
            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_Object, INIT_NAME, MTD_void);
                cob.return_();
            });
            clb.withMethodBody(CLASS_INIT_NAME, MTD_void, ACC_STATIC, cob -> {
                cob.invokestatic(ClassDesc.of(targetClass), targetMethod, MTD_void);
                cob.return_();
            });
        });
    }

    /**
     * Generates a non-linkable class file with the given class name
     */
    byte[] generateNonLinkableClass(String className) {
        return ClassFile.of().build(ClassDesc.of(className), clb -> {
            clb.withFlags(AccessFlag.PUBLIC, AccessFlag.SUPER);
            clb.withSuperclass(CD_MissingSuperClass);
            clb.withMethodBody(INIT_NAME, MTD_void, ACC_PUBLIC, cob -> {
                cob.aload(0);
                cob.invokespecial(CD_MissingSuperClass, INIT_NAME, MTD_void);
                cob.return_();
            });
        });
    }

    /**
     * Generates a class file with the given class name
     */
    byte[] generateModuleInfo() {
        return ClassFile.of().build(ClassDesc.of("module-info"), cb -> cb.withFlags(AccessFlag.MODULE));
    }

    private int nextNumber() {
        return ++nextNumber;
    }

    private int nextNumber;
}
