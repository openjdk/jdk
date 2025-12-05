/*
 * Copyright (c) 1998, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * Provides classes and interfaces, in addition to {@link Class java.lang.Class},
 * for obtaining reflective information about Java programs.  Reflection allows
 * programmatic inspection of structures in loaded classes and interfaces (JVMS
 * {@jvms 4}) in the current Java runtime, represented by <em>reflected
 * objects</em>.  These structures {@linkplain ##LanguageJvmModel may represent}
 * Java language declarations (JLS {@jls 6.1}).  Thus, a reflected object can
 * represent a Java language declaration in a class or interface not available
 * at compile time, allowing {@linkplain ##accessor programmatic use} of this
 * declaration within encapsulation and security restrictions.
 *
 * <p>{@link Array} provides static methods to dynamically create and
 * access arrays.
 *
 * <h2 id="accessor">Using the Declarations</h2>
 * The reflection classes provide <em>accessor</em> methods to use the
 * underlying declarations represented by reflected objects.  They are
 * convenient for single invocation; for repeated invocations, consider
 * using {@link MethodHandles.Lookup} to produce a {@link MethodHandle} instead.
 *
 * <h3 id="access-control">Access Control</h3>
 * An accessor of a reflected object perform access control against the caller
 * every time the accessor is used, unless that reflected object {@linkplain
 * AccessibleObject#setAccessible(boolean) suppresses checks}.  For a class or
 * interface A, core reflection represents a member declared in A and the same
 * member inherited by another reference type from A with equivalent reflective
 * objects, with A as the {@linkplain Member#getDeclaringClass() declaring class
 * or interface}.  Therefore, access checks of such a reflected object assumes
 * the use happened on the member in A, while in the Java Language and JVM,
 * the use can happen on an inherited member in another class or interface, and
 * access control proceeds differently. (JLS {@jls 6.6.1}, JVMS {@jvms 5.4.4})
 *
 * <p>In contrast, {@link MethodHandles.Lookup} performs a single access check
 * to produce a {@link MethodHandle} that performs no additional access checks.
 * If the accessed declaration is a member, the single check is performed
 * against the correct class or interface of the member.
 *
 * <h3 id="conversions">Value Conversions</h3>
 * The accessors perform conversions from accessor arguments to values accepted
 * by their underlying declarations, and from values produced by their underlying
 * declarations to accessor return values, according to the type of these values.
 * If the specified conversion does not exist or fails at run-time, these
 * accessors throw an {@link IllegalArgumentException}.
 *
 * <p id="input-conversions">For accessor arguments to underlying values:
 * <table class="striped">
 * <caption style="display:none">accessor argument to underlying value</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Accessor Parameter Type</th>
 *     <th scope="col">Underlying Value Type</th>
 *     <th scope="col">Conversions</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 *     <th scope="row">{@code Object}</th>
 *     <th scope="row">A reference type</th>
 *     <td>A cast (JLS {@jls 5.1.6.3})</td>
 * </tr>
 * <tr>
 *     <th scope="row">{@code Object}</th>
 *     <th scope="row">A primitive type</th>
 *     <td>The unboxing conversion (JLS {@jls 5.1.8}) from the argument's
 *         run-time class, followed by<br>
 *         an identity or widening primitive (JLS {@jls 5.1.2}) conversion</td>
 * </tr>
 * <tr>
 *     <th scope="row">A primitive type</th>
 *     <th scope="row">A reference type</th>
 *     <td>Does not exist</td>
 * </tr>
 * <tr>
 *     <th scope="row">A primitive type</th>
 *     <th scope="row">A primitive type</th>
 *     <td>An identity or widening primitive (JLS {@jls 5.1.2}) conversion</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p id="output-conversions">For underlying values to accessor return values:
 * <table class="striped">
 * <caption style="display:none">underlying value to accessor return type</caption>
 * <thead>
 * <tr>
 *     <th scope="col">Underlying Value Type</th>
 *     <th scope="col">Accessor Return Type</th>
 *     <th scope="col">Conversions</th>
 * </tr>
 * </thead>
 * <tbody>
 * <tr>
 *     <th scope="row">A reference type</th>
 *     <th scope="row">{@code Object}</th>
 *     <td>Trivial (JLS {@jls 5.1.5})</td>
 * </tr>
 * <tr>
 *     <th scope="row">A reference type</th>
 *     <th scope="row">A primitive type</th>
 *     <td>Does not exist</td>
 * </tr>
 * <tr>
 *     <th scope="row">A primitive type</th>
 *     <th scope="row">{@code Object}</th>
 *     <td>The boxing conversion (JLS {@jls 5.1.7}) from the underlying value's
 *         type</td>
 * </tr>
 * <tr>
 *     <th scope="row">A primitive type</th>
 *     <th scope="row">A primitive type</th>
 *     <td>An identity or widening primitive (JLS {@jls 5.1.2}) conversion</td>
 * </tr>
 * </tbody>
 * </table>
 * The conversions to {@code Object} accessor return value never throw an
 * {@code IllegalArgumentException}.
 *
 * <h2 id="LanguageJvmModel">Java programming language and JVM modeling in core reflection</h2>
 *
 * The components of core reflection, which include types in this
 * package as well as {@link java.lang.Class Class}, {@link
 * java.lang.Package Package}, and {@link java.lang.Module Module},
 * fundamentally present a JVM model of the entities in question
 * rather than a Java programming language model.  A Java compiler,
 * such as {@code javac}, translates Java source code into executable
 * output that can be run on a JVM, primarily {@code class}
 * files. Compilers for source languages other than Java can and do
 * target the JVM as well.
 *
 * <p>The translation process, including from Java language sources,
 * to executable output for the JVM is not a one-to-one
 * mapping. Structures present in the source language may have no
 * representation in the output and structures <em>not</em> present in
 * the source language may be present in the output. The latter are
 * called <i>synthetic</i> structures. Synthetic structures can
 * include {@linkplain Method#isSynthetic() methods}, {@linkplain
 * Field#isSynthetic() fields}, {@linkplain Parameter#isSynthetic()
 * parameters}, {@linkplain Class#isSynthetic() classes and
 * interfaces}. One particular kind of synthetic method is a
 * {@linkplain Method#isBridge() bridge method}. It is possible a
 * synthetic structure may not be marked as such. In particular, not
 * all {@code class} file versions support marking a parameter as
 * synthetic. A source language compiler generally has multiple ways
 * to translate a source program into a {@code class} file
 * representation. The translation may also depend on the version of
 * the {@code class} file format being targeted as different {@code
 * class} file versions have different capabilities and features. In
 * some cases the modifiers present in the {@code class} file
 * representation may differ from the modifiers on the originating
 * element in the source language, including {@link Modifier#FINAL
 * final} on a {@linkplain Parameter#getModifiers() parameter} and
 * {@code protected}, {@code private}, and {@code static} on
 * {@linkplain java.lang.Class#getModifiers() classes and interfaces}.
 *
 * <p>Besides differences in structural representation between the
 * source language and the JVM representation, core reflection also
 * exposes run-time specific information. For example, the {@linkplain
 * java.lang.Class#getClassLoader() class loaders} and {@linkplain
 * java.lang.Class#getProtectionDomain() protection domains} of a
 * {@code Class} are run-time concepts without a direct analogue in
 * source code.
 *
 * @jls 13.1 The Form of a Binary
 * @jvms 1.2 The Java Virtual Machine
 * @jvms 4.7.8 The Synthetic Attribute
 * @jvms 5.3.1 Loading Using the Bootstrap Class Loader
 * @jvms 5.3.2 Loading Using a User-defined Class Loader
 * @since 1.1
 */
package java.lang.reflect;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;