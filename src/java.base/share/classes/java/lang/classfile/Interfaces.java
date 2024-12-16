/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Arrays;
import java.util.List;

import jdk.internal.classfile.impl.InterfacesImpl;
import jdk.internal.classfile.impl.Util;

/**
 * Models the interfaces of a class.  Delivered as a {@link
 * java.lang.classfile.ClassElement} when traversing a {@link ClassModel}.
 *
 * @since 24
 */
public sealed interface Interfaces
        extends ClassElement
        permits InterfacesImpl {

    /** {@return the interfaces of this class} */
    List<ClassEntry> interfaces();

    /**
     * {@return an {@linkplain Interfaces} element}
     * @param interfaces the interfaces
     */
    static Interfaces of(List<ClassEntry> interfaces) {
        return new InterfacesImpl(interfaces);
    }

    /**
     * {@return an {@linkplain Interfaces} element}
     * @param interfaces the interfaces
     */
    static Interfaces of(ClassEntry... interfaces) {
        return of(List.of(interfaces));
    }

    /**
     * {@return an {@linkplain Interfaces} element}
     * @param interfaces the interfaces
     */
    static Interfaces ofSymbols(List<ClassDesc> interfaces) {
        return of(Util.entryList(interfaces));
    }

    /**
     * {@return an {@linkplain Interfaces} element}
     * @param interfaces the interfaces
     */
    static Interfaces ofSymbols(ClassDesc... interfaces) {
        return ofSymbols(Arrays.asList(interfaces));
    }
}
