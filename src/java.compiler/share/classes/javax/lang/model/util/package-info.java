/*
 * Copyright (c) 2005, 2024, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Utilities to assist in the processing of
 * {@linkplain javax.lang.model.element program elements} and
 * {@linkplain javax.lang.model.type types}.
 *
 * <p> Unless otherwise specified in a particular implementation, the
 * collections returned by methods in this package should be expected
 * to be unmodifiable by the caller and unsafe for concurrent access.
 *
 * <p> Unless otherwise specified, methods in this package will throw
 * a {@code NullPointerException} if given a {@code null} argument.
 *
 * @apiNote
 *
 * <h2 id=expectedEvolution>Expected visitor evolution</h2>
 *
 * As the Java programming language evolves, the visitor interfaces of
 * the language model also evolve as do the concrete visitors in this
 * package. A <a href="https://openjdk.org/jeps/12">preview language
 * feature</a> in JDK <i>N</i> may have API elements added in the set
 * of visitors for the preview language level. Such new elements are
 * marked as reflective preview API. Any existing methods whose
 * specification is updated to support the preview feature are
 * <em>not</em> marked as preview.
 *
 * <p>The remainder of this note will show two examples of the API
 * changes in the model and visitors that can be added to support a
 * language feature. The examples will use additions to the elements
 * portion of the language model, but the updates to visitors for
 * types or annotation values would be analogous.
 *
 * Two distinct cases are:
 * <ul>
 *
 * <li>the preview language construct has a corresponding new modeling
 * interface and a concomitant new kind constant, such as a new {@link
 * javax.lang.model.element.ElementKind} constant
 *
 * <li>the preview language construct only triggers the introduction
 * of a new kind <em>without</em> a new modeling interface
 *
 * </ul>
 *
 * If a preview language feature is withdrawn rather than evolving to
 * a permanent platform feature, the API elements associated with the
 * feature are expected to be removed. The examples below outline the
 * API changes expected when a preview feature becomes a permanent
 * feature.
 *
 * <h3 id=topLevelLangConstruct>Adding visitor support for a
 * top-level language construct</h3>
 *
 * Consider a new language feature, preview feature 1, in JDK <i>N</i>. This
 * feature has a top-level element interface to model it:
 *
 * <pre>
 * package javax.lang.model.element;
 * /**
 *  * Represents a preview feature 1.
 *  *
 *  * &commat;since N
 *  *&sol;
 *  public interface PreviewFeature1Element extends Element {
 *  // Methods to retrieve information specific to the preview feature...
 *  }
 * </pre>
 * A new element kind would also be introduced to model such a feature:
 *
 * <pre>
 *  //  Sample diff of ElementKind.java
 *  +    /**
 *  +     * A preview feature 1.
 *  +     * &commat;since N
 *  +     *&sol;
 *  +     PREVIEW_FEATURE_1,
 * </pre>
 *
 * A {@code default} method is added to {@code ElementVisitor} to accommodate the new construct:
 * <pre>
 * //  Sample diff for ElementVisitor.java
 *  +    /**
 *  +     * Visits a preview feature 1.
 *  +     *
 *  +     * &commat;implSpec The default implementation visits a {&commat;code
 *  +     * PreviewFeature1Element} by calling {&commat;code visitUnknown(e, p)}.
 *  +     *
 *  +     * &commat;param e  the element to visit
 *  +     * &commat;param p  a visitor-specified parameter
 *  +     * &commat;return a visitor-specified result
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    default R visitPreviewFeature1(PreviewFeature1Element e, P p) {
 *  +        return visitUnknown(e, p);
 *  +    }
 * </pre>
 *
 * Given the {@code default} method on the visitor interface, the
 * preview visitor classes need to override this method and take an
 * action appropriate for the visitor's semantics:
 *
 * <pre>
 * //  Sample diff for AbstractElementVisitorPreview.java
 * //  Re-abstract visitPreviewFeature1.
 *  +    /**
 *  +     * {&commat;inheritDoc ElementVisitor}
 *  +     *
 *  +     * &commat;implSpec Visits a {&commat;code PreviewFeature1Element} in a manner
 *  +     * defined by a subclass.
 *  +     *
 *  +     * &commat;param e {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;param p {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;return a visitor-specified result
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    @Override
 *  +    public abstract R visitPreviewFeature1(PreviewFeature1Element e, P p);
 *
 * //  Sample diff for ElementKindVisitorPreview.java
 * //  Take the default action for a preview feature 1.
 *  +
 *  +    /**
 *  +     * {&commat;inheritDoc ElementVisitor}
 *  +     *
 *  +     * &commat;implSpec This implementation calls {&commat;code defaultAction}.
 *  +     *
 *  +     * &commat;param e {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;param p {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;return  the result of {&commat;code defaultAction}
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    @Override
 *  +    public R visitPreviewFeature1(PreviewFeature1Element e, P p) {
 *  +        return defaultAction(e, p);
 *  +    }
 *
 * //  Sample diff for ElementScannerPreview.java
 * //  Scan the enclosed elements of a preview feature 1.
 *  +
 *  +    /**
 *  +     * {&commat;inheritDoc ElementVisitor}
 *  +     *
 *  +     * &commat;implSpec This implementation scans the enclosed elements.
 *  +     *
 *  +     * &commat;param e {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;param p {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;return  {&commat;inheritDoc ElementScanner6}
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    @Override
 *  +    public R visitPreviewFeature1(PreviewFeature1Element e, P p) {
 *  +        return scan(e.getEnclosedElements(), p);
 *  +    }
 *
 * //  Sample diff for SimpleElementVisitorPreview.java
 * //  Take the default action for a preview feature 1.
 *  +    /**
 *  +     * {&commat;inheritDoc ElementVisitor}
 *  +     *
 *  +     * &commat;implSpec Visits a {&commat;code PreviewFeature1Element} by calling
 *  +     * {@code defaultAction}.
 *  +     *
 *  +     * &commat;param e {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;param p {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;return  {&commat;inheritDoc ElementVisitor}
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    @Override
 *  +    public R visitPreviewFeature1(PreviewFeature1Element e, P p) {
 *  +        return defaultAction(e, p);
 *  +    }
 * </pre>
 *
 * When preview feature 1 exits preview in JDK (<i>N+k</i>), a set of
 * visitors for language level (<i>N+k</i>) would be added. The
 * methods operating over the feature would be moved from the preview
 * visitors to the new language level (<i>N+k</i>) visitors. Each
 * preview visitor would then have its direct superclass changed to
 * the new corresponding (<i>N+k</i>) visitor.
 *
 * <h3 id=newKindLangConstruct>Adding visitor support for a language
 * construct that is a new kind of an existing construct</h3>
 *
 * Consider a new language feature, preview feature 2, in JDK
 * <i>N</i>. This feature has a new element kind <em>without</em> a
 * new top-level element interface needed to model it. Concretely,
 * assume a preview feature 2 is a new kind of variable; the changes
 * would be analogous if the feature were a new kind of executable
 * instead or new kind of another existing top-level construct. In
 * that case, the API changes are more limited:
 *
 * <pre>
 *  //  Sample diff for ElementKind.java
 *  +    /**
 *  +     * A preview feature 2.
 *  +     * &commat;since N
 *  +     *&sol;
 *  +     PREVIEW_FEATURE_2,
 *  ...
 *  // Update existing methods as needed
 *       public boolean isVariable() {
 *           return switch(this) {
 *           case ENUM_CONSTANT, FIELD, PARAMETER,
 *                LOCAL_VARIABLE, EXCEPTION_PARAMETER, RESOURCE_VARIABLE,
 *  -             BINDING_VARIABLE -> true;
 *  +             BINDING_VARIABLE, PREVIEW_FEATURE_2 -> true;
 *           default -> false;
 *           };
 *       }
 * </pre>
 *
 * The kind visitors need support for the new variety of element:
 * <pre>
 * // Update visitVariable in ElementKindVisitor6:
 *        ...
 *        * &commat;implSpec This implementation dispatches to the visit method for
 *        * the specific {&commat;linkplain ElementKind kind} of variable, {&commat;code
 *        * ENUM_CONSTANT}, {&commat;code EXCEPTION_PARAMETER}, {&commat;code FIELD},
 *  -     * {&commat;code LOCAL_VARIABLE}, {&commat;code PARAMETER}, or {&commat;code RESOURCE_VARIABLE}.
 *  +     * {&commat;code LOCAL_VARIABLE}, {&commat;code PARAMETER}, {&commat;code RESOURCE_VARIABLE},
 *  +     * or {&commat;code PREVIEW_FEATURE_2}.
 *        *
 *        * &commat;param e {&commat;inheritDoc ElementVisitor}
 *        * &commat;param p {&commat;inheritDoc ElementVisitor}
 *        * &commat;return  the result of the kind-specific visit method
 *        *&sol;
 *        &commat;Override
 *        public R visitVariable(VariableElement e, P p) {
 *        ...
 *           case BINDING_VARIABLE:
 *               return visitVariableAsBindingVariable(e, p);
 *
 *  +        case PREVIEW_FEATURE_2:
 *  +            return visitVariableAsPreviewFeature2(e, p);
 *  +
 *           default:
 *               throw new AssertionError("Bad kind " + k + " for VariableElement" + e);
 *        ...
 *  +    /**
 *  +     * Visits a {&commat;code PREVIEW_FEATURE_2} variable element.
 *  +     *
 *  +     * &commat;implSpec This implementation calls {&commat;code visitUnknown}.
 *  +     *
 *  +     * &commat;param e the element to visit
 *  +     * &commat;param p a visitor-specified parameter
 *  +     * &commat;return  the result of {&commat;code visitUnknown}
 *  +     *
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    public R visitVariableAsPreviewFeature2(VariableElement e, P p) {
 *  +        return visitUnknown(e, p);
 *  +    }
 * </pre>
 *
 * The preview element kind visitor in turn overrides {@code
 * visitVariableAsPreviewFeature2}:
 * <pre>
 * // Sample diff for ElementKindVisitorPreview:
 *  +    /**
 *  +     * {&commat;inheritDoc ElementKindVisitor6}
 *  +     *
 *  +     * &commat;implSpec This implementation calls {&commat;code defaultAction}.
 *  +     *
 *  +     * &commat;param e {&commat;inheritDoc ElementKindVisitor6}
 *  +     * &commat;param p {&commat;inheritDoc ElementKindVisitor6}
 *  +     * &commat;return  the result of {&commat;code defaultAction}
 *  +     *
 *  +     * &commat;since N
 *  +     *&sol;
 *  +    @Override
 *  +    public R visitVariableAsPreviewFeature2(VariableElement e, P p) {
 *  +        return defaultAction(e, p);
 *  +    }
 * </pre>
 *
 * As in the case where a new interface is introduced, when preview
 * feature 2 exits preview in JDK (<i>N+k</i>), a set of visitors for
 * language level (<i>N+k</i>) would be added. The methods operating
 * over the new feature in the kind visitors would be moved from the
 * preview visitors to new language level (<i>N+k</i>) visitors. Each
 * preview visitor would then have its direct superclass changed to
 * the new corresponding (<i>N+k</i>) visitor.
 *
 * @since 1.6
 *
 * @see <a href="https://jcp.org/en/jsr/detail?id=269">
 * JSR 269: Pluggable Annotation Processing API</a>
 */
package javax.lang.model.util;
