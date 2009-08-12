/*
 * Copyright 2007 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
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
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */

package com.sun.tools.javac.file;

import java.io.Closeable;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.jar.JarFile;

/**
 * A URLClassLoader that also implements Closeable.
 * Reflection is used to access internal data structures in the URLClassLoader,
 * since no public API exists for this purpose. Therefore this code is somewhat
 * fragile. Caveat emptor.
 * @throws Error if the internal data structures are not as expected.
 *
 *  <p><b>This is NOT part of any API supported by Sun Microsystems.  If
 *  you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
class CloseableURLClassLoader
        extends URLClassLoader implements Closeable {
    CloseableURLClassLoader(URL[] urls, ClassLoader parent) throws Error {
        super(urls, parent);
        try {
            getLoaders(); //proactive check that URLClassLoader is as expected
        } catch (Throwable t) {
            throw new Error("cannot create CloseableURLClassLoader", t);
        }
    }

    /**
     * Close any jar files that may have been opened by the class loader.
     * Reflection is used to access the jar files in the URLClassLoader's
     * internal data structures.
     * @throws java.io.IOException if the jar files cannot be found for any
     * reson, or if closing the jar file itself causes an IOException.
     */
    public void close() throws IOException {
        try {
            for (Object l: getLoaders()) {
                if (l.getClass().getName().equals("sun.misc.URLClassPath$JarLoader")) {
                    Field jarField = l.getClass().getDeclaredField("jar");
                    JarFile jar = (JarFile) getField(l, jarField);
                    //System.err.println("CloseableURLClassLoader: closing " + jar);
                    jar.close();
                }
            }
        } catch (Throwable t) {
            IOException e = new IOException("cannot close class loader");
            e.initCause(t);
            throw e;
        }
    }

    private ArrayList<?> getLoaders()
            throws NoSuchFieldException, IllegalArgumentException, IllegalAccessException
    {
        Field ucpField = URLClassLoader.class.getDeclaredField("ucp");
        Object urlClassPath = getField(this, ucpField);
        if (urlClassPath == null)
            throw new AssertionError("urlClassPath not set in URLClassLoader");
        Field loadersField = urlClassPath.getClass().getDeclaredField("loaders");
        return (ArrayList<?>) getField(urlClassPath, loadersField);
    }

    private Object getField(Object o, Field f)
            throws IllegalArgumentException, IllegalAccessException {
        boolean prev = f.isAccessible();
        try {
            f.setAccessible(true);
            return f.get(o);
        } finally {
            f.setAccessible(prev);
        }
    }

}
