/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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
 * A tree node for an {@code @snippet} inline tag.
 *
 * <pre>
 *    {&#064;snippet :
 *     body
 *    }
 *
 *    {&#064;snippet attributes}
 *
 *    {&#064;snippet attributes :
 *     body
 *    }
 * </pre>
 *
 * @since 18
 */
public interface SnippetTree extends InlineTagTree {

    /**
     * Returns the list of the attributes of the {@code @snippet} tag.
     *
     * @return the list of the attributes
     */
    List<? extends DocTree> getAttributes();

    /**
     * Returns the body of the {@code @snippet} tag, or {@code null} if there is no body.
     *
     * @apiNote
     * An instance of {@code SnippetTree} with an empty body differs from an
     * instance of {@code SnippetTree} with no body.
     * If a tag has no body, then calling this method returns {@code null}.
     * If a tag has an empty body, then this method returns a {@code TextTree}
     * whose {@link TextTree#getBody()} returns an empty string.
     *
     * @return the body of the tag, or {@code null} if there is no body
     */
    TextTree getBody();
}
