/*
 * Copyright (c) 2003, 2026, Oracle and/or its affiliates. All rights reserved.
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
 * Target @Target} meta-annotation.
 *
 * <p>For example, an annotation of the following type may only appear
 * within a type parameter or local variable declaration:
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
 * all the kinds of annotatable declarations, plus a subcategory of
 * {@link #TYPE} called {@link #ANNOTATION_TYPE}. An annotation
 * interface can be used as a declaration annotation if it either
 * omits {@link Target @Target}, or uses it to list which specific
 * kinds of declarations it should apply to.
 *
 * <p>There are also <b>type-use annotations</b> (sometimes called
 * "type annotations"), which can appear anywhere a Java type is being
 * indicated (normally, immediately preceding that type). To be used
 * as a type-use annotation, an annotation interface must explicitly
 * include {@link #TYPE_USE} in {@link Target @Target}.
 *
 * <h3 id="ambiguous">Ambiguous contexts</h3>
 *
 * <p>For some kinds of declarations, type-use annotations can also
 * appear freely intermingled with declaration annotations and
 * modifiers:
 *
 * <ul>
 *   <li>a field, parameter, record component, or local variable (if
 *       the type is being explicitly specified; treated as if it
 *       precedes that variable's type)
 *   <li>a non-void method (treated as if it precedes the method's
 *       return type)
 *   <li>a constructor (treated as if it modifies the constructed
 *       type, even though this is not technically a type context)
 * </ul>
 *
 * <p>In general, a library method for reading declaration annotations
 * (such as {@link java.lang.reflect.Field#getAnnotations
 * Field.getAnnotations()}) will not return type-use annotations found
 * in the same location, and vice-versa.
 *
 * <p>An annotation interface may list both {@link #TYPE_USE} and one
 * or more declaration targets, and thereby be fully usable as either
 * kind. When an annotation of this type appears in one of the
 * ambiguous contexts just listed, it functions as <em>both</em> a
 * declaration annotation and a type-use annotation at the same time.
 * The results may be counterintuitive in two cases: when the variable
 * type or method return type is an inner type or an array type. In
 * these cases, the declaration annotation applies to the "entire"
 * declaration, yet the type-use annotation applies more narrowly to
 * the <em>outer type</em>, or to the <em>element type</em> of the
 * array.
 *
 * @author Joshua Bloch
 * @since 1.5
 * @jls 9.7.4 Where Annotations May Appear
 */
public enum ElementType {

    /**
     * The declaration of a named class or interface. Classes without
     * names (such as an anonymous class) cannot be annotated.
     *
     * <p><b>Terminology note:</b> an annotation on a class or
     * interface declaration is not a case of a "type annotation",
     * despite how this constant is named. That phrase is an
     * abbreviation of "type-use annotation", which is supported by
     * the {@link #TYPE_USE} target.
     *
     * @see java.lang.Class#getAnnotations()
     * @jls 8.1 Class Declarations
     * @jls 8.9 Enum Classes
     * @jls 8.10 Record Classes
     * @jls 9.1 Interface Declarations
     * @jls 9.6 Annotation Interfaces
     */
    TYPE,

    /**
     * The declaration of a field (including that of an enum
     * constant).
     *
     * <p>Any annotation valid for a field declaration may also appear
     * on the declaration of a record component, and is automatically
     * copied to the private field of the same name that is generated
     * during compilation.
     *
     * @see java.lang.reflect.Field#getAnnotations()
     * @jls 8.3 Field Declarations
     * @jls 8.9.1 Enum Constants
     * @jls 8.10.3 Record Members
     */
    FIELD,

    /**
     * The declaration of a method.
     *
     * <p>Any annotation valid for a method declaration may also
     * appear on the declaration of a record component, and is
     * automatically copied to the accessor method of the same name if
     * one is generated during compilation.
     *
     * @see java.lang.reflect.Method#getAnnotations()
     * @jls 8.4 Method Declarations (of classes)
     * @jls 9.4 Method Declarations (of interfaces)
     * @jls 9.6.1 Annotation Interface Elements
     * @jls 8.10.3 Record Members
     */
    METHOD,

    /**
     * The declaration of a formal parameter of a method, constructor,
     * or lambda expression, or of an exception parameter.
     *
     * <p>A lambda parameter declared using a <em>concise parameter
     * specifier</em> cannot be annotated; either a type or the {@code
     * var} keyword must be provided.
     *
     * <p>Any annotation valid for a parameter declaration may also
     * appear on the declaration of a record component. Unless the
     * canonical constructor's full signature was provided explicitly
     * in the source code, this annotation is automatically copied to
     * the corresponding parameter declaration of the constructor
     * generated during compilation. This happens either if the
     * constructor was not provided explicitly or it used the compact
     * syntax without an explicit parameter list.
     *
     * @see java.lang.reflect.Parameter#getAnnotations()
     *     Parameter.getAnnotations() (when applicable)
     * @jls 8.4.1 Formal Parameters
     * @jls 15.27.1 Lambda Parameters
     * @jls 14.20 The {@code try} Statement
     * @jls 8.10.4 Record Constructor Declarations
     */
    PARAMETER,

