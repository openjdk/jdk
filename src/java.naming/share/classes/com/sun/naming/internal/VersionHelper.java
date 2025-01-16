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

package com.sun.naming.internal;

import javax.naming.NamingEnumeration;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

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

public final class VersionHelper {
    private static final VersionHelper helper = new VersionHelper();

    static final String[] PROPS = new String[]{
        javax.naming.Context.INITIAL_CONTEXT_FACTORY,
        javax.naming.Context.OBJECT_FACTORIES,
        javax.naming.Context.URL_PKG_PREFIXES,
        javax.naming.Context.STATE_FACTORIES,
        javax.naming.Context.PROVIDER_URL,
        javax.naming.Context.DNS_URL,
        // The following shouldn't create a runtime dependence on ldap package.
        javax.naming.ldap.LdapContext.CONTROL_FACTORIES
    };

    public static final int INITIAL_CONTEXT_FACTORY = 0;
    public static final int OBJECT_FACTORIES = 1;
    public static final int URL_PKG_PREFIXES = 2;
    public static final int STATE_FACTORIES = 3;
    public static final int PROVIDER_URL = 4;
    public static final int DNS_URL = 5;
    public static final int CONTROL_FACTORIES = 6;

    private VersionHelper() {} // Disallow anyone from creating one of these.

    public static VersionHelper getVersionHelper() {
        return helper;
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, getContextClassLoader());
    }

    public Class<?> loadClassWithoutInit(String className) throws ClassNotFoundException {
        return loadClass(className, false, getContextClassLoader());
    }

    /**
     * Package private.
     * <p>
     * This internal method is used with Thread Context Class Loader (TCCL),
     * please don't expose this method as public.
     */
    Class<?> loadClass(String className, boolean initialize, ClassLoader cl)
            throws ClassNotFoundException {
        Class<?> cls = Class.forName(className, initialize, cl);
        return cls;
    }

    Class<?> loadClass(String className, ClassLoader cl)
            throws ClassNotFoundException {
        return loadClass(className, true, cl);
    }

    /*
     * Returns a JNDI property from the system properties. Returns
     * null if the property is not set.
     */
    String getJndiProperty(int i) {
        return System.getProperty(PROPS[i]);
    }

    /*
     * Reads each property in PROPS from the system properties, and
     * returns their values -- in order -- in an array.  For each
     * unset property, the corresponding array element is set to null.
     */
    String[] getJndiProperties() {
        Properties sysProps = System.getProperties();
        if (sysProps == null) {
            return null;
        }
        String[] jProps = new String[PROPS.length];
        for (int i = 0; i < PROPS.length; i++) {
            jProps[i] = sysProps.getProperty(PROPS[i]);
        }
        return jProps;
    }

    private static String resolveName(Class<?> c, String name) {
        if (name == null) {
            return name;
        }
        if (!name.startsWith("/")) {
            if (!c.isPrimitive()) {
                String baseName = c.getPackageName();
                if (!baseName.isEmpty()) {
                    name = baseName.replace('.', '/') + "/"
                            + name;
                }
            }
        } else {
            name = name.substring(1);
        }
        return name;
    }

    /*
     * Returns the resource of a given name associated with a particular
     * class (never null), or null if none can be found.
     */
    InputStream getResourceAsStream(Class<?> c, String name) {
        try {
            return c.getModule().getResourceAsStream(resolveName(c, name));
        } catch (IOException x) {
            return null;
        }
    }

    /*
     * Returns an input stream for a file in <java.home>/conf,
     * or null if it cannot be located or opened.
     *
     * @param filename  The file name, sans directory.
     */
    InputStream getJavaHomeConfStream(String filename) {
        try {
            String javahome = System.getProperty("java.home");
            if (javahome == null) {
                return null;
            }
            return Files.newInputStream(Path.of(javahome, "conf", filename));
        } catch (Exception e) {
            return null;
        }
    }

    /*
     * Returns an enumeration (never null) of InputStreams of the
     * resources of a given name associated with a particular class
     * loader.  Null represents the bootstrap class loader in some
     * Java implementations.
     */
    NamingEnumeration<InputStream> getResources(ClassLoader cl,
                                                String name) throws IOException {
        Enumeration<URL> urls;
        urls = (cl == null)
                ? ClassLoader.getSystemResources(name)
                : cl.getResources(name);
        return new InputStreamEnumeration(urls);
    }


    /**
     * Package private.
     * <p>
     * This internal method returns Thread Context Class Loader (TCCL),
     * if null, returns the system Class Loader.
     * <p>
     * Please don't expose this method as public.
     * @throws SecurityException if the class loader is not accessible
     */
    ClassLoader getContextClassLoader() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            // Don't use bootstrap class loader directly!
            loader = ClassLoader.getSystemClassLoader();
        }
        return loader;
    }

    /**
     * Given an enumeration of URLs, an instance of this class represents
     * an enumeration of their InputStreams.
     */
    private class InputStreamEnumeration implements
            NamingEnumeration<InputStream> {

        private final Enumeration<URL> urls;

        private InputStream nextElement;

        InputStreamEnumeration(Enumeration<URL> urls) {
            this.urls = urls;
        }

        /*
         * Returns the next InputStream, or null if there are no more.
         * An InputStream that cannot be opened is skipped.
         */
        private InputStream getNextElement() {
            while (urls.hasMoreElements()) {
                try {
                    return urls.nextElement().openStream();
                } catch (IOException e) {
                    // skip this URL
                }
            }
            return null;
        }

        public boolean hasMore() {
            if (nextElement != null) {
                return true;
            }
            nextElement = getNextElement();
            return (nextElement != null);
        }

        public boolean hasMoreElements() {
            return hasMore();
        }

        public InputStream next() {
            if (hasMore()) {
                InputStream res = nextElement;
                nextElement = null;
                return res;
            } else {
                throw new NoSuchElementException();
            }
        }

        public InputStream nextElement() {
            return next();
        }

        public void close() {
        }
    }
}
