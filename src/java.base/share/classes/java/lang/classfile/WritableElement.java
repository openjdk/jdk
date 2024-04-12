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

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.classfile.constantpool.PoolEntry;
import jdk.internal.classfile.impl.DirectFieldBuilder;
import jdk.internal.classfile.impl.DirectMethodBuilder;
import jdk.internal.javac.PreviewFeature;

/**
 * A classfile element that can encode itself as a stream of bytes in the
 * encoding expected by the classfile format.
 *
 * @param <T> the type of the entity
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface WritableElement<T> extends ClassFileElement
        permits Annotation, AnnotationElement, AnnotationValue, Attribute,
                PoolEntry, BootstrapMethodEntry, FieldModel, MethodModel,
                ConstantPoolBuilder, DirectFieldBuilder, DirectMethodBuilder {
    /**
     * Writes the element to the specified writer
     *
     * @param buf the writer
     */
    void writeTo(BufWriter buf);
}
