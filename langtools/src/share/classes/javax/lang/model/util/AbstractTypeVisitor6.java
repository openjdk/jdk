/*
 * Copyright (c) 2005, 2013, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.util;

import javax.lang.model.type.*;

/**
 * A skeletal visitor of types with default behavior appropriate for
 * the {@link javax.lang.model.SourceVersion#RELEASE_6 RELEASE_6}
 * source version.
 *
 * <p> <b>WARNING:</b> The {@code TypeVisitor} interface implemented
 * by this class may have methods added to it in the future to
 * accommodate new, currently unknown, language structures added to
 * future versions of the Java&trade; programming language.
 * Therefore, methods whose names begin with {@code "visit"} may be
 * added to this class in the future; to avoid incompatibilities,
 * classes which extend this class should not declare any instance
 * methods with names beginning with {@code "visit"}.
 *
 * <p>When such a new visit method is added, the default
 * implementation in this class will be to call the {@link
 * #visitUnknown visitUnknown} method.  A new abstract type visitor
 * class will also be introduced to correspond to the new language
 * level; this visitor will have different default behavior for the
 * visit method in question.  When the new visitor is introduced, all
 * or portions of this visitor may be deprecated.
 *
 * <p>Note that adding a default implementation of a new visit method
 * in a visitor class will occur instead of adding a <em>default
 * method</em> directly in the visitor interface since a Java SE 8
 * language feature cannot be used to this version of the API since
 * this version is required to be runnable on Java SE 7
 * implementations.  Future versions of the API that are only required
 * to run on Java SE 8 and later may take advantage of default methods
 * in this situation.
 *
 * @param <R> the return type of this visitor's methods.  Use {@link
 *            Void} for visitors that do not need to return results.
 * @param <P> the type of the additional parameter to this visitor's
 *            methods.  Use {@code Void} for visitors that do not need an
 *            additional parameter.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 *
 * @see AbstractTypeVisitor7
 * @see AbstractTypeVisitor8
 * @since 1.6
 */
public abstract class AbstractTypeVisitor6<R, P> implements TypeVisitor<R, P> {
    /**
     * Constructor for concrete subclasses to call.
     */
    protected AbstractTypeVisitor6() {}

    /**
     * Visits any type mirror as if by passing itself to that type
     * mirror's {@link TypeMirror#accept accept} method.  The
     * invocation {@code v.visit(t, p)} is equivalent to {@code
     * t.accept(v, p)}.
     *
     * @param t  the type to visit
     * @param p  a visitor-specified parameter
     * @return a visitor-specified result
     */
    public final R visit(TypeMirror t, P p) {
        return t.accept(this, p);
    }

    /**
     * Visits any type mirror as if by passing itself to that type
     * mirror's {@link TypeMirror#accept accept} method and passing
     * {@code null} for the additional parameter.  The invocation
     * {@code v.visit(t)} is equivalent to {@code t.accept(v, null)}.
     *
     * @param t  the type to visit
     * @return a visitor-specified result
     */
    public final R visit(TypeMirror t) {
        return t.accept(this, null);
    }

    /**
     * Visits a {@code UnionType} element by calling {@code
     * visitUnknown}.

     * @param t  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     *
     * @since 1.7
     */
    public R visitUnion(UnionType t, P p) {
        return visitUnknown(t, p);
    }

    /**
     * Visits an {@code IntersectionType} element by calling {@code
     * visitUnknown}.

     * @param t  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of {@code visitUnknown}
     *
     * @since 1.8
     */
    public R visitIntersection(IntersectionType t, P p) {
        return visitUnknown(t, p);
    }

    /**
     * Visits an {@code AnnotatedType} element by calling {@code
     * visit} on the underlying type.

     * @param t  {@inheritDoc}
     * @param p  {@inheritDoc}
     * @return the result of calling {@code visit} on the underlying type
     *
     * @since 1.8
     *
     * TODO: should xxxVisitor8 subclasses override this and call
     *   the defaultAction?
     */
    public R visitAnnotated(AnnotatedType t, P p) {
        return visit(t.getUnderlyingType(), p);
    }

    /**
     * {@inheritDoc}
     *
     * <p> The default implementation of this method in {@code
     * AbstractTypeVisitor6} will always throw {@code
     * UnknownTypeException}.  This behavior is not required of a
     * subclass.
     *
     * @param t  the type to visit
     * @return a visitor-specified result
     * @throws UnknownTypeException
     *  a visitor implementation may optionally throw this exception
     */
    public R visitUnknown(TypeMirror t, P p) {
        throw new UnknownTypeException(t, p);
    }
}
