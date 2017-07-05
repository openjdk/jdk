/*
 * Copyright (c) 1999, 2006, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.naming.internal;

import java.io.InputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.StringTokenizer;
import java.util.Vector;

import javax.naming.NamingEnumeration;

/**
 * VersionHelper was used by JNDI to accommodate differences between
 * JDK 1.1.x and the Java 2 platform. As this is no longer necessary
 * since JNDI's inclusion in the platform, this class currently
 * serves as a set of utilities for performing system-level things,
 * such as class-loading and reading system properties.
 *
 * @author Rosanna Lee
 * @author Scott Seligman
 */

public abstract class VersionHelper {
    private static VersionHelper helper = null;

    final static String[] PROPS = new String[] {
        javax.naming.Context.INITIAL_CONTEXT_FACTORY,
        javax.naming.Context.OBJECT_FACTORIES,
        javax.naming.Context.URL_PKG_PREFIXES,
        javax.naming.Context.STATE_FACTORIES,
        javax.naming.Context.PROVIDER_URL,
        javax.naming.Context.DNS_URL,
        // The following shouldn't create a runtime dependence on ldap package.
        javax.naming.ldap.LdapContext.CONTROL_FACTORIES
    };

    public final static int INITIAL_CONTEXT_FACTORY = 0;
    public final static int OBJECT_FACTORIES = 1;
    public final static int URL_PKG_PREFIXES = 2;
    public final static int STATE_FACTORIES = 3;
    public final static int PROVIDER_URL = 4;
    public final static int DNS_URL = 5;
    public final static int CONTROL_FACTORIES = 6;

    VersionHelper() {} // Disallow anyone from creating one of these.

    static {
        helper = new VersionHelper12();
    }

    public static VersionHelper getVersionHelper() {
        return helper;
    }

    public abstract Class loadClass(String className)
        throws ClassNotFoundException;

    abstract Class loadClass(String className, ClassLoader cl)
        throws ClassNotFoundException;

    public abstract Class loadClass(String className, String codebase)
        throws ClassNotFoundException, MalformedURLException;

    /*
     * Returns a JNDI property from the system properties.  Returns
     * null if the property is not set, or if there is no permission
     * to read it.
     */
    abstract String getJndiProperty(int i);

    /*
     * Reads each property in PROPS from the system properties, and
     * returns their values -- in order -- in an array.  For each
     * unset property, the corresponding array element is set to null.
     * Returns null if there is no permission to call System.getProperties().
     */
    abstract String[] getJndiProperties();

    /*
     * Returns the resource of a given name associated with a particular
     * class (never null), or null if none can be found.
     */
    abstract InputStream getResourceAsStream(Class c, String name);

    /*
     * Returns an input stream for a file in <java.home>/lib,
     * or null if it cannot be located or opened.
     *
     * @param filename  The file name, sans directory.
     */
    abstract InputStream getJavaHomeLibStream(String filename);

    /*
     * Returns an enumeration (never null) of InputStreams of the
     * resources of a given name associated with a particular class
     * loader.  Null represents the bootstrap class loader in some
     * Java implementations.
     */
    abstract NamingEnumeration getResources(ClassLoader cl, String name)
        throws IOException;

    /*
     * Returns the context class loader associated with the current thread.
     * Null indicates the bootstrap class loader in some Java implementations.
     *
     * @throws SecurityException if the class loader is not accessible.
     */
    abstract ClassLoader getContextClassLoader();

    static protected URL[] getUrlArray(String codebase)
        throws MalformedURLException {
        // Parse codebase into separate URLs
        StringTokenizer parser = new StringTokenizer(codebase);
        Vector vec = new Vector(10);
        while (parser.hasMoreTokens()) {
            vec.addElement(parser.nextToken());
        }
        String[] url = new String[vec.size()];
        for (int i = 0; i < url.length; i++) {
            url[i] = (String)vec.elementAt(i);
        }

        URL[] urlArray = new URL[url.length];
        for (int i = 0; i < urlArray.length; i++) {
            urlArray[i] = new URL(url[i]);
        }
        return urlArray;
    }
}
