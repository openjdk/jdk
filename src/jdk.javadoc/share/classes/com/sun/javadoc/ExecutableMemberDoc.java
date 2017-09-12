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
 * Represents a method or constructor of a java class.
 *
 * @since 1.2
 * @author Robert Field
 *
 * @deprecated
 *   The declarations in this package have been superseded by those
 *   in the package {@code jdk.javadoc.doclet}.
 *   For more information, see the <i>Migration Guide</i> in the documentation for that package.
 */
@Deprecated
public interface ExecutableMemberDoc extends MemberDoc {

    /**
     * Return exceptions this method or constructor throws.
     * If the type of the exception is a type variable, return the
     * {@code ClassDoc} of its erasure.
     *
     * <p> <i>The {@code thrownExceptions} method cannot
     * accommodate certain generic type constructs.  The
     * {@code thrownExceptionTypes} method should be used
     * instead.</i>
     *
     * @return an array of ClassDoc[] representing the exceptions
     *         thrown by this method.
     * @see #thrownExceptionTypes
     */
    ClassDoc[] thrownExceptions();

    /**
     * Return exceptions this method or constructor throws.
     *
     * @return an array representing the exceptions thrown by this method.
     *         Each array element is either a {@code ClassDoc} or a
     *         {@code TypeVariable}.
     * @since 1.5
     */
    Type[] thrownExceptionTypes();

    /**
     * Return true if this method is native
     *
     * @return true if this method is native
     */
    boolean isNative();

    /**
     * Return true if this method is synchronized
     *
     * @return true if this method is synchronized
     */
    boolean isSynchronized();

    /**
     * Return true if this method was declared to take a variable number
     * of arguments.
     *
     * @since 1.5
     * @return true if this method was declared to take a variable number of arguments.
     */
    public boolean isVarArgs();

    /**
     * Get argument information.
     *
     * @see Parameter
     *
     * @return an array of Parameter, one element per argument
     * in the order the arguments are present.
     */
    Parameter[] parameters();

    /**
     * Get the receiver type of this executable element.
     *
     * @return the receiver type of this executable element.
     * @since 1.8
     */
    Type receiverType();

    /**
     * Return the throws tags in this method.
     *
     * @return an array of ThrowTag containing all {@code @exception}
     * and {@code @throws} tags.
     */
    ThrowsTag[] throwsTags();

    /**
     * Return the param tags in this method, excluding the type
     * parameter tags.
     *
     * @return an array of ParamTag containing all {@code @param} tags
     * corresponding to the parameters of this method.
     */
    ParamTag[] paramTags();

    /**
     * Return the type parameter tags in this method.
     *
     * @return an array of ParamTag containing all {@code @param} tags
     * corresponding to the type parameters of this method.
     * @since 1.5
     */
    ParamTag[] typeParamTags();

    /**
     * Get the signature. It is the parameter list, type is qualified.
     *      For instance, for a method {@code mymethod(String x, int y)},
     *      it will return {@code (java.lang.String,int)}.
     *
     * @return the parameter list where type is qualified.
     */
    String signature();

    /**
     * get flat signature.  all types are not qualified.
     *      return a String, which is the flat signiture of this member.
     *      It is the parameter list, type is not qualified.
     *      For instance, for a method {@code mymethod(String x, int y)},
     *      it will return {@code (String, int)}.
     *
     * @return a String, which is the flat signiture of this member.
     */
    String flatSignature();

    /**
     * Return the formal type parameters of this method or constructor.
     * Return an empty array if this method or constructor is not generic.
     *
     * @return the formal type parameters of this method or constructor.
     * @since 1.5
     */
    TypeVariable[] typeParameters();
}
