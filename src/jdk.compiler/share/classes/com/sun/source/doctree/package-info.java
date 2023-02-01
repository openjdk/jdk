/*
 * Copyright (c) 2011, 2023, Oracle and/or its affiliates. All rights reserved.
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

/**
 * Provides interfaces to represent documentation comments as abstract syntax
 * trees (AST).
 *
 * <h2>Markdown</h2>
 *
 * Various classes defined in this package contain a list of {@link DocTree} nodes,
 * which may represent {@linkplain TextTree plain text}, {@linkplain EntityTree entities},
 * {@linkplain InlineTagTree inline} and {@linkplain BlockTagTree block} tags,
 * {@linkplain StartElementTree start} and {@linkplain EndElementTree end} HTML elements,
 * and uninterpreted {@linkplain RawTextTree raw text}, such as for Markdown.
 *
 * @author Jonathan Gibbons
 * @since 1.8
 *
 * @see <a href="{@docRoot}/../specs/javadoc/doc-comment-spec.html">
 *      Documentation Comment Specification for the Standard Doclet</a>
 */
package com.sun.source.doctree;
