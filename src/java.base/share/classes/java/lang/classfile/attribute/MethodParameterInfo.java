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
package java.lang.classfile.attribute;

import java.util.Optional;
import java.util.Set;

import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.ClassFile;
import jdk.internal.classfile.impl.TemporaryConstantPool;
import jdk.internal.classfile.impl.UnboundAttribute;
import jdk.internal.classfile.impl.Util;
import jdk.internal.javac.PreviewFeature;

/**
 * Models a single method parameter in the {@link MethodParametersAttribute}.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface MethodParameterInfo
        permits UnboundAttribute.UnboundMethodParameterInfo {
    /**
     * The name of the method parameter, if there is one.
     *
     * @return the parameter name, if it has one
     */
    Optional<Utf8Entry> name();

    /**
     * Parameter access flags for this parameter, as a bit mask.  Valid
     * parameter flags include {@link ClassFile#ACC_FINAL},
     * {@link ClassFile#ACC_SYNTHETIC}, and {@link ClassFile#ACC_MANDATED}.
     *
     * @return the access flags, as a bit mask
     */
    int flagsMask();

    /**
     * Parameter access flags for this parameter.
     *
     * @return the access flags, as a bit mask
     */
    default Set<AccessFlag> flags() {
        return AccessFlag.maskToAccessFlags(flagsMask(), AccessFlag.Location.METHOD_PARAMETER);
    }

    /**
     * {@return whether the method parameter has a specific flag set}
     * @param flag the method parameter flag
     */
    default boolean has(AccessFlag flag) {
        return Util.has(AccessFlag.Location.METHOD_PARAMETER, flagsMask(), flag);
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name
     * @param flags the method parameter access flags
     */
    static MethodParameterInfo of(Optional<Utf8Entry> name, int flags) {
        return new UnboundAttribute.UnboundMethodParameterInfo(name, flags);
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name
     * @param flags the method parameter access flags
     */
    static MethodParameterInfo of(Optional<String> name, AccessFlag... flags) {
        return of(name.map(TemporaryConstantPool.INSTANCE::utf8Entry), Util.flagsToBits(AccessFlag.Location.METHOD_PARAMETER, flags));
    }

    /**
     * {@return a method parameter description}
     * @param name the method parameter name
     * @param flags the method parameter access flags
     */
    static MethodParameterInfo ofParameter(Optional<String> name, int flags) {
        return of(name.map(TemporaryConstantPool.INSTANCE::utf8Entry), flags);
    }
}
