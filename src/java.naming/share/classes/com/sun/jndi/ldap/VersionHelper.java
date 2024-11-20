/*
 * Copyright (c) 1999, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.jndi.ldap;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;

public final class VersionHelper {

    private static final VersionHelper helper = new VersionHelper();

    /**
     * Determines whether classes may be loaded from an arbitrary URL code base.
     */
    private static final boolean trustURLCodebase;

    /**
     * Determines whether objects may be deserialized or reconstructed from a content of
     * 'javaSerializedData', 'javaRemoteLocation' or 'javaReferenceAddress' LDAP attributes.
     */
    private static final boolean trustSerialData;

    static {
        // System property to control whether classes may be loaded from an
        // arbitrary URL code base
        String trust = getPrivilegedProperty(
                "com.sun.jndi.ldap.object.trustURLCodebase", "false");
        trustURLCodebase = "true".equalsIgnoreCase(trust);

        // System property to control whether classes are allowed to be loaded from
        // 'javaSerializedData', 'javaRemoteLocation' or 'javaReferenceAddress' attributes.
        String trustSerialDataSp = getPrivilegedProperty(
                "com.sun.jndi.ldap.object.trustSerialData", "false");
        trustSerialData = "true".equalsIgnoreCase(trustSerialDataSp);
    }

    @SuppressWarnings("removal")
    private static String getPrivilegedProperty(String propertyName, String defaultVal) {
        PrivilegedAction<String> action = () -> System.getProperty(propertyName, defaultVal);
        if (System.getSecurityManager() == null) {
            return action.run();
        } else {
            return AccessController.doPrivileged(action);
        }
    }

    private VersionHelper() {
    }

    static VersionHelper getVersionHelper() {
        return helper;
    }

    /**
     * Returns true if deserialization or reconstruction of objects from
     * 'javaSerializedData', 'javaRemoteLocation' and 'javaReferenceAddress'
     * LDAP attributes is allowed.
     *
     * @return true if deserialization is allowed; false - otherwise
     */
    public static boolean isSerialDataAllowed() {
        return trustSerialData;
    }

    ClassLoader getURLClassLoader(String[] url) throws MalformedURLException {
        ClassLoader parent = getContextClassLoader();
        /*
         * Classes may only be loaded from an arbitrary URL code base when
         * the system property com.sun.jndi.ldap.object.trustURLCodebase
         * has been set to "true".
         */
        if (url != null && trustURLCodebase) {
            return URLClassLoader.newInstance(getUrlArray(url), parent);
        } else {
            return parent;
        }
    }

    Class<?> loadClass(String className) throws ClassNotFoundException {
        return Class.forName(className, true, getContextClassLoader());
    }

    Thread createThread(Runnable r) {
        return new Thread(r);
    }

    @SuppressWarnings("removal")
    private ClassLoader getContextClassLoader() {
        PrivilegedAction<ClassLoader> act =
                Thread.currentThread()::getContextClassLoader;
        return AccessController.doPrivileged(act);
    }

    @SuppressWarnings("deprecation")
    private static URL[] getUrlArray(String[] url) throws MalformedURLException {
        URL[] urlArray = new URL[url.length];
        for (int i = 0; i < urlArray.length; i++) {
            urlArray[i] = new URL(url[i]);
        }
        return urlArray;
    }
}
