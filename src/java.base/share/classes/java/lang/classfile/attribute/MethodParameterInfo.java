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
package java.lang.classfile.attribute;

import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.AccessFlag;
import java.lang.reflect.Parameter;
import java.util.Optional;
import java.util.Set;

import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;

/**
 * Models a single method parameter in the {@link MethodParametersAttribute}.
 *
 * @see MethodParametersAttribute#parameters()
 * @see Parameter
 * @jvms 4.7.24 The {@code MethodParameters} Attribute
 * @since 24
 */
public sealed interface MethodParameterInfo
        permits UnboundAttribute.UnboundMethodParameterInfo {
    /**
     * {@return the parameter name, if it has one}
     *
     * @see Parameter#getName()
     * @see Parameter#isNamePresent()
     */
    Optional<Utf8Entry> name();

    /**
     * {@return the access flags, as a bit mask}
     *
     * @see Parameter#getModifiers()
     * @see AccessFlag.Location#METHOD_PARAMETER
     */
    int flagsMask();

    /**
     * {@return the access flags, as a set of flag enums}
     *
     * @throws IllegalArgumentException if the flags mask has any undefined bit set
     * @see Parameter#accessFlags()
     * @see AccessFlag.Location#METHOD_PARAMETER
     */
    default Set<AccessFlag> flags() {
        return AccessFlag.maskToAccessFlags(flagsMask(), AccessFlag.Location.METHOD_PARAMETER);
    }

    /**
     * {@return whether the method parameter has a specific flag set}
     * @param flag the method parameter flag
     * @see AccessFlag.Location#METHOD_PARAMETER
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.METHOD_PARAMETER, flagsMask(), flag);
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name, may be empty
     * @param flags the method parameter access flags
     */
    static MethodParameterInfo of(Optional<Utf8Entry> name, int flags) {
        return new UnboundAttribute.UnboundMethodParameterInfo(name, flags);
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name, may be empty
     * @param flags the method parameter access flags
     * @throws IllegalArgumentException if any flag cannot be applied to the
     *         {@link AccessFlag.Location#METHOD_PARAMETER} location
     */
    static MethodParameterInfo of(Optional<String> name, AccessFlag... flags) {
        return of(name.map(TemporaryConstantPool.INSTANCE::utf8Entry), Util.flagsToBits(AccessFlag.Location.METHOD_PARAMETER, flags));
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name, may be empty
     * @param flags the method parameter access flags
     */
    static MethodParameterInfo ofParameter(Optional<String> name, int flags) {
        return of(name.map(TemporaryConstantPool.INSTANCE::utf8Entry), flags);
    }
}
