/*
 * Copyright (c) 2011, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.source.doctree;

/**
 * A tree node for an {@code @inheritDoc} inline tag.
 *
 * <pre>
 *    {&#064;inheritDoc}
 *    {&#064;inheritDoc supertype}
 * </pre>
 *
 * @apiNote
 * There is no requirement that the comment containing the tag and the comment
 * containing the inherited documentation should either be both Markdown comments
 * or both traditional (not Markdown) comments.
 *
 * @since 1.8
 */
public interface InheritDocTree extends InlineTagTree {

    /**
     * {@return the reference to a superclass or superinterface from which
     * to inherit documentation, or {@code null} if no reference was provided}
     *
     * @implSpec this implementation returns {@code null}.
     * @since 22
     */
    default ReferenceTree getSupertype() {
        return null;
    }
}
