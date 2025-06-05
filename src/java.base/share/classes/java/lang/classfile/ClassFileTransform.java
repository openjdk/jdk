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

import java.lang.classfile.attribute.RuntimeVisibleAnnotationsAttribute;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * A transformation on a {@link CompoundElement} by processing its individual
 * member elements and sending the results to a {@link ClassFileBuilder},
 * through {@link ClassFileBuilder#transform}.  A subtype of {@code
 * ClassFileTransform} is defined for each subtype of {@link CompoundElement}
 * and {@link ClassFileBuilder}, as shown in the sealed class hierarchy below.
 * <p>
 * For example, this is a basic transformation of a {@link CodeModel} that
 * redirects all calls to static methods in the {@code Foo} class to the {@code
 * Bar} class, preserving all other elements:
 * {@snippet file="PackageSnippets.java" region=fooToBarTransform}
 * Note that if no transformation of a member element is desired, the element
 * should be presented to {@link ClassFileBuilder#with builder::with}.  If no
 * action is taken, that member element is dropped.
 * <p>
 * More advanced usages of transforms include {@linkplain ##start-end start or
 * end handling}, {@linkplain ##stateful stateful transformation} that makes a
 * decision based on previously encountered member elements, and {@linkplain
 * ##composition composition} of transforms, where one transform processes the
 * results of a previous transform on the input compound structure.  All these
 * capabilities are supported by this interface and accessible to user transform
 * implementations.
 * <p id="start-end">
 * Users can define custom start and end handling for a transform by overriding
 * {@link #atStart} and {@link #atEnd}.  The start handler is called before any
 * member element is processed, and the end handler is called after all member
 * elements are processed.  For example, the start handler can be used to inject
 * extra code elements to the beginning of a code array, and the end handler,
 * combined with stateful transformation, can perform cleanup actions, such as
 * determining if an attribute has been merged, or if a new attribute should be
 * defined.  Each subtype of {@code ClassFileTransform} defines a utility method
 * {@code endHandler} that returns a transform that only has end handling.
 * <p id="stateful">
 * Transforms can have states that persist across processing of individual
 * member elements.  For example, if a transform injects an annotation, the
 * transform may keep track if it has encountered and presented an updated
 * {@link RuntimeVisibleAnnotationsAttribute} to the builder; if it has not yet,
 * it can present a new attribute containing only the injected annotation in its
 * end handler.  If such a transform is to be shared or reused, each returned
 * transform should have its own state.  Each subtype of {@code ClassFileTransform}
 * defines a utility method {@code ofStateful} where a supplier creates the
 * transform at its initial state each time the transform is reused.
 * <p id="composition">
 * Transforms can be composed via {@link #andThen}.  When this transform is
 * composed with another transform, it means the output member elements received
 * by the {@link ClassFileBuilder} become the input elements to that other
 * transform.  Composition avoids building intermediate structures for multiple
 * transforms to run on.  Each subtype of {@code ClassFileTransform} implements
 * {@link #andThen}, which generally should not be implemented by users.
 * <p>
 * Transforms that run on smaller structures can be lifted to its enclosing
 * structures to selectively run on all enclosed smaller structures of the same
 * kind.  For example, a {@link CodeTransform} can be lifted via {@link
 * ClassTransform#transformingMethodBodies(Predicate, CodeTransform)} to
 * transform the method body of select methods in the class it runs on.  This
 * allows users to write small transforms and apply to larger scales.
 * <p>
 * Besides {@link ClassFileBuilder#transform}, there are other methods that
 * accepts a transform conveniently, such as {@link ClassFile#transformClass},
 * {@link ClassBuilder#transformField}, {@link ClassBuilder#transformMethod}, or
 * {@link MethodBuilder#transformCode}.  They are convenience methods that suit
 * the majority of transformation scenarios.
 *
 * @param <C> the transform type
 * @param <E> the member element type
 * @param <B> the builder type
 *
 * @sealedGraph
 * @since 24
 */
public sealed interface ClassFileTransform<
        C extends ClassFileTransform<C, E, B>,
        E extends ClassFileElement,
        B extends ClassFileBuilder<E, B>>
        permits ClassTransform, FieldTransform, MethodTransform, CodeTransform {
    /**
     * Transform an element by taking the appropriate actions on the builder.
     * Used when transforming a classfile entity (class, method, field, method
     * body.) If no transformation is desired, the element can be presented to
     * {@link B#with(ClassFileElement)}.  If the element is to be dropped, no
     * action is required.
     * <p>
     * This method is called by the Class-File API.  Users should never call
     * this method.
     *
     * @param builder the builder for the new entity
     * @param element the element
     */
    void accept(B builder, E element);

    /**
     * Take any final action during transformation of a classfile entity.  Called
     * after all elements of the class are presented to {@link
     * #accept(ClassFileBuilder, ClassFileElement)}.
     * <p>
     * This method is called by the Class-File API.  Users should never call
     * this method.
     *
     * @param builder the builder for the new entity
     * @implSpec The default implementation does nothing.
     */
    default void atEnd(B builder) {
    }

    /**
     * Take any preliminary action during transformation of a classfile entity.
     * Called before any elements of the class are presented to {@link
     * #accept(ClassFileBuilder, ClassFileElement)}.
     * <p>
     * This method is called by the Class-File API.  Users should never call
     * this method.
     *
     * @param builder the builder for the new entity
     * @implSpec The default implementation does nothing.
     */
    default void atStart(B builder) {
    }

    /**
     * Chain this transform with another; elements presented to the builder of
     * this transform will become the input to the next transform.
     * <p>
     * This method is implemented by the Class-File API.  Users usually don't
     * have sufficient access to Class-File API functionalities to override this
     * method correctly for generic downstream transforms.
     *
     * @param next the downstream transform
     * @return the chained transform
     */
    C andThen(C next);
}
