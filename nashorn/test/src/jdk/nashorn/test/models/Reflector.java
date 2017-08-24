/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.test.models;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import jdk.nashorn.internal.runtime.Context;

/**
 * Few tests reflectively invoke or read fields of Nashorn classes
 * and objects - but of packages that are not exported to any module!
 * But, those packages are qualified exported to test [java] code
 * such as this class. So, test scripts can invoke the methods of this
 * class instead.
 */
public final class Reflector {
    private Reflector() {}
    private static final Module NASHORN_MOD = Context.class.getModule();

    public static void setAccessible(Executable e) {
        if (e.getDeclaringClass().getModule() != NASHORN_MOD) {
            throw new RuntimeException(e + " is not from Nashorn module");
        }

        e.setAccessible(true);
    }

    public static void setAccessible(Field f) {
        if (f.getDeclaringClass().getModule() != NASHORN_MOD) {
            throw new RuntimeException(f + " is not from Nashorn module");
        }

        f.setAccessible(true);
    }

    public static Object invoke(final Method m, final Object self, final Object...args) {
        if (m.getDeclaringClass().getModule() != NASHORN_MOD) {
            throw new RuntimeException(m + " is not from Nashorn module");
        }

        try {
            return m.invoke(self, args);
        } catch (final Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public static Object newInstance(final Constructor c, final Object...args) {
        if (c.getDeclaringClass().getModule() != NASHORN_MOD) {
            throw new RuntimeException(c + " is not from Nashorn module");
        }

        try {
            return c.newInstance(args);
        } catch (final Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    public static Object get(final Field f, final Object self) {
        if (f.getDeclaringClass().getModule() != NASHORN_MOD) {
            throw new RuntimeException(f + " is not from Nashorn module");
        }

        try {
            return f.get(self);
        } catch (final Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException)e;
            } else {
                throw new RuntimeException(e);
            }
        }
    }
}
