/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Set;
import jdk.internal.classfile.impl.AccessFlagsImpl;
import java.lang.reflect.AccessFlag;
import jdk.internal.javac.PreviewFeature;

/**
 * Models the access flags for a class, method, or field.  Delivered as a
 * {@link ClassElement}, {@link FieldElement}, or {@link MethodElement}
 * when traversing the corresponding model type.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface AccessFlags
        extends ClassElement, MethodElement, FieldElement
        permits AccessFlagsImpl {

    /**
     * {@return the access flags, as a bit mask}
     */
    int flagsMask();

    /**
     * {@return the access flags}
     */
    Set<AccessFlag> flags();

    /**
     * {@return whether the specified flag is present}  The specified flag
     * should be a valid flag for the classfile location associated with this
     * element otherwise false is returned.
     * @param flag the flag to test
     */
    boolean has(AccessFlag flag);

    /**
     * {@return the classfile location for this element, which is either class,
     * method, or field}
     */
    AccessFlag.Location location();

    /**
     * {@return an {@linkplain AccessFlags} for a class}
     * @param mask the flags to be set, as a bit mask
     */
    static AccessFlags ofClass(int mask) {
        return new AccessFlagsImpl(AccessFlag.Location.CLASS, mask);
    }

    /**
     * {@return an {@linkplain AccessFlags} for a class}
     * @param flags the flags to be set
     */
    static AccessFlags ofClass(AccessFlag... flags) {
        return new AccessFlagsImpl(AccessFlag.Location.CLASS, flags);
    }

    /**
     * {@return an {@linkplain AccessFlags} for a field}
     * @param mask the flags to be set, as a bit mask
     */
    static AccessFlags ofField(int mask) {
        return new AccessFlagsImpl(AccessFlag.Location.FIELD, mask);
    }

    /**
     * {@return an {@linkplain AccessFlags} for a field}
     * @param flags the flags to be set
     */
    static AccessFlags ofField(AccessFlag... flags) {
        return new AccessFlagsImpl(AccessFlag.Location.FIELD, flags);
    }

    /**
     * {@return an {@linkplain AccessFlags} for a method}
     * @param mask the flags to be set, as a bit mask
     */
    static AccessFlags ofMethod(int mask) {
        return new AccessFlagsImpl(AccessFlag.Location.METHOD, mask);
    }

    /**
     * {@return an {@linkplain AccessFlags} for a method}
     * @param flags the flags to be set
     */
    static AccessFlags ofMethod(AccessFlag... flags) {
        return new AccessFlagsImpl(AccessFlag.Location.METHOD, flags);
    }
}
