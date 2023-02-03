/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;

/**
 * A tree node for a character represented by an escape sequence.
 *
 * @apiNote This class does not constrain the set of valid escape sequences,
 * although the set may be effectively constrained to those defined in the
 * <a href="{@docRoot}/../specs/javadoc/doc-comment-spec.html#escape-sequences">
 * Documentation Comment Specification for the Standard Doclet</a>,
 * including the following context-sensitive escape sequences:
 *
 * <ul>
 * <li>{@code @@}, representing {@code @}, where it would otherwise be treated as introducing a block or inline tag,
 * <li>{@code @/}, representing {@code /}, as part of {@code *@/} to represent <code>&ast;&sol;</code>, and
 * <li>{@code @*}, representing {@code *}, where it would otherwise be {@linkplain Elements#getDocComment(Element) discarded},
 *     after whitespace at the beginning of a line.
 * </ul>
 *
 * @since 21
 */
public interface EscapeTree extends TextTree {
    /**
     * {@inheritDoc}
     *
     * <p>Note: this method returns the escaped character, not the original escape sequence.
     *
     */
    @Override
    String getBody();
}
