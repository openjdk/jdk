/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.api.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to invoke sun.reflect.misc.MethodUtil.invoke() if available. If not (other then Oracle JDK) fallbacks
 * to java.lang,reflect.Method.invoke()
 *
 * Be careful, copy of this class exists in several packages, iny modification must be done to other copies too!
 */
class MethodUtil {

    private static final Logger LOGGER = Logger.getLogger(MethodUtil.class.getName());
    private static final Method INVOKE_METHOD;

    static {
        Method method;
        try {
            Class<?> clazz = Class.forName("sun.reflect.misc.MethodUtil");
            method = clazz.getMethod("invoke", Method.class, Object.class, Object[].class);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Class sun.reflect.misc.MethodUtil found; it will be used to invoke methods.");
            }
        } catch (Throwable t) {
            method = null;
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Class sun.reflect.misc.MethodUtil not found, probably non-Oracle JVM");
            }
        }
        INVOKE_METHOD = method;
    }

    static Object invoke(Object target, Method method, Object[] args) throws IllegalAccessException, InvocationTargetException {
        if (INVOKE_METHOD != null) {
            // sun.reflect.misc.MethodUtil.invoke(method, owner, args)
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Invoking method using sun.reflect.misc.MethodUtil");
            }
            try {
                return INVOKE_METHOD.invoke(null, method, target, args);
            } catch (InvocationTargetException ite) {
                // unwrap invocation exception added by reflection code ...
                throw unwrapException(ite);
            }
        } else {
            // other then Oracle JDK ...
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Invoking method directly, probably non-Oracle JVM");
            }
            return method.invoke(target, args);
        }
    }

    private static InvocationTargetException unwrapException(InvocationTargetException ite) {
        Throwable targetException = ite.getTargetException();
        if (targetException != null && targetException instanceof InvocationTargetException) {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "Unwrapping invocation target exception");
            }
            return (InvocationTargetException) targetException;
        } else {
            return ite;
        }
    }

}
