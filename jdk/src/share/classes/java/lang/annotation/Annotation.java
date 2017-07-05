/*
 * Copyright (c) 2003, 2009, Oracle and/or its affiliates. All rights reserved.
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
 * The common interface extended by all annotation types.  Note that an
 * interface that manually extends this one does <i>not</i> define
 * an annotation type.  Also note that this interface does not itself
 * define an annotation type.
 *
 * More information about annotation types can be found in <i>The
 * Java&trade; Language Specification, Third Edition</i>, <a
 * href="http://java.sun.com/docs/books/jls/third_edition/html/interfaces.html#9.6">&sect;9.6</a>.
 *
 * @author  Josh Bloch
 * @since   1.5
 */
public interface Annotation {
    /**
     * Returns true if the specified object represents an annotation
     * that is logically equivalent to this one.  In other words,
     * returns true if the specified object is an instance of the same
     * annotation type as this instance, all of whose members are equal
     * to the corresponding member of this annotation, as defined below:
     * <ul>
     *    <li>Two corresponding primitive typed members whose values are
     *    <tt>x</tt> and <tt>y</tt> are considered equal if <tt>x == y</tt>,
     *    unless their type is <tt>float</tt> or <tt>double</tt>.
     *
     *    <li>Two corresponding <tt>float</tt> members whose values
     *    are <tt>x</tt> and <tt>y</tt> are considered equal if
     *    <tt>Float.valueOf(x).equals(Float.valueOf(y))</tt>.
     *    (Unlike the <tt>==</tt> operator, NaN is considered equal
     *    to itself, and <tt>0.0f</tt> unequal to <tt>-0.0f</tt>.)
     *
     *    <li>Two corresponding <tt>double</tt> members whose values
     *    are <tt>x</tt> and <tt>y</tt> are considered equal if
     *    <tt>Double.valueOf(x).equals(Double.valueOf(y))</tt>.
     *    (Unlike the <tt>==</tt> operator, NaN is considered equal
     *    to itself, and <tt>0.0</tt> unequal to <tt>-0.0</tt>.)
     *
     *    <li>Two corresponding <tt>String</tt>, <tt>Class</tt>, enum, or
     *    annotation typed members whose values are <tt>x</tt> and <tt>y</tt>
     *    are considered equal if <tt>x.equals(y)</tt>.  (Note that this
     *    definition is recursive for annotation typed members.)
     *
     *    <li>Two corresponding array typed members <tt>x</tt> and <tt>y</tt>
     *    are considered equal if <tt>Arrays.equals(x, y)</tt>, for the
     *    appropriate overloading of {@link java.util.Arrays#equals}.
     * </ul>
     *
     * @return true if the specified object represents an annotation
     *     that is logically equivalent to this one, otherwise false
     */
    boolean equals(Object obj);

    /**
     * Returns the hash code of this annotation, as defined below:
     *
     * <p>The hash code of an annotation is the sum of the hash codes
     * of its members (including those with default values), as defined
     * below:
     *
     * The hash code of an annotation member is (127 times the hash code
     * of the member-name as computed by {@link String#hashCode()}) XOR
     * the hash code of the member-value, as defined below:
     *
     * <p>The hash code of a member-value depends on its type:
     * <ul>
     * <li>The hash code of a primitive value <tt><i>v</i></tt> is equal to
     *     <tt><i>WrapperType</i>.valueOf(<i>v</i>).hashCode()</tt>, where
     *     <tt><i>WrapperType</i></tt> is the wrapper type corresponding
     *     to the primitive type of <tt><i>v</i></tt> ({@link Byte},
     *     {@link Character}, {@link Double}, {@link Float}, {@link Integer},
     *     {@link Long}, {@link Short}, or {@link Boolean}).
     *
     * <li>The hash code of a string, enum, class, or annotation member-value
     I     <tt><i>v</i></tt> is computed as by calling
     *     <tt><i>v</i>.hashCode()</tt>.  (In the case of annotation
     *     member values, this is a recursive definition.)
     *
     * <li>The hash code of an array member-value is computed by calling
     *     the appropriate overloading of
     *     {@link java.util.Arrays#hashCode(long[]) Arrays.hashCode}
     *     on the value.  (There is one overloading for each primitive
     *     type, and one for object reference types.)
     * </ul>
     *
     * @return the hash code of this annotation
     */
    int hashCode();

    /**
     * Returns a string representation of this annotation.  The details
     * of the representation are implementation-dependent, but the following
     * may be regarded as typical:
     * <pre>
     *   &#064;com.acme.util.Name(first=Alfred, middle=E., last=Neuman)
     * </pre>
     *
     * @return a string representation of this annotation
     */
    String toString();

    /**
     * Returns the annotation type of this annotation.
     */
    Class<? extends Annotation> annotationType();
}
