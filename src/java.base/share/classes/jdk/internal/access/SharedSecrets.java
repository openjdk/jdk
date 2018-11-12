/*
 * Copyright (c) 2002, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.access;

import javax.crypto.SealedObject;
import java.io.ObjectInputFilter;
import java.lang.module.ModuleDescriptor;
import java.util.ResourceBundle;
import java.util.jar.JarFile;
import java.io.Console;
import java.io.FileDescriptor;
import java.io.FilePermission;
import java.io.ObjectInputStream;
import java.io.RandomAccessFile;
import java.security.ProtectionDomain;
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
    private static JavaLangInvokeAccess javaLangInvokeAccess;
    private static JavaLangRefAccess javaLangRefAccess;
    private static JavaIOAccess javaIOAccess;
    private static JavaNetInetAddressAccess javaNetInetAddressAccess;
    private static JavaNetHttpCookieAccess javaNetHttpCookieAccess;
    private static JavaNetSocketAccess javaNetSocketAccess;
    private static JavaNetUriAccess javaNetUriAccess;
    private static JavaNetURLAccess javaNetURLAccess;
    private static JavaNetURLClassLoaderAccess javaNetURLClassLoaderAccess;
    private static JavaNioAccess javaNioAccess;
    private static JavaIOFileDescriptorAccess javaIOFileDescriptorAccess;
    private static JavaIOFilePermissionAccess javaIOFilePermissionAccess;
    private static JavaSecurityAccess javaSecurityAccess;
    private static JavaUtilZipFileAccess javaUtilZipFileAccess;
    private static JavaUtilResourceBundleAccess javaUtilResourceBundleAccess;
    private static JavaAWTAccess javaAWTAccess;
    private static JavaAWTFontAccess javaAWTFontAccess;
    private static JavaBeansAccess javaBeansAccess;
    private static JavaObjectInputStreamAccess javaObjectInputStreamAccess;
    private static JavaObjectInputFilterAccess javaObjectInputFilterAccess;
    private static JavaIORandomAccessFileAccess javaIORandomAccessFileAccess;
    private static JavaxCryptoSealedObjectAccess javaxCryptoSealedObjectAccess;

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
                Class<?> c = Class.forName("java.lang.invoke.MethodHandleImpl");
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

    public static void setJavaLangRefAccess(JavaLangRefAccess jlra) {
        javaLangRefAccess = jlra;
    }

    public static JavaLangRefAccess getJavaLangRefAccess() {
        return javaLangRefAccess;
    }

    public static void setJavaNetUriAccess(JavaNetUriAccess jnua) {
        javaNetUriAccess = jnua;
    }

    public static JavaNetUriAccess getJavaNetUriAccess() {
        if (javaNetUriAccess == null)
            unsafe.ensureClassInitialized(java.net.URI.class);
        return javaNetUriAccess;
    }

    public static void setJavaNetURLAccess(JavaNetURLAccess jnua) {
        javaNetURLAccess = jnua;
    }

    public static JavaNetURLAccess getJavaNetURLAccess() {
        if (javaNetURLAccess == null)
            unsafe.ensureClassInitialized(java.net.URL.class);
        return javaNetURLAccess;
    }

    public static void setJavaNetURLClassLoaderAccess(JavaNetURLClassLoaderAccess jnua) {
        javaNetURLClassLoaderAccess = jnua;
    }

    public static JavaNetURLClassLoaderAccess getJavaNetURLClassLoaderAccess() {
        if (javaNetURLClassLoaderAccess == null)
            unsafe.ensureClassInitialized(java.net.URLClassLoader.class);
        return javaNetURLClassLoaderAccess;
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

    public static void setJavaNetSocketAccess(JavaNetSocketAccess jnsa) {
        javaNetSocketAccess = jnsa;
    }

    public static JavaNetSocketAccess getJavaNetSocketAccess() {
        if (javaNetSocketAccess == null)
            unsafe.ensureClassInitialized(java.net.ServerSocket.class);
        return javaNetSocketAccess;
    }

    public static void setJavaNioAccess(JavaNioAccess jna) {
        javaNioAccess = jna;
    }

    public static JavaNioAccess getJavaNioAccess() {
        if (javaNioAccess == null) {
            // Ensure java.nio.Buffer is initialized, which provides the
            // shared secret.
            unsafe.ensureClassInitialized(java.nio.Buffer.class);
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

    public static JavaIOFilePermissionAccess getJavaIOFilePermissionAccess() {
        if (javaIOFilePermissionAccess == null)
            unsafe.ensureClassInitialized(FilePermission.class);

        return javaIOFilePermissionAccess;
    }

    public static void setJavaIOFilePermissionAccess(JavaIOFilePermissionAccess jiofpa) {
        javaIOFilePermissionAccess = jiofpa;
    }

    public static JavaIOFileDescriptorAccess getJavaIOFileDescriptorAccess() {
        if (javaIOFileDescriptorAccess == null)
            unsafe.ensureClassInitialized(FileDescriptor.class);

        return javaIOFileDescriptorAccess;
    }

    public static void setJavaSecurityAccess(JavaSecurityAccess jsa) {
        javaSecurityAccess = jsa;
    }

    public static JavaSecurityAccess getJavaSecurityAccess() {
        if (javaSecurityAccess == null) {
            unsafe.ensureClassInitialized(ProtectionDomain.class);
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
        if (javaUtilResourceBundleAccess == null)
            unsafe.ensureClassInitialized(ResourceBundle.class);
        return javaUtilResourceBundleAccess;
    }

    public static void setJavaUtilResourceBundleAccess(JavaUtilResourceBundleAccess access) {
        javaUtilResourceBundleAccess = access;
    }

    public static JavaObjectInputStreamAccess getJavaObjectInputStreamAccess() {
        if (javaObjectInputStreamAccess == null) {
            unsafe.ensureClassInitialized(ObjectInputStream.class);
        }
        return javaObjectInputStreamAccess;
    }

    public static void setJavaObjectInputStreamAccess(JavaObjectInputStreamAccess access) {
        javaObjectInputStreamAccess = access;
    }

    public static JavaObjectInputFilterAccess getJavaObjectInputFilterAccess() {
        if (javaObjectInputFilterAccess == null) {
            unsafe.ensureClassInitialized(ObjectInputFilter.Config.class);
        }
        return javaObjectInputFilterAccess;
    }

    public static void setJavaObjectInputFilterAccess(JavaObjectInputFilterAccess access) {
        javaObjectInputFilterAccess = access;
    }

    public static void setJavaIORandomAccessFileAccess(JavaIORandomAccessFileAccess jirafa) {
        javaIORandomAccessFileAccess = jirafa;
    }

    public static JavaIORandomAccessFileAccess getJavaIORandomAccessFileAccess() {
        if (javaIORandomAccessFileAccess == null) {
            unsafe.ensureClassInitialized(RandomAccessFile.class);
        }
        return javaIORandomAccessFileAccess;
    }

    public static void setJavaxCryptoSealedObjectAccess(JavaxCryptoSealedObjectAccess jcsoa) {
        javaxCryptoSealedObjectAccess = jcsoa;
    }

    public static JavaxCryptoSealedObjectAccess getJavaxCryptoSealedObjectAccess() {
        if (javaxCryptoSealedObjectAccess == null) {
            unsafe.ensureClassInitialized(SealedObject.class);
        }
        return javaxCryptoSealedObjectAccess;
    }
}
