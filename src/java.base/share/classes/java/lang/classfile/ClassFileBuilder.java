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

import java.lang.classfile.constantpool.ConstantPoolBuilder;
import java.lang.constant.ClassDesc;
import java.util.function.Consumer;

import jdk.internal.classfile.impl.TransformImpl;

/**
 * A builder for a {@link CompoundElement}, which accepts the member elements
 * to be integrated into the built structure.  Builders are usually passed as
 * an argument to {@link Consumer} handlers, such as in {@link
 * ClassFile#build(ClassDesc, Consumer)}.  The handlers should deliver elements
 * to a builder similar to how a {@link CompoundElement} traverses its member
 * elements.
 * <p>
 * The basic way a builder accepts elements is through {@link #with}, which
 * supports call chaining.  Concrete subtypes of builders usually define extra
 * methods to define elements directly to the builder, such as {@link
 * ClassBuilder#withFlags(int)} or {@link CodeBuilder#aload(int)}.
 * <p>
 * Whether a member element can appear multiple times in a compound structure
 * affects the behavior of the element in {@code ClassFileBuilder}s.  If an
 * element can appear at most once but multiple instances are supplied to a
 * {@code ClassFileBuilder}, the last supplied instance appears on the built
 * structure.  If an element appears exactly once but no instance is supplied,
 * an unspecified default value element may be used in that structure.
 * <p>
 * Due to restrictions of the {@code class} file format, certain member elements
 * that can be modeled by the API cannot be represented in the built structure
 * under specific circumstances.  Passing such elements to the builder causes
 * {@link IllegalArgumentException}.  Some {@link ClassFile.Option}s control
 * whether such elements should be altered or dropped to produce valid {@code
 * class} files.
 *
 * @param <E> the member element type
 * @param <B> the self type of this builder
 * @see CompoundElement
 * @see ClassFileTransform
 * @sealedGraph
 * @since 24
 */
public sealed interface ClassFileBuilder<E extends ClassFileElement, B extends ClassFileBuilder<E, B>>
        extends Consumer<E> permits ClassBuilder, FieldBuilder, MethodBuilder, CodeBuilder {

    /**
     * Integrates the member element into the structure being built.
     *
     * @apiNote
     * This method exists to implement {@link Consumer}; users can use {@link
     * #with} for call chaining.
     *
     * @param e the member element
     * @throws IllegalArgumentException if the member element cannot be
     *         represented in the {@code class} file format
     */
    @Override
    default void accept(E e) {
        with(e);
    }

    /**
     * Integrates the member element into the structure being built.
     *
     * @param e the member element
     * @return this builder
     * @throws IllegalArgumentException if the member element cannot be
     *         represented in the {@code class} file format
     */
    B with(E e);

    /**
     * {@return the constant pool builder associated with this builder}
     */
    ConstantPoolBuilder constantPool();

    /**
     * Applies a transform to a compound structure, directing results to this
     * builder.
     * <p>
     * The transform will receive each element of the compound structure, as
     * well as this builder for building the structure.  The transform is free
     * to preserve, remove, or replace elements as it sees fit.
     * <p>
     * A builder can run multiple transforms against different compound
     * structures, integrating member elements of different origins.
     *
     * @apiNote
     * Many subinterfaces have methods like {@link ClassBuilder#transformMethod}
     * or {@link MethodBuilder#transformCode}.  However, calling them is
     * fundamentally different from calling this method: those methods call the
     * {@code transform} on the child builders instead of on itself.  For
     * example, {@code classBuilder.transformMethod} calls {@code
     * methodBuilder.transform} with a new method builder instead of calling
     * {@code classBuilder.transform} on itself.
     *
     * @param model the structure to transform
     * @param transform the transform to apply
     * @return this builder
     * @see ClassFileTransform
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
