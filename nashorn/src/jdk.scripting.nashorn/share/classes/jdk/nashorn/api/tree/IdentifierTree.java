/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
 * A tree node for an identifier expression.
 *
 * For example:
 * <pre>
 *   <em>name</em>
 * </pre>
 *
 * @since 9
 */
public interface IdentifierTree extends ExpressionTree {
    /**
     * Returns the name of this identifier.
     *
     * @return the name of this identifier
     */
    String getName();

    /**
     * Is this a rest parameter for a function or rest elements of an array?
     *
     * @return true if this is a rest parameter
     */
    boolean isRestParameter();

    /**
     * Is this super identifier?
     *
     * @return true if this is super identifier
     */
    boolean isSuper();

    /**
     * Is this 'this' identifier?
     *
     * @return true if this is 'this' identifier
     */
    boolean isThis();

    /**
     * Is this "*" used in module export entry?
     *
     * @return true if this "*" used in module export entry?
     */
    boolean isStar();

    /**
     * Is this "default" used in module export entry?
     *
     * @return true if this 'default' used in module export entry?
     */
    boolean isDefault();

    /**
     * Is this "*default*" used in module export entry?
     *
     * @return true if this '*default*' used in module export entry?
     */
    boolean isStarDefaultStar();
}
