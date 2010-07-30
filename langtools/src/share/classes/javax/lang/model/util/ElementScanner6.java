/*
 * Copyright (c) 2005, 2006, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.*;
import javax.annotation.processing.SupportedSourceVersion;
import static javax.lang.model.element.ElementKind.*;
import javax.lang.model.SourceVersion;
import static javax.lang.model.SourceVersion.*;


/**
 * A scanning visitor of program elements with default behavior
 * appropriate for the {@link SourceVersion#RELEASE_6 RELEASE_6}
 * source version.  The <tt>visit<i>XYZ</i></tt> methods in this
 * class scan their component elements by calling {@code scan} on
 * their {@linkplain Element#getEnclosedElements enclosed elements},
 * {@linkplain ExecutableElement#getParameters parameters}, etc., as
 * indicated in the individual method specifications.  A subclass can
 * control the order elements are visited by overriding the
 * <tt>visit<i>XYZ</i></tt> methods.  Note that clients of a scanner
 * may get the desired behavior be invoking {@code v.scan(e, p)} rather
 * than {@code v.visit(e, p)} on the root objects of interest.
 *
 * <p>When a subclass overrides a <tt>visit<i>XYZ</i></tt> method, the
 * new method can cause the enclosed elements to be scanned in the
 * default way by calling <tt>super.visit<i>XYZ</i></tt>.  In this
 * fashion, the concrete visitor can control the ordering of traversal
 * over the component elements with respect to the additional
 * processing; for example, consistently calling
 * <tt>super.visit<i>XYZ</i></tt> at the start of the overridden
 * methods will yield a preorder traversal, etc.  If the component
 * elements should be traversed in some other order, instead of
 * calling <tt>super.visit<i>XYZ</i></tt>, an overriding visit method
 * should call {@code scan} with the elements in the desired order.
 *
 * <p> Methods in this class may be overridden subject to their
 * general contract.  Note that annotating methods in concrete
 * subclasses with {@link java.lang.Override @Override} will help
 * ensure that methods are overridden as intended.
 *
 * <p> <b>WARNING:</b> The {@code ElementVisitor} interface
 * implemented by this class may have methods added to it in the
 * future to accommodate new, currently unknown, language structures
 * added to future versions of the Java&trade; programming language.
 * Therefore, methods whose names begin with {@code "visit"} may be
 * added to this class in the future; to avoid incompatibilities,
 * classes which extend this class should not declare any instance
 * methods with names beginning with {@code "visit"}.
 *
 * <p>When such a new visit method is added, the default
 * implementation in this class will be to call the {@link
 * #visitUnknown visitUnknown} method.  A new element scanner visitor
 * class will also be introduced to correspond to the new language
 * level; this visitor will have different default behavior for the
 * visit method in question.  When the new visitor is introduced, all
 * or portions of this visitor may be deprecated.
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
 * @see ElementScanner7
 * @since 1.6
 */
@SupportedSourceVersion(RELEASE_6)
public class ElementScanner6<R, P> extends AbstractElementVisitor6<R, P> {
    /**
     * The specified default value.
     */
    protected final R DEFAULT_VALUE;

    /**
     * Constructor for concrete subclasses; uses {@code null} for the
     * default value.
     */
    protected ElementScanner6(){
        DEFAULT_VALUE = null;
    }

    /**
     * Constructor for concrete subclasses; uses the argument for the
     * default value.
     */
    protected ElementScanner6(R defaultValue){
        DEFAULT_VALUE = defaultValue;
    }

    /**
     * Iterates over the given elements and calls {@link
     * #scan(Element, Object) scan(Element, P)} on each one.  Returns
     * the result of the last call to {@code scan} or {@code
     * DEFAULT_VALUE} for an empty iterable.
     *
     * @param iterable the elements to scan
     * @param  p additional parameter
     * @return the scan of the last element or {@code DEFAULT_VALUE} if no elements
     */
    public final R scan(Iterable<? extends Element> iterable, P p) {
        R result = DEFAULT_VALUE;
        for(Element e : iterable)
            result = scan(e, p);
        return result;
    }

    /**
     * Processes an element by calling {@code e.accept(this, p)};
     * this method may be overridden by subclasses.
     * @return the result of visiting {@code e}.
     */
    public R scan(Element e, P p) {
        return e.accept(this, p);
    }

    /**
     * Convenience method equivalent to {@code v.scan(e, null)}.
     * @return the result of scanning {@code e}.
     */
    public final R scan(Element e) {
        return scan(e, null);
    }

    /**
     * {@inheritDoc} This implementation scans the enclosed elements.
     *
     * @param e  the element to visit
     * @param p  a visitor-specified parameter
     * @return the result of scanning
     */
    public R visitPackage(PackageElement e, P p) {
        return scan(e.getEnclosedElements(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the enclosed elements.
     *
     * @param e  the element to visit
     * @param p  a visitor-specified parameter
     * @return the result of scanning
     */
    public R visitType(TypeElement e, P p) {
        return scan(e.getEnclosedElements(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the enclosed elements.
     *
     * @param e  the element to visit
     * @param p  a visitor-specified parameter
     * @return the result of scanning
     */
    public R visitVariable(VariableElement e, P p) {
        return scan(e.getEnclosedElements(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the parameters.
     *
     * @param e  the element to visit
     * @param p  a visitor-specified parameter
     * @return the result of scanning
     */
    public R visitExecutable(ExecutableElement e, P p) {
        return scan(e.getParameters(), p);
    }

    /**
     * {@inheritDoc} This implementation scans the enclosed elements.
     *
     * @param e  the element to visit
     * @param p  a visitor-specified parameter
     * @return the result of scanning
     */
    public R visitTypeParameter(TypeParameterElement e, P p) {
        return scan(e.getEnclosedElements(), p);
    }
}
