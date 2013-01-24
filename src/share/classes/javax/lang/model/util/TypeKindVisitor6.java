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

import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.type.*;
import static javax.lang.model.SourceVersion.*;

/**
 * A visitor of types based on their {@linkplain TypeKind kind} with
 * default behavior appropriate for the {@link SourceVersion#RELEASE_6
 * RELEASE_6} source version.  For {@linkplain
 * TypeMirror types} <tt><i>XYZ</i></tt> that may have more than one
 * kind, the <tt>visit<i>XYZ</i></tt> methods in this class delegate
 * to the <tt>visit<i>XYZKind</i></tt> method corresponding to the
 * first argument's kind.  The <tt>visit<i>XYZKind</i></tt> methods
 * call {@link #defaultAction defaultAction}, passing their arguments
 * to {@code defaultAction}'s corresponding parameters.
 *
 * <p> Methods in this class may be overridden subject to their
 * general contract.  Note that annotating methods in concrete
 * subclasses with {@link java.lang.Override @Override} will help
 * ensure that methods are overridden as intended.
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
 * #visitUnknown visitUnknown} method.  A new type kind visitor class
 * will also be introduced to correspond to the new language level;
 * this visitor will have different default behavior for the visit
 * method in question.  When the new visitor is introduced, all or
 * portions of this visitor may be deprecated.
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
 * @see TypeKindVisitor7
 * @see TypeKindVisitor8
 * @since 1.6
 */
@SupportedSourceVersion(RELEASE_6)
public class TypeKindVisitor6<R, P> extends SimpleTypeVisitor6<R, P> {
    /**
     * Constructor for concrete subclasses to call; uses {@code null}
     * for the default value.
     */
    protected TypeKindVisitor6() {
        super(null);
    }


    /**
     * Constructor for concrete subclasses to call; uses the argument
     * for the default value.
     *
     * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
     */
    protected TypeKindVisitor6(R defaultValue) {
        super(defaultValue);
    }

    /**
     * Visits a primitive type, dispatching to the visit method for
     * the specific {@linkplain TypeKind kind} of primitive type:
     * {@code BOOLEAN}, {@code BYTE}, etc.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of the kind-specific visit method
     */
    @Override
    public R visitPrimitive(PrimitiveType t, P p) {
        TypeKind k = t.getKind();
        switch (k) {
        case BOOLEAN:
            return visitPrimitiveAsBoolean(t, p);

        case BYTE:
            return visitPrimitiveAsByte(t, p);

        case SHORT:
            return visitPrimitiveAsShort(t, p);

        case INT:
            return visitPrimitiveAsInt(t, p);

        case LONG:
            return visitPrimitiveAsLong(t, p);

        case CHAR:
            return visitPrimitiveAsChar(t, p);

        case FLOAT:
            return visitPrimitiveAsFloat(t, p);

        case DOUBLE:
            return visitPrimitiveAsDouble(t, p);

        default:
            throw new AssertionError("Bad kind " + k + " for PrimitiveType" + t);
        }
    }

    /**
     * Visits a {@code BOOLEAN} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsBoolean(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code BYTE} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsByte(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code SHORT} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsShort(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits an {@code INT} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsInt(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code LONG} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsLong(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code CHAR} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsChar(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code FLOAT} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsFloat(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@code DOUBLE} primitive type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitiveAsDouble(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@link NoType} instance, dispatching to the visit method for
     * the specific {@linkplain TypeKind kind} of pseudo-type:
     * {@code VOID}, {@code PACKAGE}, or {@code NONE}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of the kind-specific visit method
     */
    @Override
    public R visitNoType(NoType t, P p) {
        TypeKind k = t.getKind();
        switch (k) {
        case VOID:
            return visitNoTypeAsVoid(t, p);

        case PACKAGE:
            return visitNoTypeAsPackage(t, p);

        case NONE:
            return visitNoTypeAsNone(t, p);

        default:
            throw new AssertionError("Bad kind " + k + " for NoType" + t);
        }
    }

    /**
     * Visits a {@link TypeKind#VOID VOID} pseudo-type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitNoTypeAsVoid(NoType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@link TypeKind#PACKAGE PACKAGE} pseudo-type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitNoTypeAsPackage(NoType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * Visits a {@link TypeKind#NONE NONE} pseudo-type by calling
     * {@code defaultAction}.
     *
     * @param t the type to visit
     * @param p a visitor-specified parameter
     * @return  the result of {@code defaultAction}
     */
    public R visitNoTypeAsNone(NoType t, P p) {
        return defaultAction(t, p);
    }
}
