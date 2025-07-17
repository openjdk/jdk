/*
 * Copyright (c) 2005, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Types and hierarchies of packages comprising a {@index "Java language
 * model"}, a reflective API that models the declarations and types of the Java
 * programming language.
 *
 * The members of this package and its subpackages are for use in
 * language modeling and language processing tasks and APIs including,
 * but not limited to, the {@linkplain javax.annotation.processing
 * annotation processing} framework.
 *
 * <p> This language model follows a <i>mirror</i>-based design; see
 *
 * <blockquote>
 * Gilad Bracha and David Ungar. <cite>Mirrors: Design Principles for
 * Meta-level Facilities of Object-Oriented Programming Languages</cite>.
 * In Proc. of the ACM Conf. on Object-Oriented Programming, Systems,
 * Languages and Applications, October 2004.
 * </blockquote>
 *
 * In particular, the model makes a distinction between declared
 * language constructs, like the {@linkplain javax.lang.model.element
 * element} representing {@code java.util.Set}, and the family of
 * {@linkplain javax.lang.model.type types} that may be associated
 * with an element, like the raw type {@code java.util.Set}, {@code
 * java.util.Set<String>}, and {@code java.util.Set<T>}.
 *
 * <p>Unless otherwise specified, methods in this package will throw
 * a {@code NullPointerException} if given a {@code null} argument.
 *
 * <h2><a id=elementsAndTypes>Elements and Types</a></h2>
 *
 * <h3><a id=DefUse>Definitions and Uses</a></h3>
 *
 * In broad terms the {@link javax.lang.model.element element} package
 * models the declarations, that is the <em>definitions</em>, of elements while
 * the {@link javax.lang.model.type type} package models <em>uses</em>
 * of types. In general, distinct uses can have individualized
 * information separate from the information associated with the
 * definition. In some sense, the information in the definition is
 * shared by all the uses.

 * <p>For example, consider the uses of {@code
 * java.lang.String} in the string processing method {@code
 * identityOrEmpty} below:
 *
 * {@snippet lang=java :
 * // Return the argument if it is non-null and the empty string otherwise.
 * public static @DefinitelyNotNull String identityOrEmpty(@MightBeNull String argument) {
 *    ...
 * }
 * }
 *
 * The return type of the method is a {@code String} annotated with
 * a {@code @DefinitelyNotNull} type annotation while the type of
 * the parameter is a {@code String} annotated with a {@code
 * @MightBeNull} type annotation. In a reflective API, since the set
 * of annotations is different for the two <em>uses</em> of {@code
 * String} as a type, the return type and argument type would need to
 * be represented by different objects to distinguish between these two
 * cases. The <em>definition</em> of {@code java.lang.String} itself
 * is annotated with neither of the type annotations in question.
 *
 * <p>Another example, consider the declaration of the generic
 * interface (JLS {@jls 9.1.2}) {@code java.util.Set} which has one
 * type parameter. This declaration captures commonality between the
 * many parameterized types (JLS {@jls 4.5}) derived from that
 * declaration such as {@code java.util.Set<String>}, {@code
 * java.util.Set<E>}, {@code java.util.Set<?>}, and also the raw type
 * (JLS {@jls 4.8}) {@code java.util.Set}.
 *
 * <h3><a id=elementTypeMapping>Mapping between Elements and Types</a></h3>
 *
 * While distinct concepts, there are bidirectional (partial) mappings
 * between elements and types, between definitions and uses. For
 * example, roughly speaking, information that would be invariant for
 * all uses of a type can be retrieved from the element defining a
 * type. For example, consider a {@link
 * javax.lang.model.type.DeclaredType DeclaredType} type mirror
 * modeling a use of {@code java.lang.String}. Calling {@link
 * javax.lang.model.type.DeclaredType#asElement()} would return the
 * {@link javax.lang.model.element.TypeElement} for {@code
 * java.lang.String}. From the {@code TypeElement}, common information
 * such as {@linkplain
 * javax.lang.model.element.TypeElement#getSimpleName() name} and
 * {@linkplain javax.lang.model.element.TypeElement#getModifiers()
 * modifiers} can be retrieved.
 *
 * <p>All elements can be {@linkplain
 * javax.lang.model.element.Element#asType() mapped to} some type.
 * The elements for classes and interfaces get {@linkplain
 * javax.lang.model.element.TypeElement#asType() mapped to} a
 * {@linkplain javax.lang.model.element.TypeElement#asType() prototypical type}.
 * Conversely, in general, many types can map to the same
 * {@linkplain javax.lang.model.element.TypeElement type element}. For
 * example, the type mirror for the raw type {@code java.util.Set},
 * the prototypical type {@code java.util.Set<E>}, and the type {@code
 * java.util.Set<String>} would all {@linkplain
 * javax.lang.model.type.DeclaredType#asElement() map to} the type
 * element for {@code java.util.Set}. Several kinds of types can be
 * mapped to elements, but other kinds of types do <em>not</em> have
 * an {@linkplain javax.lang.model.util.Types#asElement(TypeMirror)
 * element mapping}.  For example, the type mirror of an {@linkplain
 * javax.lang.model.type.ExecutableType executable type} does
 * <em>not</em> have an element mapping while a {@linkplain
 * javax.lang.model.type.DeclaredType declared type} would map to a
 * {@linkplain javax.lang.model.element.TypeElement type element}, as
 * discussed above.
 *
 * @since 1.6
 *
 * @see <a href="https://jcp.org/en/jsr/detail?id=269">
 * JSR 269: Pluggable Annotation Processing API</a>
 */

package javax.lang.model;
