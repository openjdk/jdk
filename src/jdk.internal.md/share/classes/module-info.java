/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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

// This module is primarily an import from a recent tagged version of
//     https://github.com/commonmark/commonmark-java
//
// The following parts are imported:
//
// * commonmark/src/main/java
// * commonmark/src/main/resources
// * commonmark-ext-gfm-tables/src/main/java
// * commonmark-ext-gfm-tables/src/main/resources
//
// For source and resource files, the following transformations are applied:
//
// * legal headers are added
// * package and import statements are updated
// * characters outside the ASCII range are converted to Unicode escapes
// * @SuppressWarnings("fallthrough") is added to getSetextHeadingLevel
// * the value for ENTITY_PATH is updated with the modified package
// * the file `entities.properties` is renamed to `entities.txt`

/**
 * Internal support for Markdown.
 *
 * @since 23
 */
module jdk.internal.md {
    requires jdk.compiler;

    exports jdk.internal.markdown to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark.ext.gfm.tables to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark.node to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark.parser to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark.renderer to
            jdk.javadoc,
            jdk.jshell;
    exports jdk.internal.org.commonmark.renderer.html to
            jdk.javadoc,
            jdk.jshell;

    provides com.sun.tools.javac.api.JavacTrees.DocCommentTreeTransformer
            with jdk.internal.markdown.MarkdownTransformer;
}
