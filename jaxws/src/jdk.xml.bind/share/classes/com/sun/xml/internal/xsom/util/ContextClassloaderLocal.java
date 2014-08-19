/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.xml.internal.xsom.util;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.WeakHashMap;

/**
 * Simple utility ensuring that the value is cached only in case it is non-internal implementation
 */
abstract class ContextClassloaderLocal<V> {

    private static final String FAILED_TO_CREATE_NEW_INSTANCE = "FAILED_TO_CREATE_NEW_INSTANCE";

    private WeakHashMap<ClassLoader, V> CACHE = new WeakHashMap<ClassLoader, V>();

    public V get() throws Error {
        ClassLoader tccl = getContextClassLoader();
        V instance = CACHE.get(tccl);
        if (instance == null) {
            instance = createNewInstance();
            CACHE.put(tccl, instance);
        }
        return instance;
    }

    public void set(V instance) {
        CACHE.put(getContextClassLoader(), instance);
    }

    protected abstract V initialValue() throws Exception;

    private V createNewInstance() {
        try {
            return initialValue();
        } catch (Exception e) {
            throw new Error(format(FAILED_TO_CREATE_NEW_INSTANCE, getClass().getName()), e);
        }
    }

    private static String format(String property, Object... args) {
        String text = ResourceBundle.getBundle(ContextClassloaderLocal.class.getName()).getString(property);
        return MessageFormat.format(text, args);
    }

    private static ClassLoader getContextClassLoader() {
        return (ClassLoader)
                AccessController.doPrivileged(new PrivilegedAction() {
                    public Object run() {
                        ClassLoader cl = null;
                        try {
                            cl = Thread.currentThread().getContextClassLoader();
                        } catch (SecurityException ex) {
                        }
                        return cl;
                    }
                });
    }
}
