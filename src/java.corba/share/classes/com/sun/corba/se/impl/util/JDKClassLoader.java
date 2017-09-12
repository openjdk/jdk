/*
 * Copyright (c) 1999, 2004, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Licensed Materials - Property of IBM
 * RMI-IIOP v1.0
 * Copyright IBM Corp. 1998 1999  All Rights Reserved
 *
 */

package com.sun.corba.se.impl.util;

import sun.corba.Bridge ;

import java.util.Map ;
import java.util.WeakHashMap ;
import java.util.Collections ;

import java.security.AccessController ;
import java.security.PrivilegedAction ;

/**
 *  Utility method for crawling call stack to load class
 */
class JDKClassLoader {

    private static final JDKClassLoaderCache classCache
        = new JDKClassLoaderCache();

    private static final Bridge bridge =
        (Bridge)AccessController.doPrivileged(
            new PrivilegedAction() {
                public Object run() {
                    return Bridge.get() ;
                }
            }
        ) ;

    static Class loadClass(Class aClass, String className)
        throws ClassNotFoundException {

        // Maintain the same error semantics as Class.forName()
        if (className == null) {
            throw new NullPointerException();
        }
        if (className.length() == 0) {
            throw new ClassNotFoundException();
        }

        // It would be nice to bypass JDKClassLoader's attempts completely
        // if it's known that the latest user defined ClassLoader will
        // fail.
        //
        // Otherwise, we end up calling Class.forName here as well as in
        // the next step in JDKBridge.  That can take a long time depending
        // on the length of the classpath.

        // Note: Looking at the only place in JDKBridge where this code
        // is invoked, it is clear that aClass will always be null.
        ClassLoader loader;
        if (aClass != null) {
            loader = aClass.getClassLoader();
        } else {
            loader = bridge.getLatestUserDefinedLoader();
        }
        // See createKey for a description of what's involved
        Object key = classCache.createKey(className, loader);

        if (classCache.knownToFail(key)) {
            throw new ClassNotFoundException(className);
        } else {
            try {
                // Loading this class with the call stack
                // loader isn't known to fail, so try
                // to load it.
                return Class.forName(className, false, loader);
            } catch(ClassNotFoundException cnfe) {
                // Record that we failed to find the class
                // with this particular loader.  This way, we won't
                // waste time looking with this loader, again.
                classCache.recordFailure(key);
                throw cnfe;
            }
        }
    }

    /**
     * Private cache implementation specific to JDKClassLoader.
     */
    private static class JDKClassLoaderCache
    {
        // JDKClassLoader couldn't find the class with the located
        // ClassLoader.  Note this in our cache so JDKClassLoader
        // can abort early next time.
        public final void recordFailure(Object key) {
            cache.put(key, JDKClassLoaderCache.KNOWN_TO_FAIL);
        }

        // Factory for a key (CacheKey is an implementation detail
        // of JDKClassLoaderCache).
        //
        // A key currently consists of the class name as well as
        // the latest user defined class loader, so it's fairly
        // expensive to create.
        public final Object createKey(String className, ClassLoader latestLoader) {
            return new CacheKey(className, latestLoader);
        }

        // Determine whether or not this combination of class name
        // and ClassLoader is known to fail.
        public final boolean knownToFail(Object key) {
            return cache.get(key) == JDKClassLoaderCache.KNOWN_TO_FAIL;
        }

        // Synchronized WeakHashMap
        private final Map cache
            = Collections.synchronizedMap(new WeakHashMap());

        // Cache result used to mark the caches when there is
        // no way JDKClassLoader could succeed with the given
        // key
        private static final Object KNOWN_TO_FAIL = new Object();

        // Key consisting of the class name and the latest
        // user defined class loader
        private static class CacheKey
        {
            String className;
            ClassLoader loader;

            public CacheKey(String className, ClassLoader loader) {
                this.className = className;
                this.loader = loader;
            }

            // Try to incorporate both class name and loader
            // into the hashcode
            public int hashCode() {
                if (loader == null)
                    return className.hashCode();
                else
                    return className.hashCode() ^ loader.hashCode();
            }

            public boolean equals(Object obj) {
                try {

                    // WeakHashMap may compare null keys
                    if (obj == null)
                        return false;

                    CacheKey other = (CacheKey)obj;

                    // I've made a decision to actually compare the
                    // loader references.  I don't want a case when
                    // two loader instances override their equals
                    // methods and only compare code base.
                    //
                    // This way, at worst, our performance will
                    // be slower, but we know we'll do the correct
                    // loading.
                    return (className.equals(other.className) &&
                            loader == other.loader);

                } catch (ClassCastException cce) {
                    return false;
                }
            }
        }
    }
}
