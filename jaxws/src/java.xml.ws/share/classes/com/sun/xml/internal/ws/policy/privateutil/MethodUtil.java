/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.ws.policy.privateutil;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility class to invoke com.sun.xml.internal.ws.policy.util.MethodUtil.invoke() if available. If not (other then Oracle JDK) fallbacks
 * to java.lang,reflect.Method.invoke()
 */
class MethodUtil {

    private static final Logger LOGGER = Logger.getLogger(MethodUtil.class.getName());

    static Object invoke(Object target, Method method, Object[] args) throws IllegalAccessException, InvocationTargetException {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "Invoking method using com.sun.xml.internal.ws.policy.util.MethodUtil");
        }
        try {
            return com.sun.xml.internal.ws.policy.util.MethodUtil.invoke(method, target, args);
        } catch (InvocationTargetException ite) {
            // unwrap invocation exception added by reflection code ...
            throw unwrapException(ite);
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
