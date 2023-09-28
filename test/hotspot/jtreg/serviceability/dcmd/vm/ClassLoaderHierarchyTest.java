/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2018 SAP SE. All rights reserved.
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
 * @summary Test of diagnostic command VM.classloaders
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          jdk.compiler
 *          jdk.internal.jvmstat/sun.jvmstat.monitor
 * @run testng ClassLoaderHierarchyTest
 */

import org.testng.Assert;
import org.testng.annotations.Test;

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.dcmd.CommandExecutor;
import jdk.test.lib.dcmd.JMXExecutor;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ClassLoaderHierarchyTest {

    class EmptyDelegatingLoader extends ClassLoader {
        EmptyDelegatingLoader(String name, ClassLoader parent) {
            super(name, parent);
        }
    }

    static void loadTestClassInLoaderAndCheck(String classname, ClassLoader loader) throws ClassNotFoundException {
        Class<?> c = Class.forName(classname, true, loader);
        if (c.getClassLoader() != loader) {
            Assert.fail(classname + " defined by wrong classloader: " + c.getClassLoader());
        }
    }

//+-- <bootstrap>
//      |
//      +-- "platform", jdk.internal.loader.ClassLoaders$PlatformClassLoader
//      |     |
//      |     +-- "app", jdk.internal.loader.ClassLoaders$AppClassLoader
//      |
//      +-- jdk.internal.reflect.DelegatingClassLoader
//      |
//      +-- "Kevin", ClassLoaderHierarchyTest$TestClassLoader
//      |
//      +-- ClassLoaderHierarchyTest$TestClassLoader
//            |
//            +-- "Bill", ClassLoaderHierarchyTest$TestClassLoader

    public void run(CommandExecutor executor) throws ClassNotFoundException {

        // A) one unnamed, two named loaders
        ClassLoader unnamed_cl = new TestClassLoader(null, null);
        ClassLoader named_cl = new TestClassLoader("Kevin", null);
        ClassLoader named_child_cl = new TestClassLoader("Bill", unnamed_cl);
        loadTestClassInLoaderAndCheck("TestClass2", unnamed_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_cl);

        // B) A named CL with empty loaders as parents (JDK-8293156)
        EmptyDelegatingLoader emptyLoader1 = new EmptyDelegatingLoader("EmptyLoader1", null);
        EmptyDelegatingLoader emptyLoader2 = new EmptyDelegatingLoader("EmptyLoader2", emptyLoader1);
        ClassLoader named_child_2_cl = new TestClassLoader("Child2", emptyLoader2);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_2_cl);

        // C) Test output for several class loaders, same class, same name, empty parents,
        //    and all these should be folded by default.
        EmptyDelegatingLoader emptyLoader3 = new EmptyDelegatingLoader("EmptyLoader3", null);
        EmptyDelegatingLoader emptyLoader4 = new EmptyDelegatingLoader("EmptyLoader4", emptyLoader3);
        ClassLoader named_child_3_cl = new TestClassLoader("ChildX", emptyLoader4); // Same names
        ClassLoader named_child_4_cl = new TestClassLoader("ChildX", emptyLoader4);
        ClassLoader named_child_5_cl = new TestClassLoader("ChildX", emptyLoader4);
        ClassLoader named_child_6_cl = new TestClassLoader("ChildX", emptyLoader4);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_3_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_4_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_5_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_6_cl);

        // D) Test output for several *unnamed* class loaders, same class, same parents,
        //    and all these should be folded by default too.
        EmptyDelegatingLoader emptyLoader5 = new EmptyDelegatingLoader(null, null);
        EmptyDelegatingLoader emptyLoader6 = new EmptyDelegatingLoader(null, emptyLoader5);
        ClassLoader named_child_7_cl = new TestClassLoader(null, emptyLoader6); // Same names
        ClassLoader named_child_8_cl = new TestClassLoader(null, emptyLoader6);
        ClassLoader named_child_9_cl = new TestClassLoader(null, emptyLoader6);
        ClassLoader named_child_10_cl = new TestClassLoader(null, emptyLoader6);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_7_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_8_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_9_cl);
        loadTestClassInLoaderAndCheck("TestClass2", named_child_10_cl);

        // First test: simple output, no classes displayed
        OutputAnalyzer output = executor.execute("VM.classloaders");
        // (A)
        output.shouldContain("+-- <bootstrap>");
        output.shouldContain("      +-- \"platform\", jdk.internal.loader.ClassLoaders$PlatformClassLoader");
        output.shouldContain("      |     +-- \"app\", jdk.internal.loader.ClassLoaders$AppClassLoader");
        output.shouldContain("      +-- \"Kevin\", ClassLoaderHierarchyTest$TestClassLoader");
        output.shouldContain("      +-- ClassLoaderHierarchyTest$TestClassLoader");
        output.shouldContain("      |     +-- \"Bill\", ClassLoaderHierarchyTest$TestClassLoader");
        // (B)
        output.shouldContain("      +-- \"EmptyLoader1\", ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("      |     +-- \"EmptyLoader2\", ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("      |           +-- \"Child2\", ClassLoaderHierarchyTest$TestClassLoader");
        // (C)
        output.shouldContain("      +-- \"EmptyLoader3\", ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("      |     +-- \"EmptyLoader4\", ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("      |           +-- \"ChildX\", ClassLoaderHierarchyTest$TestClassLoader (+ 3 more)");
        // (D)
        output.shouldContain("      +-- ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("            +-- ClassLoaderHierarchyTest$EmptyDelegatingLoader");
        output.shouldContain("                  +-- ClassLoaderHierarchyTest$TestClassLoader (+ 3 more)");

        // Second test: print with classes.
        output = executor.execute("VM.classloaders show-classes");
        output.shouldContain("<bootstrap>");
        output.shouldContain("java.lang.Object");
        output.shouldContain("java.lang.Enum");
        output.shouldContain("java.lang.NullPointerException");
        output.shouldContain("TestClass2");

        output.shouldContain("Hidden Classes:");
    }

    static class TestClassLoader extends ClassLoader {

        public TestClassLoader() {
            super();
        }

        public TestClassLoader(String name, ClassLoader parent) {
            super(name, parent);
        }

        public static final String CLASS_NAME = "TestClass2";

        static ByteBuffer readClassFile(String name)
        {
            File f = new File(System.getProperty("test.classes", "."),
                              name);
            try (FileInputStream fin = new FileInputStream(f);
                 FileChannel fc = fin.getChannel())
            {
                return fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
            } catch (IOException e) {
                Assert.fail("Can't open file: " + name, e);
            }

            /* Will not reach here as Assert.fail() throws exception */
            return null;
        }

        protected Class<?> loadClass(String name, boolean resolve)
            throws ClassNotFoundException
        {
            Class<?> c;
            if (!CLASS_NAME.equals(name)) {
                c = super.loadClass(name, resolve);
            } else {
                // should not delegate to the system class loader
                c = findClass(name);
                if (resolve) {
                    resolveClass(c);
                }
            }
            return c;
        }

        protected Class<?> findClass(String name)
            throws ClassNotFoundException
        {
            if (!CLASS_NAME.equals(name)) {
                throw new ClassNotFoundException("Unexpected class: " + name);
            }
            return defineClass(name, readClassFile(name + ".class"), null);
        }

    }

    @Test
    public void jmx() throws ClassNotFoundException {
        run(new JMXExecutor());
    }

}

class TestClass2 {
    static {
        Runnable r = () -> System.out.println("Hello");
        r.run();
    }
}

