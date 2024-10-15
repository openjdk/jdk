/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package java.lang.annotation;

/**
 * A syntactic location where an annotation may appear in Java code.
 * An annotation interface may optionally restrict its usage to a
 * particular subset of these locations using the {@link
 * java.lang.annotation.Target @Target} meta-annotation.
 *
 * <p>For example, an annotation of the following type may only appear
 * as part of a type parameter or local variable declaration:
 *
 * {@snippet id='example' :
 * @Target({ElementType.TYPE_PARAMETER, ElementType.LOCAL_VARIABLE})
 * public @interface MyAnnotation {}
 * }
 *
 * <h2 id="kinds-of-annotations">Declaration annotations and type-use
 * annotations</h2>
 *
 * <p>Most annotations in Java code are <b>declaration
 * annotations</b>, which act like modifiers of declarations (such as
 * a field or method declaration). The constants of this class cover
 * all ten kinds of annotatable declarations, plus a subcategory of
 * {@link #TYPE} called {@link #ANNOTATION_TYPE}. An annotation
 * interface can be used as a declaration annotation if it either
 * omits {@link java.lang.annotation.Target @Target}, or uses it to
 * list which specific kinds of declarations it should apply to.
 *
 * <p>There are also <b>type-use annotations</b> (sometimes called
 * "type annotations"), which can appear anywhere a Java type is being
 * indicated (normally, immediately preceding that type). To be used
 * as a type-use annotation, an annotation interface must use {@link
 * java.lang.annotation.Target @Target} and include {@link #TYPE_USE}
 * explicitly.
 *
 * <h3 id="ambiguous">Ambiguous contexts</h3>
 *
 * <p>For six kinds of declarations, type-use annotations can also
 * appear freely intermingled with declaration annotations and
 * modifiers:
 *
 * <ul>
 * <li>a field, parameter, local variable, or record component
 *     (treated as if it precedes that variable's type)
 * <li>a non-void method (treated as if it precedes the method's
 *     return type)
 * <li>a constructor (treated as if it modifies the constructed type,
 *     even though this is not technically a type context)
 * </ul>
 *
 * <p>In general, a library method for reading declaration annotations
 * (like {@link java.lang.reflect.Field#getAnnotations}) will not
 * return type-use annotations found in the same location, and
 * vice-versa.
 *
 * <p>An annotation interface may specify both {@link #TYPE_USE} and
 * declaration targets, and thereby be fully usable as either kind.
 * When an annotation of this type appears in one of the six ambiguous
 * contexts just listed, it functions as <em>both</em> a declaration
 * annotation and a type-use annotation at the same time. The results
 * may be counterintuitive in two cases: when the variable type or
 * method return type is an inner type or an array type. In these
 * cases, the declaration annotation applies to the "entire"
 * declaration, yet the type-use annotation applies more narrowly to
 * the <em>outer type</em> or to the <em>component type</em> of the
 * array.
 *
 * @author  Joshua Bloch
 * @since 1.5
 * @jls 9.7.4 Where Annotations May Appear
 */
public enum ElementType {

    /**
     * The declaration of a named class or interface. Classes without
     * names, such as an anonymous class or an enum constant with a
     * class body, cannot be annotated.
     *
     * <p><b>Terminology note:</b> despite this constant's name, an
     * annotation applied to a class declaration is not a "type
     * annotation". That phrase is only ever used as an abbreviation
     * of "type-use annotation", which is supported by {@link
     * #TYPE_USE}.
     */
    TYPE,

    /**
     * The declaration of a field (including of an enum constant).
     *
     * <p>Any annotation valid for a field declaration may also appear
     * on the declaration of a record component, and is automatically
     * copied to the private field of the same name that is generated
     * during compilation.
     */
    FIELD,

    /**
     * The declaration of a method (including of an element of an
     * annotation interface).
     *
     * <p>Any annotation valid for a method declaration may also appear
     * on the declaration of a record component, and is automatically
     * copied to the accessor method of the same name if one is
     * generated during compilation.
     */
    METHOD,

    /**
     * The declaration of a formal parameter of a method, constructor,
     * or lambda expression, or of an exception parameter.
     *
     * <p>Any annotation valid for a parameter declaration may also
     * appear on the declaration of a record component. Unless the
     * canonical constructor's full signature was provided explicitly in
     * the source code, this annotation is automatically copied to the
     * corresponding parameter declaration of the constructor generated
     * during compilation.
     *
     * <p>Lambda parameter declarations using the <em>concise
     * syntax</em> cannot be annotated; either a type or the `var`
     * keyword must be provided for each.
     */
    PARAMETER,

    /**
     * The declaration of a constructor.
     */
    CONSTRUCTOR,

    /**
     * The declaration of a local variable. This may be an ordinary
     * declaration statement, or declared within the header of a {@code
     * for} or {@code try} statement, or within a pattern as a pattern
     * variable. However, the similar case of an exception variable
     * declared in a {@code catch} clause is considered a {@link
     * #PARAMETER} instead.
     */
    LOCAL_VARIABLE,

    /**
     * The declaration of an annotation interface (a subcategory of
     * {@link #TYPE}). An annotation that itself appears on the
     * declaration of an annotation interface is sometimes informally
     * called a "meta-annotation"; {@link Target} itself is a primary
     * example.
     */
    ANNOTATION_TYPE,

    /**
     * The declaration of a package in a {@code package-info.java} file.
     * Package declarations in other source files cannot be annotated.
     *
     * @jls 7.4 Package Declarations
     */
    PACKAGE,

    /**
     * The declaration of a type parameter within a generic class,
     * method, or constructor declaration.
     *
     * @since 1.8
     */
    TYPE_PARAMETER,

    /**
     * A code location where a compile-time type is being indicated.
     * An annotation in such a location is called a "type-use
     * annotation" (sometimes called just "type annotation", but be
     * careful not to confuse this with {@link #TYPE}, which is only
     * for <em>class</em> declarations).
     *
     * <p>This is a very broad category: {@jls 4.11} lists seventeen
     * kinds of type contexts, followed by five more locations where
     * type-use annotations can also legally appear. Six of these
     * locations are also annotatable <em>declarations</em>
     * themselves; see <a href="#ambiguous">ambiguous cases</a> above.
     *
     * <p>In addition, specifying this target automatically includes
     * the declaration targets {@link #TYPE} and {@link
     * #TYPE_PARAMETER}, though these are not type contexts.
     *
     * @since 1.8
     */
    TYPE_USE,

    /**
     * The declaration of a module in a {@code module-info.java} file.
     *
     * @since 9
     * @jls 7.7 Module Declarations
     */
    MODULE,

    /**
     * The declaration of a record component, in the header of a record
     * class declaration. If an annotation interface should apply
     * conceptually to the field, accessor method, or constructor
     * parameter that is generated corresponding to a record component,
     * it should specify the appropriate element types ({@link #FIELD},
     * {@link #METHOD}, or {@link #PARAMETER}), instead of {@code
     * RECORD_COMPONENT} or in addition to it. This allows the
     * annotation to be automatically copied to any generated elements
     * it applies to.
     *
     * <p>{@link #RECORD_COMPONENT} must be included explicitly in
     * {@code @Target} in order for annotations of that type to be
     * available via the {@link java.lang.reflect.RecordComponent
     * RecordComponent} reflection API.
     *
     * @since 16
     * @jls 8.10.1 Record Components
     */
    RECORD_COMPONENT;
}
