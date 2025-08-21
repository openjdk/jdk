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

import java.lang.reflect.AccessFlag;
import java.util.Set;

import jdk.internal.classfile.impl.AccessFlagsImpl;

/**
 * Models the access flags for a class, method, or field.  The access flags
 * appears exactly once in each class, method, or field; a {@link
 * ClassBuilder} and a {@link FieldBuilder} chooses an unspecified default value
 * if access flags are not provided, and a {@link MethodBuilder} is always
 * created with access flags.
 * <p>
 * {@code AccessFlags} cannot be created via a factory method directly; it can
 * be created with {@code withFlags} methods on the respective builders.
 * <p>
 * A {@link MethodBuilder} throws an {@link IllegalArgumentException} if it is
 * supplied an {@code AccessFlags} object that changes the preexisting
 * {@link ClassFile#ACC_STATIC ACC_STATIC} flag of the builder, because the
 * access flag change may invalidate previously supplied data to the builder.
 *
 * @apiNote
 * The access flags of classes, methods, and fields are modeled as a standalone
 * object to support streaming as elements for {@link ClassFileTransform}.
 * Other access flags are not elements of a {@link CompoundElement} and thus not
 * modeled by {@code AccessFlags}; they provide their own {@code flagsMask},
 * {@code flags}, and {@code has} methods.
 *
 * @see ClassModel#flags()
 * @see FieldModel#flags()
 * @see MethodModel#flags()
 * @see ClassBuilder#withFlags
 * @see FieldBuilder#withFlags
 * @see MethodBuilder#withFlags
 * @since 24
 */
public sealed interface AccessFlags
        extends ClassElement, MethodElement, FieldElement
        permits AccessFlagsImpl {

    /**
     * {@return the access flags, as a bit mask}  It is in the range of unsigned
     * short, {@code [0, 0xFFFF]}.
     */
    int flagsMask();

    /**
     * {@return the access flags, as a set of flag enums}
     *
     * @throws IllegalArgumentException if the flags mask has any undefined bit set
     * @see #location()
     */
    Set<AccessFlag> flags();

    /**
     * {@return whether the specified flag is set}  If the specified flag
     * is not available to this {@linkplain #location() location}, returns
     * {@code false}.
     *
     * @param flag the flag to test
     * @see #location()
     */
    boolean has(AccessFlag flag);

    /**
     * {@return the {@code class} file location for this element, which is
     * either class, method, or field}
     *
     * @see AccessFlag.Location#CLASS
     * @see AccessFlag.Location#FIELD
     * @see AccessFlag.Location#METHOD
     */
    AccessFlag.Location location();
}
