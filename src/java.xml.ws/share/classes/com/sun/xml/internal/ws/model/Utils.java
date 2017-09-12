/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.xml.internal.bind.v2.model.nav.Navigator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utils class.
 *
 * WARNING: If you are doing any changes don't forget to change other Utils classes in different packages.
 *
 * Has *package private* access to avoid inappropriate usage.
 */
final class Utils {

    private static final Logger LOGGER = Logger.getLogger(Utils.class.getName());

    /**
     * static ReflectionNavigator field to avoid usage of reflection every time we use it.
     */
    static final Navigator<Type, Class, Field, Method> REFLECTION_NAVIGATOR;

    static { // we statically initializing REFLECTION_NAVIGATOR property
        try {
            final Class refNav = Class.forName("com.sun.xml.internal.bind.v2.model.nav.ReflectionNavigator");

            // requires accessClassInPackage privilege
            final Method getInstance = AccessController.doPrivileged(
                    new PrivilegedAction<Method>() {
                        @Override
                        public Method run() {
                            try {
                                Method getInstance = refNav.getDeclaredMethod("getInstance");
                                getInstance.setAccessible(true);
                                return getInstance;
                            } catch (NoSuchMethodException e) {
                                throw new IllegalStateException("ReflectionNavigator.getInstance can't be found");
                            }
                        }
                    }
            );

            //noinspection unchecked
            REFLECTION_NAVIGATOR = (Navigator<Type, Class, Field, Method>) getInstance.invoke(null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Can't find ReflectionNavigator class");
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("ReflectionNavigator.getInstance throws the exception");
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("ReflectionNavigator.getInstance method is inaccessible");
        } catch (SecurityException e) {
            LOGGER.log(Level.FINE, "Unable to access ReflectionNavigator.getInstance", e);
            throw e;
        }
    }

    /**
     * private constructor to avoid util class instantiating
     */
    private Utils() {
    }
}
