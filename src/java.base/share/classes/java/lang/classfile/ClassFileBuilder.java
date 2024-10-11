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

import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

import java.lang.classfile.constantpool.ConstantPoolBuilder;

import jdk.internal.classfile.impl.TransformImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A builder for a classfile or portion of a classfile.  Builders are rarely
 * created directly; they are passed to handlers by methods such as
 * {@link ClassFile#build(ClassDesc, Consumer)} or to transforms.
 * Elements of the newly built entity can be specified
 * abstractly (by passing a {@link ClassFileElement} to {@link #with(ClassFileElement)}
 * or concretely by calling the various {@code withXxx} methods.
 *
 * @param <E> the element type
 * @param <B> the builder type
 * @see ClassFileTransform
 *
 * @sealedGraph
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
public sealed interface ClassFileBuilder<E extends ClassFileElement, B extends ClassFileBuilder<E, B>>
        extends Consumer<E> permits ClassBuilder, FieldBuilder, MethodBuilder, CodeBuilder {

    /**
     * Integrate the {@link ClassFileElement} into the entity being built.
     * @param e the element
     */
    @Override
    default void accept(E e) {
        with(e);
    }

    /**
     * Integrate the {@link ClassFileElement} into the entity being built.
     * @param e the element
     * @return this builder
     */
    B with(E e);

    /**
     * {@return the constant pool builder associated with this builder}
     */
    ConstantPoolBuilder constantPool();

    /**
     * Apply a transform to a model, directing results to this builder.
     * @param model the model to transform
     * @param transform the transform to apply
     * @return this builder
     */
    default B transform(CompoundElement<E> model, ClassFileTransform<?, E, B> transform) {
        @SuppressWarnings("unchecked")
        B builder = (B) this;
        var resolved = TransformImpl.resolve(transform, builder);
        resolved.startHandler().run();
        model.forEach(resolved.consumer());
        resolved.endHandler().run();
        return builder;
    }
}
