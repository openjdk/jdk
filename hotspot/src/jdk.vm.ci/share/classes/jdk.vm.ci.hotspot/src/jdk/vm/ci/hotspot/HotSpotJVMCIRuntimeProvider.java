/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package jdk.vm.ci.hotspot;

import java.io.OutputStream;

import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JVMCIMetaAccessContext;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaType;
import jdk.vm.ci.runtime.JVMCIRuntime;
import jdk.internal.misc.Unsafe;

//JaCoCo Exclude

/**
 * Configuration information for the HotSpot JVMCI runtime.
 */
public interface HotSpotJVMCIRuntimeProvider extends JVMCIRuntime {

    HotSpotVMConfig getConfig();

    CompilerToVM getCompilerToVM();

    /**
     * Gets an output stream that writes to the HotSpot's {@code tty} stream.
     */
    OutputStream getLogStream();

    /**
     * Converts a name to a Java type. This method attempts to resolve {@code name} to a
     * {@link ResolvedJavaType}.
     *
     * @param name a well formed Java type in {@linkplain JavaType#getName() internal} format
     * @param accessingType the context of resolution which must be non-null
     * @param resolve specifies whether resolution failure results in an unresolved type being
     *            return or a {@link LinkageError} being thrown
     * @return a Java type for {@code name} which is guaranteed to be of type
     *         {@link ResolvedJavaType} if {@code resolve == true}
     * @throws LinkageError if {@code resolve == true} and the resolution failed
     * @throws NullPointerException if {@code accessingClass} is {@code null}
     */
    JavaType lookupType(String name, HotSpotResolvedObjectType accessingType, boolean resolve);

    /**
     * Gets the JVMCI mirror for a {@link Class} object.
     *
     * @return the {@link ResolvedJavaType} corresponding to {@code javaClass}
     */
    ResolvedJavaType fromClass(Class<?> clazz);

    JVMCIMetaAccessContext getMetaAccessContext();

    /**
     * The offset from the origin of an array to the first element.
     *
     * @return the offset in bytes
     */
    static int getArrayBaseOffset(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_BASE_OFFSET;
            case Byte:
                return Unsafe.ARRAY_BYTE_BASE_OFFSET;
            case Char:
                return Unsafe.ARRAY_CHAR_BASE_OFFSET;
            case Short:
                return Unsafe.ARRAY_SHORT_BASE_OFFSET;
            case Int:
                return Unsafe.ARRAY_INT_BASE_OFFSET;
            case Long:
                return Unsafe.ARRAY_LONG_BASE_OFFSET;
            case Float:
                return Unsafe.ARRAY_FLOAT_BASE_OFFSET;
            case Double:
                return Unsafe.ARRAY_DOUBLE_BASE_OFFSET;
            case Object:
                return Unsafe.ARRAY_OBJECT_BASE_OFFSET;
            default:
                throw new JVMCIError("%s", kind);
        }
    }

    /**
     * The scale used for the index when accessing elements of an array of this kind.
     *
     * @return the scale in order to convert the index into a byte offset
     */
    static int getArrayIndexScale(JavaKind kind) {
        switch (kind) {
            case Boolean:
                return Unsafe.ARRAY_BOOLEAN_INDEX_SCALE;
            case Byte:
                return Unsafe.ARRAY_BYTE_INDEX_SCALE;
            case Char:
                return Unsafe.ARRAY_CHAR_INDEX_SCALE;
            case Short:
                return Unsafe.ARRAY_SHORT_INDEX_SCALE;
            case Int:
                return Unsafe.ARRAY_INT_INDEX_SCALE;
            case Long:
                return Unsafe.ARRAY_LONG_INDEX_SCALE;
            case Float:
                return Unsafe.ARRAY_FLOAT_INDEX_SCALE;
            case Double:
                return Unsafe.ARRAY_DOUBLE_INDEX_SCALE;
            case Object:
                return Unsafe.ARRAY_OBJECT_INDEX_SCALE;
            default:
                throw new JVMCIError("%s", kind);
        }
    }
}
