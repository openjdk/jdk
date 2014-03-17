/*
 * Copyright (c) 1998, 2014, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.javadoc;

/**
 * Represents a user-defined cross-reference to related documentation.
 * The tag can reference a package, class or member, or can hold
 * plain text.  (The plain text might be a reference
 * to something not online, such as a printed book, or be a hard-coded
 * HTML link.)  The reference can either be inline with the comment,
 * using {@code {@link}}, or a separate block comment,
 * using {@code @see}.
 * Method {@code name()} returns "@link" (no curly braces) or
 * "@see", depending on the tag.
 * Method {@code kind()} returns "@see" for both tags.
 *
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Atul M Dambalkar
 *
 */
public interface SeeTag extends Tag {

    /**
     * Get the label of the {@code @see} tag.
     * Return null if no label is present.
     * For example, for:
     * <p>
     *    &nbsp;&nbsp;{@code @see String#trim() the trim method}
     * </p>
     * return "the trim method".
     *
     * @return "the trim method".
     */
    String label();

    /**
     * Get the package doc when {@code @see} references only a package.
     * Return null if the package cannot be found, or if
     * {@code @see} references any other element (class,
     * interface, field, constructor, method) or non-element.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;{@code @see java.lang}
     * </p>
     * return the {@code PackageDoc} for {@code java.lang}.
     *
     * @return the {@code PackageDoc} for {@code java.lang}.
     */
    public PackageDoc referencedPackage();

    /**
     * Get the class or interface name of the {@code @see} reference.
     * The name is fully qualified if the name specified in the
     * original {@code @see} tag was fully qualified, or if the class
     * or interface can be found; otherwise it is unqualified.
     * If {@code @see} references only a package name, then return
     * the package name instead.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;{@code @see String#valueOf(java.lang.Object)}
     * </p>
     * return "java.lang.String".
     * For "{@code @see java.lang}", return "java.lang".
     * Return null if {@code @see} references a non-element, such as
     * {@code @see <a href="java.sun.com">}.
     *
     * @return null if {@code @see} references a non-element, such as
     * {@code @see <a href="java.sun.com">}.
     */
    String referencedClassName();

    /**
     * Get the class doc referenced by the class name part of @see.
     * Return null if the class cannot be found.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;{@code @see String#valueOf(java.lang.Object)}
     * </p>
     * return the {@code ClassDoc} for {@code java.lang.String}.
     *
     * @return the {@code ClassDoc} for {@code java.lang.String}.
     */
    ClassDoc referencedClass();

    /**
     * Get the field, constructor or method substring of the {@code @see}
     * reference. Return null if the reference is to any other
     * element or to any non-element.
     * References to member classes (nested classes) return null.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;{@code @see String#startsWith(String)}
     * </p>
     * return "startsWith(String)".
     *
     * @return "startsWith(String)".
     */
    String referencedMemberName();

    /**
     * Get the member doc for the field, constructor or method
     * referenced by {@code @see}. Return null if the member cannot
     * be found or if the reference is to any other element or to any
     * non-element.
     * References to member classes (nested classes) return null.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;{@code @see String#startsWith(java.lang.String)}
     * </p>
     * return the {@code MethodDoc} for {@code startsWith}.
     *
     * @return the {@code MethodDoc} for {@code startsWith}.
     */
    MemberDoc referencedMember();
}
