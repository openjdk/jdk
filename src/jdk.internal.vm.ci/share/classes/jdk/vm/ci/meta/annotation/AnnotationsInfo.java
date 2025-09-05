/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.vm.ci.meta.annotation;

import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;

import java.util.Objects;

/**
 * Encapsulates the raw info of a class file annotations attribute (e.g. {@code RuntimeVisibleAnnotations}).
 *
 * @param bytes     raw bytes of the attribute after the {@code u2 attribute_name_index; u4 attribute_length} prefix
 * @param constPool for decoding constant pool indexes in embedded in {@code bytes}
 * @param container for resolving type names embedded in {@code bytes}
 */
public record AnnotationsInfo(byte[] bytes, ConstantPool constPool, ResolvedJavaType container) {
    /**
     * Returns an {@link AnnotationsInfo} instance for the given args if
     * {@code bytes != null} else {@code null}.
     */
    public static AnnotationsInfo make(byte[] bytes, ConstantPool constPool, ResolvedJavaType container) {
        if (bytes == null) {
            return null;
        }
        return new AnnotationsInfo(bytes, Objects.requireNonNull(constPool), Objects.requireNonNull(container));
    }
}
