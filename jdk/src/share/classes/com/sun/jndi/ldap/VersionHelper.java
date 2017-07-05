/*
 * Copyright (c) 1999, Oracle and/or its affiliates. All rights reserved.
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

abstract class VersionHelper {

    private static VersionHelper helper = null;

    VersionHelper() {} // Disallow anyone from creating one of these.

    static {
        try {
            Class.forName("java.net.URLClassLoader"); // 1.2 test
            Class.forName("java.security.PrivilegedAction"); // 1.2 test
            helper = (VersionHelper)
                Class.forName(
                    "com.sun.jndi.ldap.VersionHelper12").newInstance();
        } catch (Exception e) {
        }

        // Use 1.1 helper if 1.2 test fails, or if we cannot create 1.2 helper
        if (helper == null) {
            try {
                helper = (VersionHelper)
                    Class.forName(
                        "com.sun.jndi.ldap.VersionHelper11").newInstance();
            } catch (Exception e) {
                // should never happen
            }
        }
    }

    static VersionHelper getVersionHelper() {
        return helper;
    }

    abstract ClassLoader getURLClassLoader(String[] url)
        throws MalformedURLException;


    static protected URL[] getUrlArray(String[] url) throws MalformedURLException {
        URL[] urlArray = new URL[url.length];
        for (int i = 0; i < urlArray.length; i++) {
            urlArray[i] = new URL(url[i]);
        }
        return urlArray;
    }

    abstract Class loadClass(String className) throws ClassNotFoundException;

    abstract Thread createThread(Runnable r);
}
