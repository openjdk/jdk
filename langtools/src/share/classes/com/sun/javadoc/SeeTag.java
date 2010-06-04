/*
 * Copyright (c) 1998, 2002, Oracle and/or its affiliates. All rights reserved.
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
 * using <code>&#123;@link}</code>, or a separate block comment,
 * using <code>@see</code>.
 * Method <code>name()</code> returns "@link" (no curly braces) or
 * "@see", depending on the tag.
 * Method <code>kind()</code> returns "@see" for both tags.
 *
 * @author Kaiyang Liu (original)
 * @author Robert Field (rewrite)
 * @author Atul M Dambalkar
 *
 */
public interface SeeTag extends Tag {

    /**
     * Get the label of the <code>@see</code> tag.
     * Return null if no label is present.
     * For example, for:
     * <p>
     *    &nbsp;&nbsp;<code>@see String#trim() the trim method</code>
     * </p>
     * return "the trim method".
     */
    String label();

    /**
     * Get the package doc when <code>@see</code> references only a package.
     * Return null if the package cannot be found, or if
     * <code>@see</code> references any other element (class,
     * interface, field, constructor, method) or non-element.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;<code>@see java.lang</code>
     * </p>
     * return the <code>PackageDoc</code> for <code>java.lang</code>.
     */
    public PackageDoc referencedPackage();

    /**
     * Get the class or interface name of the <code>@see</code> reference.
     * The name is fully qualified if the name specified in the
     * original <code>@see</code> tag was fully qualified, or if the class
     * or interface can be found; otherwise it is unqualified.
     * If <code>@see</code> references only a package name, then return
     * the package name instead.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;<code>@see String#valueOf(java.lang.Object)</code>
     * </p>
     * return "java.lang.String".
     * For "<code>@see java.lang</code>", return "java.lang".
     * Return null if <code>@see</code> references a non-element, such as
     * <code>@see &lt;a href="java.sun.com"&gt;</code>.
     */
    String referencedClassName();

    /**
     * Get the class doc referenced by the class name part of @see.
     * Return null if the class cannot be found.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;<code>@see String#valueOf(java.lang.Object)</code>
     * </p>
     * return the <code>ClassDoc</code> for <code>java.lang.String</code>.
     */
    ClassDoc referencedClass();

    /**
     * Get the field, constructor or method substring of the <code>@see</code>
     * reference. Return null if the reference is to any other
     * element or to any non-element.
     * References to member classes (nested classes) return null.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;<code>@see String#startsWith(String)</code>
     * </p>
     * return "startsWith(String)".
     */
    String referencedMemberName();

    /**
     * Get the member doc for the field, constructor or method
     * referenced by <code>@see</code>. Return null if the member cannot
     * be found or if the reference is to any other element or to any
     * non-element.
     * References to member classes (nested classes) return null.
     * For example, for:
     * <p>
     *   &nbsp;&nbsp;<code>@see String#startsWith(java.lang.String)</code>
     * </p>
     * return the <code>MethodDoc</code> for <code>startsWith</code>.
     */
    MemberDoc referencedMember();
}
