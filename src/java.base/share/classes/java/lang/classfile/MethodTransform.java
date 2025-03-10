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

import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

import jdk.internal.classfile.impl.TransformImpl;

import static java.util.Objects.requireNonNull;

/**
 * A transformation on streams of {@link MethodElement}.
 * <p>
 * Refer to {@link ClassFileTransform} for general guidance and caution around
 * the use of transforms for structures in the {@code class} file format.
 * <p>
 * A method transform can be lifted to a class transform via {@link
 * ClassTransform#transformingMethods(MethodTransform)}, transforming only
 * the {@link MethodModel} among the class members and passing all other
 * elements to the builders.
 *
 * @see MethodModel
 * @see ClassBuilder#transformMethod
 * @since 24
 */
@FunctionalInterface
public non-sealed interface MethodTransform
        extends ClassFileTransform<MethodTransform, MethodElement, MethodBuilder> {

    /**
     * A method transform that passes all elements to the builder.
     */
    MethodTransform ACCEPT_ALL = new MethodTransform() {
        @Override
        public void accept(MethodBuilder builder, MethodElement element) {
            builder.with(element);
        }
    };

    /**
     * Creates a stateful method transform from a {@link Supplier}.  The supplier
     * will be invoked for each transformation.
     *
     * @param supplier a {@link Supplier} that produces a fresh transform object
     *                 for each traversal
     * @return the stateful method transform
     */
    static MethodTransform ofStateful(Supplier<MethodTransform> supplier) {
        requireNonNull(supplier);
        return new TransformImpl.SupplierMethodTransform(supplier);
    }

    /**
     * Creates a method transform that passes each element through to the builder,
     * and calls the specified function when transformation is complete.
     *
     * @param finisher the function to call when transformation is complete
     * @return the method transform
     */
    static MethodTransform endHandler(Consumer<MethodBuilder> finisher) {
        requireNonNull(finisher);
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
     * Creates a method transform that passes each element through to the builder,
     * except for those that the supplied {@link Predicate} is true for.
     *
     * @param filter the predicate that determines which elements to drop
     * @return the method transform
     */
    static MethodTransform dropping(Predicate<MethodElement> filter) {
        requireNonNull(filter);
        return (b, e) -> {
            if (!filter.test(e))
                b.with(e);
        };
    }

    /**
     * Creates a method transform that transforms {@link CodeModel} elements
     * with the supplied code transform, passing every other element through to
     * the builder.
     *
     * @param xform the method transform
     * @return the class transform
     */
    static MethodTransform transformingCode(CodeTransform xform) {
        return new TransformImpl.MethodCodeTransform(requireNonNull(xform));
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
        return new TransformImpl.ChainedMethodTransform(this, requireNonNull(t));
    }
}
