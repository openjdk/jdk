/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.util.List;

/**
 * A tree node for an {@code @note} block or inline tag.
 *
 * <pre>
 *    &#064;note body
 *    &#064;note [attributes] body
 *    {&#064;note body}
 *    {&#064;note [attributes] body}
 * </pre>
 *
 * @since 26
 */
public interface NoteTree extends BlockTagTree, InlineTagTree {

    /**
     * Returns the list of the attributes of the {@code @note} tag.
     *
     * @return the list of the attributes
     */
    List<? extends DocTree> getAttributes();

    /**
     * Returns the body of the {@code @note} tag.
     * @return the body of the tag
     */
    List<? extends DocTree> getBody();

    /**
     * Returns whether this instance is an inline tag.
     * @return {@code true} if this instance is an inline tag, and {@code false} otherwise
     */
    boolean isInline();
}
