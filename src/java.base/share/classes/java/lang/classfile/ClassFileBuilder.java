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
import java.util.Objects;
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
 * @param <C> the type of transform that runs on this builder
 * @see CompoundElement
 * @see ClassFileTransform
 * @sealedGraph
 * @since 24
 */
public sealed interface ClassFileBuilder<C extends ClassFileTransform<C, E, B>, E extends ClassFileElement, B extends ClassFileBuilder<C, E, B>>
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
     * A {@linkplain #transforming(ClassFileTransform, Consumer) handler-based}
     * version behaves similarly; the elements built by the handler are passed
     * instead of elements from an existing structure.
     * <p>
     * A builder can run multiple transforms against different compound
     * structures and handlers, integrating member elements of different origins.
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
     * @see #transforming(ClassFileTransform, Consumer)
     */
    default B transform(CompoundElement<E> model, C transform) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(transform);

        // This can be as simple as:
        // return transform(Util.writingAll(model), transform);
        // but this version saves an additional builder
        @SuppressWarnings("unchecked")
        B builder = (B) this;
        var resolved = TransformImpl.resolve(transform, builder);
        resolved.startHandler().run();
        model.forEach(resolved.consumer());
        resolved.endHandler().run();
        return builder;
    }

    /**
     * Applies a transform to a structure built by a handler, directing results
     * to this builder.  The builder passed to the handler is initialized with
     * the same required arguments as this builder.
     * <p>
     * The transform will receive each element built by the handler, as well
     * as this builder for building the structure.  The transform is free
     * to preserve, remove, or replace elements as it sees fit.
     * <p>
     * A {@linkplain #transform(CompoundElement, ClassFileTransform)
     * structure-based} version behaves similarly; the elements from an existing
     * source are passed instead of elements built by a handler.
     * <p>
     * A builder can run multiple transforms against different compound
     * structures and handlers, integrating member elements of different origins.
     *
     * @apiNote
     * Many subinterfaces have methods like {@link ClassBuilder#transformMethod}
     * or {@link MethodBuilder#transformCode}.  However, calling them is
     * fundamentally different from calling this method: those methods call the
     * {@code transform} on the child builders instead of on itself.  For
     * example, {@code classBuilder.transformMethod} calls {@code
     * methodBuilder.transform} with a new method builder instead of calling
     * {@code classBuilder.transform} on itself.
     * <p>
     * If elements are sourced from a {@link CompoundElement}, the {@linkplain
     * #transform(CompoundElement, ClassFileTransform) structure-based} version
     * may be more efficient.
     *
     * @param transform the transform to apply
     * @param handler the handler to produce elements to be transformed
     * @return this builder
     * @see ClassFileTransform
     * @see #transform(CompoundElement, ClassFileTransform)
     * @since 25
     */
    B transforming(C transform, Consumer<? super B> handler);
}
