/*
 * Copyright (c) 1998, 2024, Oracle and/or its affiliates. All rights reserved.
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
 * Provides classes that are fundamental to the design of the Java
 * programming language. The most important classes are {@link
 * Object}, which is the root of the class hierarchy, and {@link
 * Class}, instances of which represent classes at run time.
 *
 * <p id=wrapperClass>Frequently it is necessary to represent a
 * value of primitive type as if it were an object.The <dfn>{@index
 * "wrapper classes"}</dfn> {@link Boolean}, {@link Byte}, {@link
 * Character}, {@link Short}, {@link Integer}, {@link Long}, {@link
 * Float}, and {@link Double} serve this purpose.  An object of type
 * {@code Double}, for example, contains a field whose type is {@code
 * double}, representing that value in such a way that a reference to
 * it can be stored in a variable of reference type. As discussed in
 * <cite>The Java Language Specification</cite>, the wrapper classes
 * are involved in boxing (JLS {@jls 5.1.7}) and unboxing (JLS {@jls
 * 5.1.8}) conversions. These classes provide a number of methods for
 * converting among primitive values, as well as methods supporting
 * such standard functionality as {@code equals} and {@code hashCode}.
 * The {@link Void} class is a non-instantiable class that holds a
 * reference to a {@code Class} object representing the type {@code
 * void}.
 *
 * <p>The class {@link Math} provides commonly used mathematical
 * functions such as {@linkplain Math#sin(double) sine}, {@linkplain
 * Math#cos(double) cosine}, and {@linkplain Math#sqrt(double) square
 * root}. The classes {@link String}, {@link StringBuffer}, and {@link
 * StringBuilder} similarly provide commonly used operations on
 * character strings.
 *
 * <p>Classes {@link ClassLoader}, {@link Process}, {@link ProcessBuilder},
 * {@link Runtime}, and {@link System} provide "system operations" that
 * manage the dynamic loading of classes, creation of external processes,
 * and host environment inquiries such as the time of day.
 *
 * <p>Class {@link Throwable} encompasses objects that may be thrown
 * by the {@code throw} statement. Subclasses of {@code Throwable}
 * represent errors and exceptions.
 *
 * <a id="charenc"></a>
 * <h2>Character Encodings</h2>
 *
 * The specification of the {@link java.nio.charset.Charset
 * java.nio.charset.Charset} class describes the naming conventions
 * for character encodings as well as the set of standard encodings
 * that must be supported by every implementation of the Java
 * platform.
 *
 * @since 1.0
 */
package java.lang;
