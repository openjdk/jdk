/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.channels.SocketChannel;

/**
 * Launched by IllegalAccessTest to attempt illegal access.
 */

public class TryAccess {

    public static void main(String[] args) throws Exception {
        String[] methodNames = args[0].split(",");
        for (String methodName : methodNames) {
            Method m = TryAccess.class.getDeclaredMethod(methodName);
            m.invoke(null);
        }
    }

    // -- static access --

    static void accessPublicClassNonExportedPackage() throws Exception {
        Object obj = new sun.security.x509.X500Name("CN=name");
    }

    static void accessPublicClassJdk9NonExportedPackage() {
        Object obj = jdk.internal.misc.Unsafe.getUnsafe();
    }

    // -- reflective access --

    static void reflectPublicMemberExportedPackage() throws Exception {
        Constructor<?> ctor = String.class.getConstructor(String.class);
        Object name = ctor.newInstance("value");
    }

    static void reflectNonPublicMemberExportedPackage() throws Exception {
        Field f = String.class.getDeclaredField("value");
        Object obj = f.get("foo");
    }

    static void reflectPublicMemberNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Constructor<?> ctor = clazz.getConstructor(String.class);
        Object obj = ctor.newInstance("CN=user");
    }

    static void reflectNonPublicMemberNonExportedPackage() throws Exception {
        SocketChannel sc = SocketChannel.open();
        Field f = sc.getClass().getDeclaredField("fd");
        Object obj = f.get(sc);
    }

    static void reflectPublicMemberJdk9NonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("jdk.internal.misc.Unsafe");
        Method m = clazz.getMethod("getUnsafe");
        Object obj = m.invoke(null);
    }

    static void reflectPublicMemberApplicationModule() throws Exception {
        Class<?> clazz = Class.forName("p.Type");
        Constructor<?> ctor = clazz.getConstructor(int.class);
        Object obj = ctor.newInstance(1);
    }

    // -- setAccessible --

    static void setAccessiblePublicMemberExportedPackage() throws Exception {
        Constructor<?> ctor = String.class.getConstructor(String.class);
        ctor.setAccessible(true);
    }

    static void setAccessibleNonPublicMemberExportedPackage() throws Exception {
        Method method = ClassLoader.class.getDeclaredMethod("defineClass",
                byte[].class, int.class, int.class);
        method.setAccessible(true);
    }

    static void setAccessiblePublicMemberNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        Constructor<?> ctor = clazz.getConstructor(String.class);
        ctor.setAccessible(true);
    }

    static void setAccessibleNonPublicMemberNonExportedPackage() throws Exception {
        SocketChannel sc = SocketChannel.open();
        Field f = sc.getClass().getDeclaredField("fd");
        f.setAccessible(true);
    }

    static void setAccessiblePublicMemberJdk9NonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("jdk.internal.misc.Unsafe");
        Method m = clazz.getMethod("getUnsafe");
        m.setAccessible(true);
    }

    static void setAccessiblePublicMemberApplicationModule() throws Exception {
        Class<?> clazz = Class.forName("p.Type");
        Constructor<?> ctor = clazz.getConstructor(int.class);
        ctor.setAccessible(true);
    }


    static void setAccessibleNotPublicMemberApplicationModule() throws Exception {
        Class<?> clazz = Class.forName("p.Type");
        Constructor<?> ctor = clazz.getDeclaredConstructor(int.class, int.class);
        ctor.setAccessible(true);
    }


    // -- privateLookupIn --

    static void privateLookupPublicClassExportedPackage() throws Exception {
        MethodHandles.privateLookupIn(String.class, MethodHandles.lookup());
    }

    static void privateLookupNonPublicClassExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("java.lang.WeakPairMap");
        MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    }

    static void privateLookupPublicClassNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.security.x509.X500Name");
        MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    }

    static void privateLookupNonPublicClassNonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("sun.nio.ch.SocketChannelImpl");
        MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    }

    static void privateLookupPublicClassJdk9NonExportedPackage() throws Exception {
        Class<?> clazz = Class.forName("jdk.internal.misc.Unsafe");
        MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    }

    static void privateLookupPublicClassApplicationModule() throws Exception {
        Class<?> clazz = Class.forName("p.Type");
        MethodHandles.privateLookupIn(clazz, MethodHandles.lookup());
    }


    // -- export/open packages to this unnamed module --

    static void exportNonExportedPackages() throws Exception {
        Class<?> helper = Class.forName("java.lang.Helper");
        Method m = helper.getMethod("export", String.class, Module.class);
        m.invoke(null, "sun.security.x509", TryAccess.class.getModule());
        m.invoke(null, "sun.nio.ch", TryAccess.class.getModule());
    }

    static void openExportedPackage() throws Exception {
        Class<?> helper = Class.forName("java.lang.Helper");
        Method m = helper.getMethod("open", String.class, Module.class);
        m.invoke(null, "java.lang", TryAccess.class.getModule());
    }

    static void openNonExportedPackages() throws Exception {
        Class<?> helper = Class.forName("java.lang.Helper");
        Method m = helper.getMethod("open", String.class, Module.class);
        m.invoke(null, "sun.security.x509", TryAccess.class.getModule());
        m.invoke(null, "sun.nio.ch", TryAccess.class.getModule());
    }
}
