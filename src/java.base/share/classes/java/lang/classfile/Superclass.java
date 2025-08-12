/*
 * Copyright (c) 2022, 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile;

import java.lang.classfile.constantpool.ClassEntry;
import java.lang.constant.ClassDesc;

import jdk.internal.classfile.impl.SuperclassImpl;

/**
 * Models the superclass (JVMS {@jvms 4.1}) of a class.  A {@code Superclass}
 * appears at most once in a {@link ClassModel}: it must be absent for
 * {@linkplain ClassModel#isModuleInfo() module descriptors} or the {@link
 * Object} class, and must be present otherwise.  A {@link ClassBuilder} sets
 * the {@link Object} class as the superclass if the superclass is not supplied
 * and the class to build is required to have a superclass.
 * <p>
 * All {@linkplain ClassFile#ACC_INTERFACE interfaces} have {@link Object} as
 * their superclass.
 *
 * @see ClassModel#superclass()
 * @see ClassBuilder#withSuperclass
 * @jvms 4.1 The {@code ClassFile} Structure
 * @since 24
 */
public sealed interface Superclass
        extends ClassElement
        permits SuperclassImpl {

    /** {@return the superclass} */
    ClassEntry superclassEntry();

    /**
     * {@return a {@linkplain Superclass} element}
     *
     * @param superclassEntry the superclass
     */
    static Superclass of(ClassEntry superclassEntry) {
        return new SuperclassImpl(superclassEntry);
    }
}
