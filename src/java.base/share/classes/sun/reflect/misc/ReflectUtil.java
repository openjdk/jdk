/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.reflect.misc;

import jdk.internal.reflect.Reflection;

public final class ReflectUtil {

    private ReflectUtil() {
    }

    /**
     * Ensures that access to a method or field is granted and throws
     * IllegalAccessException if not. This method is not suitable for checking
     * access to constructors.
     *
     * @param currentClass the class performing the access
     * @param memberClass the declaring class of the member being accessed
     * @param target the target object if accessing instance field or method;
     *               or null if accessing static field or method or if target
     *               object access rights will be checked later
     * @param modifiers the member's access modifiers
     * @throws IllegalAccessException if access to member is denied
     * @implNote Delegates directly to
     *           {@link Reflection#ensureMemberAccess(Class, Class, Class, int)}
     *           which should be used instead.
     */
    public static void ensureMemberAccess(Class<?> currentClass,
                                          Class<?> memberClass,
                                          Object target,
                                          int modifiers)
        throws IllegalAccessException
    {
        Reflection.ensureMemberAccess(currentClass,
                                      memberClass,
                                      target == null ? null : target.getClass(),
                                      modifiers);
    }
}
