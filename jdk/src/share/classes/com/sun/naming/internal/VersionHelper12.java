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
import java.net.URLClassLoader;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.Hashtable;
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

    private boolean getSystemPropsFailed = false;

    VersionHelper12() {} // Disallow external from creating one of these.

    public Class loadClass(String className) throws ClassNotFoundException {
        ClassLoader cl = getContextClassLoader();
        return Class.forName(className, true, cl);
    }

    /**
      * Package private.
      */
    Class loadClass(String className, ClassLoader cl)
        throws ClassNotFoundException {
        return Class.forName(className, true, cl);
    }

    /**
     * @param className A non-null fully qualified class name.
     * @param codebase A non-null, space-separated list of URL strings.
     */
    public Class loadClass(String className, String codebase)
        throws ClassNotFoundException, MalformedURLException {
        ClassLoader cl;

        ClassLoader parent = getContextClassLoader();
        cl = URLClassLoader.newInstance(getUrlArray(codebase), parent);

        return Class.forName(className, true, cl);
    }

    String getJndiProperty(final int i) {
        return (String) AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
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
        if (getSystemPropsFailed) {
            return null;        // after one failure, don't bother trying again
        }
        Properties sysProps = (Properties) AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    try {
                        return System.getProperties();
                    } catch (SecurityException e) {
                        getSystemPropsFailed = true;
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

    InputStream getResourceAsStream(final Class c, final String name) {
        return (InputStream) AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    return c.getResourceAsStream(name);
                }
            }
        );
    }

    InputStream getJavaHomeLibStream(final String filename) {
        return (InputStream) AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
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

    NamingEnumeration getResources(final ClassLoader cl, final String name)
            throws IOException
    {
        Enumeration urls;
        try {
            urls = (Enumeration) AccessController.doPrivileged(
                new PrivilegedExceptionAction() {
                    public Object run() throws IOException {
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

    ClassLoader getContextClassLoader() {
        return (ClassLoader) AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    return Thread.currentThread().getContextClassLoader();
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
    class InputStreamEnumeration implements NamingEnumeration {

        private final Enumeration urls;

        private Object nextElement = null;

        InputStreamEnumeration(Enumeration urls) {
            this.urls = urls;
        }

        /*
         * Returns the next InputStream, or null if there are no more.
         * An InputStream that cannot be opened is skipped.
         */
        private Object getNextElement() {
            return AccessController.doPrivileged(
                new PrivilegedAction() {
                    public Object run() {
                        while (urls.hasMoreElements()) {
                            try {
                                return ((URL)urls.nextElement()).openStream();
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

        public Object next() {
            if (hasMore()) {
                Object res = nextElement;
                nextElement = null;
                return res;
            } else {
                throw new NoSuchElementException();
            }
        }

        public Object nextElement() {
            return next();
        }

        public void close() {
        }
    }
}
