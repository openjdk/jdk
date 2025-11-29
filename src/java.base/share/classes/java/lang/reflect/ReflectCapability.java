/*
 * Copyright (c) 2001, 2024, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.reflect;

import java.lang.invoke.MethodHandles;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * ReflectCapability is a helper class to provide access to a
 * MethodHandles.Lookup with full capabilities to the
 * java.lang.reflect package.
 *
 * @since 26
 */
public final class ReflectCapability {
    final MethodHandles.Lookup lookup;
    final Class<?> lookupClass;

    private ReflectCapability(MethodHandles.Lookup lookup, Class<?> lookupClass) {
        this.lookup = lookup;
        this.lookupClass = lookupClass;
    }

    /**
     * Returns an instance of ReflectCapability with the full capabilities of the
     * caller class to emulate all supported bytecode behaviors of the caller.
     * These capabilities include private access to members of the caller class
     * and to members of other classes in the same package as the caller class.
     * <p>
     * This object is a <em>reflect capability</em> which may be delegated to
     * trusted agents. Do not store it in place where untrusted code can access it.
     * <p>
     * This method is caller sensitive, which means that it may return different
     * capability to different callers.
     * <p>
     * In cases the {@code ReflectCapability.getInstance} is called from a context
     * where there is no caller frame on the stack (e.g. when called directly
     * from a JNI attached thread), {@code IllegalCallerException} is thrown.
     *
     * @return a reflect capability object with the full capabilities of the
     *         caller class
     * @throws IllegalCallerException if the caller class could not be determined.
     */
    @CallerSensitive
    public static ReflectCapability getInstance() {
        // var lookup = MethodHandles.lookup();
        final Class<?> lookupClass = Reflection.getCallerClass();
        if (lookupClass == null) {
            throw new IllegalCallerException("no caller frame");
        }
        return new ReflectCapability(Reflection.getLookup(lookupClass), lookupClass);
    }

    /**
     * Returns the caller class of the method that invoked this method.
     * <p>
     * <b>NOTE:</b> This method is not public and is subject to change or
     * removal without notice.
     *
     * @return the caller class of the method that invoked this method
     */
    @CallerSensitive
    public static Class<?> returnCallerClass() {
        System.out.println("Reflection.getCallerClass()");
        // new Exception().printStackTrace();
        return Reflection.getCallerClass();
    }
}

