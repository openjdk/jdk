/*
 * Copyright (c) 2022, 2023, Oracle and/or its affiliates. All rights reserved.
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
package java.lang.classfile.components;

import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.AccessFlag;
import java.lang.classfile.AccessFlags;
import java.lang.classfile.CodeTransform;
import java.lang.classfile.TypeKind;
import jdk.internal.classfile.impl.CodeLocalsShifterImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * {@link CodeLocalsShifter} is a {@link CodeTransform} shifting locals to
 * newly allocated positions to avoid conflicts during code injection.
 * Locals pointing to the receiver or to method arguments slots are never shifted.
 * All locals pointing beyond the method arguments are re-indexed in order of appearance.
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface CodeLocalsShifter extends CodeTransform permits CodeLocalsShifterImpl {

    /**
     * Creates a new instance of {@link CodeLocalsShifter}
     * with fixed local slots calculated from provided method information.
     * @param methodFlags flags of the method to construct {@link CodeLocalsShifter} for
     * @param methodDescriptor descriptor of the method to construct {@link CodeLocalsShifter} for
     * @return new instance of {@link CodeLocalsShifter}
     */
    static CodeLocalsShifter of(AccessFlags methodFlags, MethodTypeDesc methodDescriptor) {
        int fixed = methodFlags.has(AccessFlag.STATIC) ? 0 : 1;
        for (var param : methodDescriptor.parameterList())
            fixed += TypeKind.from(param).slotSize();
        return new CodeLocalsShifterImpl(fixed);
    }
}
