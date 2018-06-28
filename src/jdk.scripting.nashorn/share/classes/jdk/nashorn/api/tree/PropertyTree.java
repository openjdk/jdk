/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.nashorn.api.tree;

/**
 * To represent property setting in an object literal tree.
 *
 * @deprecated Nashorn JavaScript script engine and APIs, and the jjs tool
 * are deprecated with the intent to remove them in a future release.
 *
 * @since 9
 */
@Deprecated(since="11", forRemoval=true)
public interface PropertyTree extends Tree {
    /**
     * Returns the name of this property.
     *
     * @return the name of the property
     */
    public ExpressionTree getKey();

    /**
     * Returns the value of this property. This is null for accessor properties.
     *
     * @return the value of the property
     */
    public ExpressionTree getValue();

    /**
     * Returns the setter function of this property if this
     * is an accessor property. This is null for data properties.
     *
     * @return the setter function of the property
     */
    public FunctionExpressionTree getGetter();

    /**
     * Returns the getter function of this property if this
     * is an accessor property. This is null for data properties.
     *
     * @return the getter function of the property
     */
    public FunctionExpressionTree getSetter();

    /**
     * Is this a class static property?
     *
     * @return true if this is a static property
     */
    public boolean isStatic();

    /**
     * Is this a computed property?
     *
     * @return true if this is a computed property
     */
    public boolean isComputed();
}