    /**
     * The declaration of a constructor.
     *
     * @see java.lang.reflect.Constructor#getAnnotations()
     * @jls 8.8 Constructor Declarations
     */
    CONSTRUCTOR,

    /**
     * The declaration of a local variable. The variable might be
     * declared in an ordinary declaration statement, in the header of
     * a {@code for} or {@code try} statement, or within a pattern (as
     * a pattern variable). However, an exception variable declared
     * after {@code catch} is considered a {@link #PARAMETER} instead.
     *
     * <p>These annotations are not available via reflection.
     *
     * @jls 14.4 Local Variable Declarations
     * @jls 14.20.3 Try-with-resources
     * @jls 14.30.1 Kinds of Patterns
     */
    LOCAL_VARIABLE,

    /**
     * The declaration of an annotation interface (a subcategory of
     * {@link #TYPE}).
     *
     * @see java.lang.Class#getAnnotations()
     * @jls 9.6 Annotation Interfaces
     */
    ANNOTATION_TYPE,

    /**
     * A package declaration. For each package, at most one package
     * declaration may be annotated; by convention it should be in a
     * file named {@code package-info.java}.
     *
     * @see java.lang.Package#getAnnotations()
     * @jls 7.4.1 Named Packages
     */
    PACKAGE,

    /**
     * The declaration of a type parameter within a generic class,
     * interface, method, or constructor declaration.
     *
     * @since 1.8
     * @see java.lang.reflect.TypeVariable#getAnnotations()
     * @jls 4.4 Type Variables
     */
    TYPE_PARAMETER,

    /**
     * A syntactic location where a compile-time type is being
     * explicitly indicated. An annotation in such a location is a
     * <b>type-use annotation</b> (sometimes called a "type
     * annotation", but not to be confused with {@link #TYPE}).
     *
     * <p>This is a very broad category: JLS {@jls 4.11} lists
     * seventeen kinds of type contexts, followed by five more
     * locations where type-use annotations can also appear. Several
     * of these locations are also annotatable <em>declarations</em>
     * themselves; see <a href="#ambiguous">ambiguous cases</a> above.
     * Type-use annotations may only appear where a type is being
     * explicitly given (not, for example, if the {@code var} keyword
     * is used).
     *
     * <p>Type-use annotations present on types exposed through the
     * reflection API (for example, a field type, but not a local
     * variable type) can be accessed via the various reflection
     * methods, with "Annotated" in their names, that return {@link
     * java.lang.reflect.AnnotatedType}.
     *
     * <p>Specifying this target automatically implies the declaration
     * targets {@link #TYPE} and {@link #TYPE_PARAMETER} as well.
     * Annotations appearing in such declarations are declaration
     * annotations. As a special rule, type-use annotations may also
     * appear in a constructor declaration, to be obtained by {@link
     * java.lang.reflect.Constructor#getAnnotatedReturnType()
     * Constructor.getAnnotatedReturnType()}. These are <em>not</em>
     * treated as declaration annotations unless {@link #CONSTRUCTOR}
     * was also specified.
     *
     * <p>When the type of a record component is propagated to its
     * generated field, accessor method, or constructor parameter, its
     * embedded type-use annotations are propagated with it. No such
     * propagation occurs if the parameter or method was declared
     * explicitly in the source code.
     *
     * @since 1.8
     * @see java.lang.reflect.AnnotatedType#getAnnotations()
     * @jls 4.11 Where Types Are Used
     */
    TYPE_USE,

    /**
     * The declaration of a module in a {@code module-info.java} file.
     *
     * <p><b>Warning:</b> If a client uses the module on the <em>class
     * path</em> rather than the module path, the module declaration
     * and all its annotations will be invisible to that client.
     *
     * @since 9
     * @see java.lang.Module#getAnnotations()
     * @jls 7.7 Module Declarations
     */
    MODULE,

    /**
     * The declaration of a record component in a record class
     * declaration.
     *
     * <p>The targets ({@link #FIELD}, {@link #METHOD}, and {@link
     * #PARAMETER}) also enable usage on a record component
     * declaration, as each explains. However, if the annotation
     * interface uses {@link Target @Target} without explicitly
     * including {@link #RECORD_COMPONENT}, annotations of that type
     * will not be returned by {@link
     * java.lang.reflect.RecordComponent#getAnnotations()
     * RecordComponent.getAnnotations()}.
     *
     * @since 16
     * @jls 8.10.1 Record Components
     */
    RECORD_COMPONENT;
}
