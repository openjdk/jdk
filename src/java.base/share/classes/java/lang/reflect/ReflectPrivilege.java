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
import java.lang.invoke.MethodHandles.Lookup;

import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 * [TBD] FullPrivilege is a helper class to provide access to a
 * MethodHandles.Lookup with full capabilities to the java.lang.reflect package.
 *
 * @since 26
 */
public final class ReflectPrivilege {

    private final MethodHandles.Lookup lookup;

    private ReflectPrivilege(MethodHandles.Lookup lookup) {
        this.lookup = lookup;
    }

    /**
     * Returns an instance of ReflectPrivilege with the full privilege
     * capabilities of the caller class to emulate all supported bytecode
     * behaviors of the caller. These capabilities include private access to
     * members of the caller class and to members of other classes in the same
     * package as the caller class.
     * <p>
     * This object is a <em>reflect access capability</em> which may be
     * delegated to trusted agents. Do not store it in place where untrusted
     * code can access it.
     * <p>
     * This method is caller sensitive, which means that it may return different
     * capability to different callers.
     * <p>
     * In cases the {@code ReflectPrivilege.fullPrivilege()} is called from a
     * context where there is no caller frame on the stack (e.g. when called
     * directly from a JNI attached thread), {@code IllegalCallerException} is
     * thrown.
     *
     * @return a reflect acces capability object with the full privilege
     * capabilities of the caller class
     * @throws IllegalCallerException if the caller class could not be
     * determined.
     */
    @CallerSensitive
    public static ReflectPrivilege fullPrivilege() {
        final Class<?> c = Reflection.getCallerClass();
        if (c == null) {
            throw new IllegalCallerException("no caller frame");
        }
        return new ReflectPrivilege(Reflection.getLookup(c));
    }

    /*
     * [TBD] should we just carry over the "privilege" of the lookup? if so then
     * the class name probably should be renamed to ReflectCapability and the
     * other/above factory method should be renamed to fullPrivilege()?
     *
     * @return a reflect acces capability object with the full capabilities of
     * the lookup class of the specified lookup object.
     *
    public static ReflectPrivilege of(MethodHandles.Lookup lookup) {
        final Class<?> c = lookup.lookupClass();
        if (c == null) {
            throw new IllegalCallerException("lookup does not have a caller class");
        }
        return new FullPrivilege(Reflection.getLookup(c));
    }
     */

    /**
     * Returns the caller class of this access privilege. It is the class that
     * the access checks are performed for visibility and access permissions.
     *
     * @return the caller class of this privilege object.
     */
    public Class<?> callerClass() {
        return lookup.lookupClass();
    }

    /**
     * Returns the lookup of this access privilege. It is the lookup that performs
     * the access visibility checks, access permissions, and finds & returns a
     * methodhandle.
     *
     * @return the lookup of this privilege object.
     */
    Lookup lookup() {
        return lookup;
    }
}
