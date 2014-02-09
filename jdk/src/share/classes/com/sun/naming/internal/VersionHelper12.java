/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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
import java.net.URLClassLoader;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.NoSuchElementException;
import java.util.Properties;

import javax.naming.*;

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

final class VersionHelper12 extends VersionHelper {

    // Disallow external from creating one of these.
    VersionHelper12() {
    }

    public Class<?> loadClass(String className) throws ClassNotFoundException {
        return loadClass(className, getContextClassLoader());
    }

    /**
     * Package private.
     *
     * This internal method is used with Thread Context Class Loader (TCCL),
     * please don't expose this method as public.
     */
    Class<?> loadClass(String className, ClassLoader cl)
        throws ClassNotFoundException {
        Class<?> cls = Class.forName(className, true, cl);
        return cls;
    }

    /**
     * @param className A non-null fully qualified class name.
     * @param codebase A non-null, space-separated list of URL strings.
     */
    public Class<?> loadClass(String className, String codebase)
            throws ClassNotFoundException, MalformedURLException {

        ClassLoader parent = getContextClassLoader();
        ClassLoader cl =
                 URLClassLoader.newInstance(getUrlArray(codebase), parent);

        return loadClass(className, cl);
    }

    String getJndiProperty(final int i) {
        return AccessController.doPrivileged(
            new PrivilegedAction<String>() {
                public String run() {
                    try {
                        return System.getProperty(PROPS[i]);
                    } catch (SecurityException e) {
                        return null;
                    }
                }
            }
        );
    }

    String[] getJndiProperties() {
        Properties sysProps = AccessController.doPrivileged(
            new PrivilegedAction<Properties>() {
                public Properties run() {
                    try {
                        return System.getProperties();
                    } catch (SecurityException e) {
                        return null;
                    }
                }
            }
        );
        if (sysProps == null) {
            return null;
        }
        String[] jProps = new String[PROPS.length];
        for (int i = 0; i < PROPS.length; i++) {
            jProps[i] = sysProps.getProperty(PROPS[i]);
        }
        return jProps;
    }

    InputStream getResourceAsStream(final Class<?> c, final String name) {
        return AccessController.doPrivileged(
            new PrivilegedAction<InputStream>() {
                public InputStream run() {
                    return c.getResourceAsStream(name);
                }
            }
        );
    }

    InputStream getJavaHomeLibStream(final String filename) {
        return AccessController.doPrivileged(
            new PrivilegedAction<InputStream>() {
                public InputStream run() {
                    try {
                        String javahome = System.getProperty("java.home");
                        if (javahome == null) {
                            return null;
                        }
                        String pathname = javahome + java.io.File.separator +
                            "lib" + java.io.File.separator + filename;
                        return new java.io.FileInputStream(pathname);
                    } catch (Exception e) {
                        return null;
                    }
                }
            }
        );
    }

    NamingEnumeration<InputStream> getResources(final ClassLoader cl,
            final String name) throws IOException {
        Enumeration<URL> urls;
        try {
            urls = AccessController.doPrivileged(
                new PrivilegedExceptionAction<Enumeration<URL>>() {
                    public Enumeration<URL> run() throws IOException {
                        return (cl == null)
                            ? ClassLoader.getSystemResources(name)
                            : cl.getResources(name);
                    }
                }
            );
        } catch (PrivilegedActionException e) {
            throw (IOException)e.getException();
        }
        return new InputStreamEnumeration(urls);
    }

    /**
     * Package private.
     *
     * This internal method returns Thread Context Class Loader (TCCL),
     * if null, returns the system Class Loader.
     *
     * Please don't expose this method as public.
     */
    ClassLoader getContextClassLoader() {

        return AccessController.doPrivileged(
            new PrivilegedAction<ClassLoader>() {
                public ClassLoader run() {
                    ClassLoader loader =
                            Thread.currentThread().getContextClassLoader();
                    if (loader == null) {
                        // Don't use bootstrap class loader directly!
                        loader = ClassLoader.getSystemClassLoader();
                    }

                    return loader;
                }
            }
        );
    }

    /**
     * Given an enumeration of URLs, an instance of this class represents
     * an enumeration of their InputStreams.  Each operation on the URL
     * enumeration is performed within a doPrivileged block.
     * This is used to enumerate the resources under a foreign codebase.
     * This class is not MT-safe.
     */
    class InputStreamEnumeration implements NamingEnumeration<InputStream> {

        private final Enumeration<URL> urls;

        private InputStream nextElement = null;

        InputStreamEnumeration(Enumeration<URL> urls) {
            this.urls = urls;
        }

        /*
         * Returns the next InputStream, or null if there are no more.
         * An InputStream that cannot be opened is skipped.
         */
        private InputStream getNextElement() {
            return AccessController.doPrivileged(
                new PrivilegedAction<InputStream>() {
                    public InputStream run() {
                        while (urls.hasMoreElements()) {
                            try {
                                return urls.nextElement().openStream();
                            } catch (IOException e) {
                                // skip this URL
                            }
                        }
                        return null;
                    }
                }
            );
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
