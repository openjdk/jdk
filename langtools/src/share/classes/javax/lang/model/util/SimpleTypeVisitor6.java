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
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;


/**
 * A simple visitor of types with default behavior appropriate for the
 * {@link SourceVersion#RELEASE_6 RELEASE_6} source version.
 *
 * Visit methods corresponding to {@code RELEASE_6} language
 * constructs call {@link #defaultAction defaultAction}, passing their
 * arguments to {@code defaultAction}'s corresponding parameters.
 *
 * For constructs introduced in {@code RELEASE_7} and later, {@code
 * visitUnknown} is called instead.
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
 * #visitUnknown visitUnknown} method.  A new simple type visitor
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
 * @see SimpleTypeVisitor7
 * @see SimpleTypeVisitor8
 * @since 1.6
 */
@SupportedSourceVersion(RELEASE_6)
public class SimpleTypeVisitor6<R, P> extends AbstractTypeVisitor6<R, P> {
    /**
     * Default value to be returned; {@link #defaultAction
     * defaultAction} returns this value unless the method is
     * overridden.
     */
    protected final R DEFAULT_VALUE;

    /**
     * Constructor for concrete subclasses; uses {@code null} for the
     * default value.
     */
    protected SimpleTypeVisitor6(){
        DEFAULT_VALUE = null;
    }

    /**
     * Constructor for concrete subclasses; uses the argument for the
     * default value.
     *
     * @param defaultValue the value to assign to {@link #DEFAULT_VALUE}
     */
    protected SimpleTypeVisitor6(R defaultValue){
        DEFAULT_VALUE = defaultValue;
    }

    /**
     * The default action for visit methods.  The implementation in
     * this class just returns {@link #DEFAULT_VALUE}; subclasses will
     * commonly override this method.
     */
    protected R defaultAction(TypeMirror e, P p) {
        return DEFAULT_VALUE;
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitPrimitive(PrimitiveType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitNull(NullType t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitArray(ArrayType t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitDeclared(DeclaredType t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitError(ErrorType t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitTypeVariable(TypeVariable t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitWildcard(WildcardType t, P p){
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitExecutable(ExecutableType t, P p) {
        return defaultAction(t, p);
    }

    /**
     * {@inheritDoc} This implementation calls {@code defaultAction}.
     *
     * @param t {@inheritDoc}
     * @param p {@inheritDoc}
     * @return  the result of {@code defaultAction}
     */
    public R visitNoType(NoType t, P p){
        return defaultAction(t, p);
    }
}
