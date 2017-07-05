/*
 * Copyright (c) 2008, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.model;

import javax.xml.ws.WebServiceException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A {@link ClassLoader} used to "inject" wrapper and exception bean classes
 * into the VM.
 *
 * @author Jitendra kotamraju
 */
final class Injector {

    private static final Logger LOGGER = Logger.getLogger(Injector.class.getName());

    private static final Method defineClass;
    private static final Method resolveClass;
    private static final Method getPackage;
    private static final Method definePackage;

    static {
        try {
            defineClass = ClassLoader.class.getDeclaredMethod("defineClass",String.class,byte[].class,Integer.TYPE,Integer.TYPE);
            resolveClass = ClassLoader.class.getDeclaredMethod("resolveClass",Class.class);
            getPackage = ClassLoader.class.getDeclaredMethod("getPackage", String.class);
            definePackage = ClassLoader.class.getDeclaredMethod("definePackage",
                    String.class, String.class, String.class, String.class,
                    String.class, String.class, String.class, URL.class);
        } catch (NoSuchMethodException e) {
            // impossible
            throw new NoSuchMethodError(e.getMessage());
        }
        AccessController.doPrivileged(new PrivilegedAction<Void>() {
            public Void run() {
                // TODO: check security implication
                // do these setAccessible allow anyone to call these methods freely?s
                defineClass.setAccessible(true);
                resolveClass.setAccessible(true);
                getPackage.setAccessible(true);
                definePackage.setAccessible(true);
                return null;
            }
        });
    }

    static synchronized Class inject(ClassLoader cl, String className, byte[] image) {
        // To avoid race conditions let us check if the classloader
        // already contains the class
        try {
            return cl.loadClass(className);
        } catch (ClassNotFoundException e) {
            // nothing to do
        }
        try {
            int packIndex = className.lastIndexOf('.');
            if (packIndex != -1) {
                String pkgname = className.substring(0, packIndex);
                // Check if package already loaded.
                Package pkg = (Package)getPackage.invoke(cl, pkgname);
                if (pkg == null) {
                    definePackage.invoke(cl, pkgname, null, null, null, null, null, null, null);
                }
            }

            Class c = (Class)defineClass.invoke(cl,className.replace('/','.'),image,0,image.length);
            resolveClass.invoke(cl, c);
            return c;
        } catch (IllegalAccessException e) {
            LOGGER.log(Level.FINE,"Unable to inject "+className,e);
            throw new WebServiceException(e);
        } catch (InvocationTargetException e) {
            LOGGER.log(Level.FINE,"Unable to inject "+className,e);
            throw new WebServiceException(e);
        }
    }

}
