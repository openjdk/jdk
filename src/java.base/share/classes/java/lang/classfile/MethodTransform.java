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

import jdk.internal.classfile.impl.TransformImpl;
import jdk.internal.javac.PreviewFeature;

/**
 * A transformation on streams of {@link MethodElement}.
 *
 * @see ClassFileTransform
 *
 * @since 22
 */
@PreviewFeature(feature = PreviewFeature.Feature.CLASSFILE_API)
@FunctionalInterface
public non-sealed interface MethodTransform
        extends ClassFileTransform<MethodTransform, MethodElement, MethodBuilder> {

    /**
     * A method transform that sends all elements to the builder.
     */
    MethodTransform ACCEPT_ALL = new MethodTransform() {
        @Override
        public void accept(MethodBuilder builder, MethodElement element) {
            builder.with(element);
        }
    };

    /**
     * Create a stateful method transform from a {@link Supplier}.  The supplier
     * will be invoked for each transformation.
     *
     * @param supplier a {@link Supplier} that produces a fresh transform object
     *                 for each traversal
     * @return the stateful method transform
     */
    static MethodTransform ofStateful(Supplier<MethodTransform> supplier) {
        return new TransformImpl.SupplierMethodTransform(supplier);
    }

    /**
     * Create a method transform that passes each element through to the builder,
     * and calls the specified function when transformation is complete.
     *
     * @param finisher the function to call when transformation is complete
     * @return the method transform
     */
    static MethodTransform endHandler(Consumer<MethodBuilder> finisher) {
        return new MethodTransform() {
            @Override
            public void accept(MethodBuilder builder, MethodElement element) {
                builder.with(element);
            }

            @Override
            public void atEnd(MethodBuilder builder) {
                finisher.accept(builder);
            }
        };
    }

    /**
     * Create a method transform that passes each element through to the builder,
     * except for those that the supplied {@link Predicate} is true for.
     *
     * @param filter the predicate that determines which elements to drop
     * @return the method transform
     */
    static MethodTransform dropping(Predicate<MethodElement> filter) {
        return (b, e) -> {
            if (!filter.test(e))
                b.with(e);
        };
    }

    /**
     * Create a method transform that transforms {@link CodeModel} elements
     * with the supplied code transform.
     *
     * @param xform the method transform
     * @return the class transform
     */
    static MethodTransform transformingCode(CodeTransform xform) {
        return new TransformImpl.MethodCodeTransform(xform);
    }

    /**
     * @implSpec The default implementation returns a resolved transform bound
     *           to the given method builder.
     */
    @Override
    default ResolvedTransform<MethodElement> resolve(MethodBuilder builder) {
        return new TransformImpl.ResolvedTransformImpl<>(e -> accept(builder, e),
                                                         () -> atEnd(builder),
                                                         () -> atStart(builder));
    }

    /**
     * @implSpec
     * The default implementation returns this method transform chained with another
     * method transform from the argument. Chaining of two transforms requires to
     * involve a chained builder serving as a target builder for this transform
     * and also as a source of elements for the downstream transform.
     */
    @Override
    default MethodTransform andThen(MethodTransform t) {
        return new TransformImpl.ChainedMethodTransform(this, t);
    }
}
