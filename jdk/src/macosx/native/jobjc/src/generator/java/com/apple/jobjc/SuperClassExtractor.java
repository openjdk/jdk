/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.apple.jobjc;

import java.util.Map;

import com.apple.internal.jobjc.generator.model.Clazz;

/*
 * Isolating all the reflection trickery to hijack the runtime into giving up its secrets
 * without actually having a full working version of JObjC. Below is a bunch of evil reflection,
 * but it allows the generated output to have a cleaner design.
 */
public class SuperClassExtractor {
    public static Clazz getSuperClassFor(final String className, final MacOSXFramework nativeFramework, final Map<String, Clazz> allClasses) throws Throwable {
        final NSClass<ID> nativeClass = new NSClass<ID>(className, nativeFramework.getRuntime());
        final NSClass<? extends ID> nativeSuperClass = UnsafeRuntimeAccess.getSuperClass(nativeClass);
        final String superClassName = UnsafeRuntimeAccess.getClassNameFor(nativeSuperClass);
        if ("nil".equals(superClassName)) return null;

        final Clazz superClazz = allClasses.get(superClassName);
        if (superClazz != null) return superClazz;

        final Clazz superClazzX = getSuperClassFor(superClassName, nativeFramework, allClasses);
        System.out.print("[Warning] class \"" + superClassName + "\" not found in bridge support files, ");
        System.out.println("using \"" + superClazzX.name + "\" as superclass for \"" + className + "\"");
        return superClazzX;
    }
}
