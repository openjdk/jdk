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

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import java.lang.classfile.attribute.CodeAttribute;
import jdk.internal.classfile.impl.TransformImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A transformation on streams of {@link ClassElement}.
 *
 * @see ClassFileTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
@FunctionalInterface
public non-sealed interface ClassTransform
        extends ClassFileTransform<ClassTransform, ClassElement, ClassBuilder> {

    /**
     * A class transform that sends all elements to the builder.
     */
    static final ClassTransform ACCEPT_ALL = new ClassTransform() {
        @Override
        public void accept(ClassBuilder builder, ClassElement element) {
            builder.with(element);
        }
    };

    /**
     * Create a stateful class transform from a {@link Supplier}.  The supplier
     * will be invoked for each transformation.
     *
     * @param supplier a {@link Supplier} that produces a fresh transform object
     *                 for each traversal
     * @return the stateful class transform
     */
    static ClassTransform ofStateful(Supplier<ClassTransform> supplier) {
        return new TransformImpl.SupplierClassTransform(supplier);
    }

    /**
     * Create a class transform that passes each element through to the builder,
     * and calls the specified function when transformation is complete.
     *
     * @param finisher the function to call when transformation is complete
     * @return the class transform
     */
    static ClassTransform endHandler(Consumer<ClassBuilder> finisher) {
        return new ClassTransform() {
            @Override
            public void accept(ClassBuilder builder, ClassElement element) {
                builder.with(element);
            }

            @Override
            public void atEnd(ClassBuilder builder) {
                finisher.accept(builder);
            }
        };
    }

    /**
     * Create a class transform that passes each element through to the builder,
     * except for those that the supplied {@link Predicate} is true for.
     *
     * @param filter the predicate that determines which elements to drop
     * @return the class transform
     */
    static ClassTransform dropping(Predicate<ClassElement> filter) {
        return (b, e) -> {
            if (!filter.test(e))
                b.with(e);
        };
    }

    /**
     * Create a class transform that transforms {@link MethodModel} elements
     * with the supplied method transform.
     *
     * @param filter a predicate that determines which methods to transform
     * @param xform the method transform
     * @return the class transform
     */
    static ClassTransform transformingMethods(Predicate<MethodModel> filter,
                                              MethodTransform xform) {
        return new TransformImpl.ClassMethodTransform(xform, filter);
    }

    /**
     * Create a class transform that transforms {@link MethodModel} elements
     * with the supplied method transform.
     *
     * @param xform the method transform
     * @return the class transform
     */
    static ClassTransform transformingMethods(MethodTransform xform) {
        return transformingMethods(mm -> true, xform);
    }

    /**
     * Create a class transform that transforms the {@link CodeAttribute} (method body)
     * of {@link MethodModel} elements with the supplied code transform.
     *
     * @param filter a predicate that determines which methods to transform
     * @param xform the code transform
     * @return the class transform
     */
    static ClassTransform transformingMethodBodies(Predicate<MethodModel> filter,
                                                   CodeTransform xform) {
        return transformingMethods(filter, MethodTransform.transformingCode(xform));
    }

    /**
     * Create a class transform that transforms the {@link CodeAttribute} (method body)
     * of {@link MethodModel} elements with the supplied code transform.
     *
     * @param xform the code transform
     * @return the class transform
     */
    static ClassTransform transformingMethodBodies(CodeTransform xform) {
        return transformingMethods(MethodTransform.transformingCode(xform));
    }

    /**
     * Create a class transform that transforms {@link FieldModel} elements
     * with the supplied field transform.
     *
     * @param xform the field transform
     * @return the class transform
     */
    static ClassTransform transformingFields(FieldTransform xform) {
        return new TransformImpl.ClassFieldTransform(xform, f -> true);
    }

    /**
     * @implSpec
     * The default implementation returns this class transform chained with another
     * class transform from the argument. Chaining of two transforms requires to
     * involve a chained builder serving as a target builder for this transform
     * and also as a source of elements for the downstream transform.
     */
    @Override
    default ClassTransform andThen(ClassTransform t) {
        return new TransformImpl.ChainedClassTransform(this, t);
    }

    /**
     * @implSpec The default implementation returns a resolved transform bound
     *           to the given class builder.
     */
    @Override
    default ResolvedTransform<ClassElement> resolve(ClassBuilder builder) {
        return new TransformImpl.ResolvedTransformImpl<>(e -> accept(builder, e),
                                                         () -> atEnd(builder),
                                                         () -> atStart(builder));
    }
}
