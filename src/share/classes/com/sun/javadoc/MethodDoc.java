/*
 * Copyright (c) 1998, 2006, Oracle and/or its affiliates. All rights reserved.
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
 * Represents a method of a java class.
 *
 * @since 1.2
 * @author Robert Field
 */
public interface MethodDoc extends ExecutableMemberDoc {

    /**
     * Return true if this method is abstract
     */
    boolean isAbstract();

    /**
     * Get return type.
     *
     * @return the return type of this method, null if it
     * is a constructor.
     */
    Type returnType();

    /**
     * Return the class containing the method that this method overrides.
     *
     * <p> <i>The <code>overriddenClass</code> method cannot
     * accommodate certain generic type constructs.  The
     * <code>overriddenType</code> method should be used instead.</i>
     *
     * @return a ClassDoc representing the superclass
     *         defining a method that this method overrides, or null if
     *         this method does not override.
     */
    ClassDoc overriddenClass();

    /**
     * Return the type containing the method that this method overrides.
     * It may be a <code>ClassDoc</code> or a <code>ParameterizedType</code>.
     *
     * @return the supertype whose method is overridden, or null if this
     *         method does not override another in a superclass
     * @since 1.5
     */
    Type overriddenType();

    /**
     * Return the method that this method overrides.
     *
     * @return a MethodDoc representing a method definition
     * in a superclass this method overrides, null if
     * this method does not override.
     */
    MethodDoc overriddenMethod();

    /**
     * Tests whether this method overrides another.
     * The overridden method may be one declared in a superclass or
     * a superinterface (unlike {@link #overriddenMethod()}).
     *
     * <p> When a non-abstract method overrides an abstract one, it is
     * also said to <i>implement</i> the other.
     *
     * @param meth  the other method to examine
     * @return <tt>true</tt> if this method overrides the other
     * @since 1.5
     */
    boolean overrides(MethodDoc meth);
}
