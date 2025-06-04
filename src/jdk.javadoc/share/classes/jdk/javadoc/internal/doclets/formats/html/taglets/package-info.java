/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
 * Classes used to build the output for documentation comment tags.
 *
 * Tags are either inline tags, meaning they can be used within a
 * sentence or phrase, or are block tags, meaning that they provide
 * additional details that follow the main description in a comment.
 * Taglets model that distinction.
 *
 * Inline tags are always processed individually, within the surrounding
 * context. In general, inline tags always generate some (non-empty) output,
 * even if the output is some form indicating an error. It is almost never
 * correct to not generate any output to place between the parts of the
 * comment that come before and after the tag in the underlying comment.
 *
 * Conversely, block tags of any given kind are always processed as a
 * group, even if they do not appear contiguously in the underlying comment.
 */
package jdk.javadoc.internal.doclets.formats.html.taglets;
