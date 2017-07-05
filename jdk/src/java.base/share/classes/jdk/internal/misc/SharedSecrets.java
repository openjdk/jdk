/*
 * Copyright (c) 2002, 2015, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.misc;

import java.lang.module.ModuleDescriptor;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.security.ProtectionDomain;
import java.security.AccessController;
import jdk.internal.misc.Unsafe;

/** A repository of "shared secrets", which are a mechanism for
    calling implementation-private methods in another package without
    using reflection. A package-private class implements a public
    interface and provides the ability to call package-private methods
    within that package; the object implementing that interface is
    provided through a third package to which access is restricted.
    This framework avoids the primary disadvantage of using reflection
    for this purpose, namely the loss of compile-time checking. */

public class SharedSecrets {
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static JavaUtilJarAccess javaUtilJarAccess;
    private static JavaLangAccess javaLangAccess;
    private static JavaLangModuleAccess javaLangModuleAccess;
    private static JavaLangReflectModuleAccess javaLangReflectModuleAccess;
    private static JavaLangInvokeAccess javaLangInvokeAccess;
    private static JavaLangRefAccess javaLangRefAccess;
    private static JavaIOAccess javaIOAccess;
    private static JavaNetAccess javaNetAccess;
    private static JavaNetInetAddressAccess javaNetInetAddressAccess;
    private static JavaNetHttpCookieAccess javaNetHttpCookieAccess;
    private static JavaNioAccess javaNioAccess;
    private static JavaIOFileDescriptorAccess javaIOFileDescriptorAccess;
    private static JavaSecurityProtectionDomainAccess javaSecurityProtectionDomainAccess;
    private static JavaSecurityAccess javaSecurityAccess;
    private static JavaUtilZipFileAccess javaUtilZipFileAccess;
    private static JavaUtilResourceBundleAccess javaUtilResourceBundleAccess;
    private static JavaAWTAccess javaAWTAccess;
    private static JavaAWTFontAccess javaAWTFontAccess;
    private static JavaBeansAccess javaBeansAccess;

    public static JavaUtilJarAccess javaUtilJarAccess() {
        if (javaUtilJarAccess == null) {
            // Ensure JarFile is initialized; we know that that class
            // provides the shared secret
            unsafe.ensureClassInitialized(JarFile.class);
        }
        return javaUtilJarAccess;
    }

    public static void setJavaUtilJarAccess(JavaUtilJarAccess access) {
        javaUtilJarAccess = access;
    }

    public static void setJavaLangAccess(JavaLangAccess jla) {
        javaLangAccess = jla;
    }

    public static JavaLangAccess getJavaLangAccess() {
        return javaLangAccess;
    }

    public static void setJavaLangInvokeAccess(JavaLangInvokeAccess jlia) {
        javaLangInvokeAccess = jlia;
    }

    public static JavaLangInvokeAccess getJavaLangInvokeAccess() {
        if (javaLangInvokeAccess == null) {
            try {
                Class<?> c = Class.forName("java.lang.invoke.MemberName");
                unsafe.ensureClassInitialized(c);
            } catch (ClassNotFoundException e) {};
        }
        return javaLangInvokeAccess;
    }

    public static void setJavaLangModuleAccess(JavaLangModuleAccess jlrma) {
        javaLangModuleAccess = jlrma;
    }

    public static JavaLangModuleAccess getJavaLangModuleAccess() {
        if (javaLangModuleAccess == null) {
            unsafe.ensureClassInitialized(ModuleDescriptor.class);
        }
        return javaLangModuleAccess;
    }

    public static void setJavaLangReflectModuleAccess(JavaLangReflectModuleAccess jlrma) {
        javaLangReflectModuleAccess = jlrma;
    }

    public static JavaLangReflectModuleAccess getJavaLangReflectModuleAccess() {
        if (javaLangReflectModuleAccess == null)
            unsafe.ensureClassInitialized(java.lang.reflect.Module.class);
        return javaLangReflectModuleAccess;
    }

    public static void setJavaLangRefAccess(JavaLangRefAccess jlra) {
        javaLangRefAccess = jlra;
    }

    public static JavaLangRefAccess getJavaLangRefAccess() {
        return javaLangRefAccess;
    }

    public static void setJavaNetAccess(JavaNetAccess jna) {
        javaNetAccess = jna;
    }

