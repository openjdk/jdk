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

/**
 * @test
 * @modules java.base/jdk.internal.misc
 * @library /test/lib ..
 * @compile p2/c2.java
 * @compile p4/c4.java
 * @build sun.hotspot.WhiteBox
 * @compile/module=java.base java/lang/ModuleHelper.java
 * @run main ClassFileInstaller sun.hotspot.WhiteBox
 *                              sun.hotspot.WhiteBox$WhiteBoxPermission
 * @run main/othervm -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+WhiteBoxAPI CCE_module_msg
 */

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;
import static jdk.test.lib.Asserts.*;

// Test that the message in a runtime ClassCastException contains module info.
public class CCE_module_msg {
    private static final Path CLASSES_DIR = Paths.get("classes");

    public static void main(String[] args) throws Throwable {
        // Should not display version
        invalidObjectToDerived();
        // Should display version
        invalidClassToString();
        // Should display customer class loader
        invalidClassToStringCustomLoader();
    }

    public static void invalidObjectToDerived() {
        java.lang.Object instance = new java.lang.Object();
        int left = 23;
        int right = 42;
        try {
            for (int i = 0; i < 1; i += 1) {
                left = ((Derived) instance).method(left, right);
            }
            throw new RuntimeException("ClassCastException wasn't thrown, test failed.");
        } catch (ClassCastException cce) {
            System.out.println(cce.getMessage());
            if (!cce.getMessage().contains("java.base/java.lang.Object cannot be cast to Derived")) {
                throw new RuntimeException("Wrong message: " + cce.getMessage());
            }
        }
    }

    public static void invalidClassToString() throws Throwable {
        // Get the java.lang.Module object for module java.base.
        Class jlObject = Class.forName("java.lang.Object");
        Object jlObject_jlM = jlObject.getModule();
        assertNotNull(jlObject_jlM, "jlModule object of java.lang.Object should not be null");

        // Get the class loader for CCE_module_msg and assume it's also used to
        // load classes p1.c1 and p2.c2.
        ClassLoader this_cldr = CCE_module_msg.class.getClassLoader();

        // Define a module for p2.
        Object m2x = ModuleHelper.ModuleObject("module_two", this_cldr, new String[] { "p2" });
        assertNotNull(m2x, "Module should not be null");
        ModuleHelper.DefineModule(m2x, "9.0", "m2x/there", new String[] { "p2" });
        ModuleHelper.AddReadsModule(m2x, jlObject_jlM);

        try {
            ModuleHelper.AddModuleExportsToAll(m2x, "p2");
            Object p2Obj = new p2.c2();
            System.out.println((String)p2Obj);
            throw new RuntimeException("ClassCastException wasn't thrown, test failed.");
        } catch (ClassCastException cce) {
            String exception = cce.getMessage();
            System.out.println(exception);
            if (exception.contains("module_two/p2.c2") ||
                !(exception.contains("module_two@") &&
                  exception.contains("/p2.c2 cannot be cast to java.base/java.lang.String"))) {
                throw new RuntimeException("Wrong message: " + exception);
            }
        }
    }

    public static void invalidClassToStringCustomLoader() throws Throwable {
        // Get the java.lang.Module object for module java.base.
        Class jlObject = Class.forName("java.lang.Object");
        Object jlObject_jlM = jlObject.getModule();
        assertNotNull(jlObject_jlM, "jlModule object of java.lang.Object should not be null");

        // Create a customer class loader to load class p4/c4.
        URL[] urls = new URL[] { CLASSES_DIR.toUri().toURL() };
        ClassLoader parent = ClassLoader.getSystemClassLoader();
        MyURLClassLoader myCldr = new MyURLClassLoader("MyClassLoader", urls, parent);

        try {
            // Class p4.c4 should be defined to the unnamed module of myCldr
            Class p4_c4_class = myCldr.loadClass("p4.c4");
            Object c4Obj = p4_c4_class.newInstance();
            System.out.println((String)c4Obj);
            throw new RuntimeException("ClassCastException wasn't thrown, test failed.");
        } catch (ClassCastException cce) {
            String exception = cce.getMessage();
            System.out.println(exception);
            if (!exception.contains("MyClassLoader//p4.c4 cannot be cast to java.base/java.lang.String")) {
                throw new RuntimeException("Wrong message: " + exception);
            }
        }
    }
}

class Derived extends java.lang.Object {
    public int method(int left, int right) {
        return right;
    }
}

class MyURLClassLoader extends URLClassLoader {
    public MyURLClassLoader(String name,
                          URL[] urls,
                          ClassLoader parent) {
        super(name, urls, parent);
    }

    public Class loadClass(String name) throws ClassNotFoundException {
        if (!name.equals("p4.c4")) {
            return super.loadClass(name);
        }
        byte[] data = getClassData(name);
        return defineClass(name, data, 0, data.length);
    }

    byte[] getClassData(String name) {
        try {
           String TempName = name.replaceAll("\\.", "/");
           String currentDir = System.getProperty("test.classes");
           String filename = currentDir + File.separator + TempName + ".class";
           FileInputStream fis = new FileInputStream(filename);
           byte[] b = new byte[5000];
           int cnt = fis.read(b, 0, 5000);
           byte[] c = new byte[cnt];
           for (int i=0; i<cnt; i++) c[i] = b[i];
              return c;
        } catch (IOException e) {
           return null;
        }
    }
}