    public static JavaNetAccess getJavaNetAccess() {
        if (javaNetAccess == null)
            unsafe.ensureClassInitialized(java.net.URLClassLoader.class);
        return javaNetAccess;
    }

    public static void setJavaNetInetAddressAccess(JavaNetInetAddressAccess jna) {
        javaNetInetAddressAccess = jna;
    }

    public static JavaNetInetAddressAccess getJavaNetInetAddressAccess() {
        if (javaNetInetAddressAccess == null)
            unsafe.ensureClassInitialized(java.net.InetAddress.class);
        return javaNetInetAddressAccess;
    }

    public static void setJavaNetHttpCookieAccess(JavaNetHttpCookieAccess a) {
        javaNetHttpCookieAccess = a;
    }

    public static JavaNetHttpCookieAccess getJavaNetHttpCookieAccess() {
        if (javaNetHttpCookieAccess == null)
            unsafe.ensureClassInitialized(java.net.HttpCookie.class);
        return javaNetHttpCookieAccess;
    }

    public static void setJavaNioAccess(JavaNioAccess jna) {
        javaNioAccess = jna;
    }

    public static JavaNioAccess getJavaNioAccess() {
        if (javaNioAccess == null) {
            // Ensure java.nio.ByteOrder is initialized; we know that
            // this class initializes java.nio.Bits that provides the
            // shared secret.
            unsafe.ensureClassInitialized(java.nio.ByteOrder.class);
        }
        return javaNioAccess;
    }

    public static void setJavaIOAccess(JavaIOAccess jia) {
        javaIOAccess = jia;
    }

    public static JavaIOAccess getJavaIOAccess() {
        if (javaIOAccess == null) {
            unsafe.ensureClassInitialized(Console.class);
        }
        return javaIOAccess;
    }

    public static void setJavaIOFileDescriptorAccess(JavaIOFileDescriptorAccess jiofda) {
        javaIOFileDescriptorAccess = jiofda;
    }

    public static JavaIOFileDescriptorAccess getJavaIOFileDescriptorAccess() {
        if (javaIOFileDescriptorAccess == null)
            unsafe.ensureClassInitialized(FileDescriptor.class);

        return javaIOFileDescriptorAccess;
    }

    public static void setJavaSecurityProtectionDomainAccess
        (JavaSecurityProtectionDomainAccess jspda) {
            javaSecurityProtectionDomainAccess = jspda;
    }

    public static JavaSecurityProtectionDomainAccess
        getJavaSecurityProtectionDomainAccess() {
            if (javaSecurityProtectionDomainAccess == null)
                unsafe.ensureClassInitialized(ProtectionDomain.class);
            return javaSecurityProtectionDomainAccess;
    }

    public static void setJavaSecurityAccess(JavaSecurityAccess jsa) {
        javaSecurityAccess = jsa;
    }

    public static JavaSecurityAccess getJavaSecurityAccess() {
        if (javaSecurityAccess == null) {
            unsafe.ensureClassInitialized(AccessController.class);
        }
        return javaSecurityAccess;
    }

    public static JavaUtilZipFileAccess getJavaUtilZipFileAccess() {
        if (javaUtilZipFileAccess == null)
            unsafe.ensureClassInitialized(java.util.zip.ZipFile.class);
        return javaUtilZipFileAccess;
    }

    public static void setJavaUtilZipFileAccess(JavaUtilZipFileAccess access) {
        javaUtilZipFileAccess = access;
    }

    public static void setJavaAWTAccess(JavaAWTAccess jaa) {
        javaAWTAccess = jaa;
    }

    public static JavaAWTAccess getJavaAWTAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTAccess;
    }

    public static void setJavaAWTFontAccess(JavaAWTFontAccess jafa) {
        javaAWTFontAccess = jafa;
    }

    public static JavaAWTFontAccess getJavaAWTFontAccess() {
        // this may return null in which case calling code needs to
        // provision for.
        return javaAWTFontAccess;
    }

    public static JavaBeansAccess getJavaBeansAccess() {
        return javaBeansAccess;
    }

    public static void setJavaBeansAccess(JavaBeansAccess access) {
        javaBeansAccess = access;
    }

    public static JavaUtilResourceBundleAccess getJavaUtilResourceBundleAccess() {
        return javaUtilResourceBundleAccess;
    }

    public static void setJavaUtilResourceBundleAccess(JavaUtilResourceBundleAccess access) {
        javaUtilResourceBundleAccess = access;
    }
}
